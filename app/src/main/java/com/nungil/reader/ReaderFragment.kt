package com.nungil.reader

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.nungil.R
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.core.scan.ScanPhrases
import com.nungil.contract.app.CameraScreen
import com.nungil.contract.app.SwitchableCamera
import com.nungil.contract.app.services
import com.nungil.shell.MainActivity
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.VoiceCommand
import com.nungil.databinding.ReaderFragmentBinding
import com.nungil.scan.CameraSession
import com.nungil.search.CameraGate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads QR codes, barcodes and printed text aloud, every 1.5 s, never the same text twice within 10 s.
 * Version 1 reads Latin text only (Korean OCR needs an extra ML Kit model; see the Hand-offs).
 */
class ReaderFragment : Fragment(), CameraScreen, VoiceHandler {
    private var _binding: ReaderFragmentBinding? = null
    private val binding get() = _binding!!
    private val gate = CameraGate(this) { startCamera() }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var lang: Lang

    private var camera: CameraSession? = null
    private var extras: ExecutorService? = null
    private val busy = AtomicBoolean(false)
    private val engine = ReaderEngine()

    @Volatile
    private var running = true

    /** Set while the view is gone, so a read still in flight does not speak. */
    @Volatile
    private var leaving = false

    @Volatile
    private var lastRunMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ReaderFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        leaving = false
        services = services()
        lang = services.lang
        ViewCompat.setAccessibilityHeading(binding.readerTitle, true)
        binding.readerButton.setOnClickListener { if (running) stopReading() else startReading() }
        // A tap on what was found reads it, as "read" does.
        binding.readerText.setOnClickListener { main.removeCallbacks(giveUp); readOffer() }
        gate.attach(binding.readerPermission)
        extras = Executors.newSingleThreadExecutor()
    }

    override fun onResume() {
        super.onResume()
        gate.check()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        leaving = true
        camera?.stop()
        camera = null
        gate.detach()
        extras?.let { executor ->
            // No waiting here: blocking the main thread froze the screen while leaving. A read still in flight
            // finishes on the extras thread without speaking (leaving), and shutdown() lets nothing new in.
            executor.shutdown()
        }
        extras = null
        main.removeCallbacksAndMessages(null)
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.close()
    }

    @Volatile private var last: VisionFrame? = null

    override fun lastFrame(): VisionFrame? = last

    override val switchable: SwitchableCamera? get() = camera

    override val isWorking: Boolean get() = running

    override fun pause() {
        if (_binding == null || !running) return
        stopReading()
        services.speaker.sayNow(ScanPhrases.paused(lang))
    }

    override fun resume() {
        if (_binding == null) return
        startReading()
    }

    private fun startCamera() {
        if (camera != null || _binding == null) return
        val options = CameraSession.Options(facing = Facing.BACK, detect = false, keepBitmap = true)
        camera = CameraSession(this, binding.readerPreview, options, ::onFrame, ::onCameraError).also { it.start() }
    }

    private fun stopReading() {
        running = false
        binding.readerButton.setText(R.string.reader_resume)
    }

    private fun startReading() {
        running = true
        lastRunMs = 0L
        binding.readerButton.setText(R.string.reader_pause)
    }

    /** Analysis thread. */
    private fun onFrame(frame: VisionFrame) {
        last = frame
        if (!running) return
        val bitmap = frame.bitmap ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRunMs < ReaderPolicy.READ_EVERY_MS) return
        val executor = extras ?: return
        if (!busy.compareAndSet(false, true)) return
        lastRunMs = now
        try {
            executor.execute {
                try {
                    read(bitmap)
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
        }
    }

    /** Extras thread: a code wins over text in the same frame; text is read top to bottom (ReaderEngine). */
    private fun read(bitmap: Bitmap) {
        val read = try {
            engine.read(bitmap, SystemClock.elapsedRealtime(), lang)
        } catch (e: Exception) {
            Log.i("Nungil", "Reader failed: ${e.message}")
            return
        }
        if (leaving) return
        read.spoken?.let { code ->
            Log.i("Nungil", "Reader says \"$code\"")
            services.speaker.say(code)
            main.post { _binding?.readerText?.text = code }
        }
        read.offer?.let { offer -> main.post { offer(offer) } }
    }

    // ---- Text found: offered first, read on the user's word -----------------------------------------

    /** Main thread: "Text found, …. Say read to hear it." and the next words are the answer. */
    private fun offer(offer: ReaderPolicy.Offer) {
        val b = _binding ?: return
        val phrase = offer.phrase(lang)
        Log.i("Nungil", "Reader offers ${offer.lines.size} lines: \"$phrase\"")
        services.speaker.say(phrase)
        b.readerText.text = phrase
        listenForAnswer()
        main.removeCallbacks(giveUp)
        main.postDelayed(giveUp, ANSWER_MS)
    }

    private fun listenForAnswer() {
        (activity as? MainActivity)?.let {
            it.dictationAccepts = { text -> ReaderPolicy.answer(text) != null }
            it.dictationEarly = true
        }
        services.askForWords(viewLifecycleOwner) { text -> answer(text) }
    }

    /** Main thread: "read" or "yes" reads the text found in full; "no" lets it go. */
    private fun answer(text: String) {
        main.removeCallbacks(giveUp)
        when (ReaderPolicy.answer(text)) {
            true -> readOffer()
            false -> {
                engine.decline()
                services.speaker.say(ReaderPolicy.skipped(lang))
            }
            null -> listenForAnswer()
        }
    }

    private fun readOffer(): Boolean {
        val full = engine.accept() ?: return false
        Log.i("Nungil", "Reader reads \"${full.take(80)}\"")
        services.speaker.say(full)
        _binding?.readerText?.text = full
        return true
    }

    /** No answer in time: the microphone is let go, so the next words are not taken for one. */
    private val giveUp = Runnable {
        engine.decline()
        (activity as? MainActivity)?.dropWords()
    }

    /** "Read" or "read text" said on this screen: the text offered, not the reader opened again. */
    override fun onVoiceCommand(command: VoiceCommand): Boolean {
        if (command != VoiceCommand.ReadText) return false
        main.removeCallbacks(giveUp)
        if (!readOffer()) services.speaker.say(ReaderPolicy.nothingFound(lang))
        return true
    }

    private fun onCameraError(error: Throwable) {
        Log.i("Nungil", "Reader camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }

    private companion object {
        /** How long an offer waits for "read". */
        const val ANSWER_MS = 15_000L
    }
}

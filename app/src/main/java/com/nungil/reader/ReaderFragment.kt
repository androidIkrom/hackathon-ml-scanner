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
import androidx.core.view.isVisible
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
import com.nungil.speech.TtsSpeaker
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

    /** The text being read, line by line. */
    private var reading: List<String> = emptyList()

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
        // Read it, Skip, or a tap on what was found: the same as saying "read" or "no".
        binding.readerRead.setOnClickListener { answered(true) }
        binding.readerSkip.setOnClickListener { answered(false) }
        binding.readerText.setOnClickListener { if (binding.readerAnswer.isVisible) answered(true) }
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

    /** "Stop": the voice stops at once (in the middle of a text too), and so does the looking. */
    override fun pause() {
        if (_binding == null || !running) return
        services.speaker.stop()
        stopReading()
        services.speaker.sayNow(ScanPhrases.paused(lang))
    }

    /** "Continue": a text stopped in the middle goes on from the line it was at, and the looking starts again. */
    override fun resume() {
        if (_binding == null) return
        startReading()
        // The line the stop cut: "stop" here, or a bare "stop" that only silenced the voice.
        ReaderPolicy.resumeAt(reading, (services.speaker as? TtsSpeaker)?.cutLine)?.let { readLines(reading, it) }
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

    /**
     * Main thread: "Text found, …. Say read to hear it.", with Read it / Skip. The answer is listened for once
     * that has been said: the app's own "Read text or a code" was taken for "read" (the logs, 2026-10-12).
     */
    private fun offer(offer: ReaderPolicy.Offer) {
        val b = _binding ?: return
        val phrase = offer.phrase(lang)
        Log.i("Nungil", "Reader offers ${offer.lines.size} lines: \"$phrase\"")
        b.readerText.text = phrase
        b.readerAnswer.isVisible = true
        services.speaker.sayFinal(phrase) { if (_binding?.readerAnswer?.isVisible == true) listenForAnswer() }
        main.removeCallbacks(giveUp)
        main.postDelayed(giveUp, ReaderPolicy.ANSWER_MS)
    }

    private fun listenForAnswer() {
        (activity as? MainActivity)?.let {
            it.dictationAccepts = { text -> ReaderPolicy.wantsAnother(text) || ReaderPolicy.answer(text) != null }
            it.dictationEarly = true
        }
        services.askForWords(viewLifecycleOwner) { text ->
            val yes = ReaderPolicy.answer(text)
            when {
                ReaderPolicy.wantsAnother(text) -> another()
                yes == null -> listenForAnswer()
                else -> answered(yes)
            }
        }
    }

    /** Main thread: "read", "yes", Read it or a tap on the text reads it in full; "no" or Skip lets it go. */
    private fun answered(yes: Boolean) {
        main.removeCallbacks(giveUp)
        (activity as? MainActivity)?.dropWords()
        _binding?.readerAnswer?.isVisible = false
        if (!yes) {
            engine.decline()
            services.speaker.say(ReaderPolicy.skipped(lang))
            return
        }
        val lines = engine.accept(SystemClock.elapsedRealtime())
        if (lines == null) {
            services.speaker.say(ReaderPolicy.nothingFound(lang))
            return
        }
        readLines(lines, 0)
    }

    /** The text, line by line from [from], so "stop" knows where it was and "continue" goes on from there. */
    private fun readLines(lines: List<String>, from: Int) {
        reading = lines
        val rest = lines.drop(from)
        Log.i("Nungil", "Reader reads ${rest.size} lines from line $from: \"${rest.firstOrNull()?.take(60)}\"")
        services.speaker.sayLines(rest)
        engine.reading(SystemClock.elapsedRealtime(), rest.sumOf { it.length })
        _binding?.readerText?.text = lines.joinToString(" ")
    }

    /** "Another text", "next": this text is dropped and other text in view is offered at once. */
    private fun another() {
        main.removeCallbacks(giveUp)
        (activity as? MainActivity)?.dropWords()
        services.speaker.stop()
        engine.another()
        reading = emptyList()
        _binding?.readerAnswer?.isVisible = false
        if (!running) startReading()
        services.speaker.say(ReaderPolicy.lookingForAnother(lang))
    }

    /**
     * No answer in ReaderPolicy.ANSWER_MS (it was 15 s, and "yes" came 17 s after the offer, the app still talking:
     * the logs): the buttons go and the microphone is let go, so the next words are not taken for an answer.
     */
    private val giveUp = Runnable {
        _binding?.readerAnswer?.isVisible = false
        (activity as? MainActivity)?.dropWords()
    }

    /**
     * "Read" or "read text" said on this screen reads the text offered, not the reader opened again; "yes" or "no"
     * heard as no command (no longer waited for) answer the offer too.
     */
    override fun onVoiceCommand(command: VoiceCommand): Boolean {
        val yes = when (command) {
            VoiceCommand.ReadText -> true
            // "Again": the last text read, in full (elsewhere it says the last sentence again).
            VoiceCommand.Repeat -> {
                if (reading.isEmpty()) return false
                services.speaker.stop()
                readLines(reading, 0)
                return true
            }
            is VoiceCommand.Unknown -> {
                if (ReaderPolicy.wantsAnother(command.text)) {
                    another()
                    return true
                }
                ReaderPolicy.answer(command.text) ?: return false
            }
            else -> return false
        }
        answered(yes)
        return true
    }

    private fun onCameraError(error: Throwable) {
        Log.i("Nungil", "Reader camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }
}

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
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nungil.R
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.databinding.ReaderFragmentBinding
import com.nungil.scan.CameraSession
import com.nungil.search.CameraGate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads QR codes, barcodes and printed text aloud, every 1.5 s, never the same text twice within 10 s.
 * Version 1 reads Latin text only (Korean OCR needs an extra ML Kit model; see the Hand-offs).
 */
class ReaderFragment : Fragment(), VoiceHandler {
    private var _binding: ReaderFragmentBinding? = null
    private val binding get() = _binding!!
    private val gate = CameraGate(this) { startCamera() }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var lang: Lang

    private var camera: CameraSession? = null
    private var extras: ExecutorService? = null
    private val busy = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val policy = ReaderPolicy()

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
        binding.readerButton.setOnClickListener { if (running) pause() else resume() }
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
        scanner.close()
        recognizer.close()
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.ReadText, VoiceCommand.Start -> {
            resume()
            true
        }
        VoiceCommand.Stop -> {
            pause()
            true
        }
        else -> false
    }

    private fun startCamera() {
        if (camera != null || _binding == null) return
        val options = CameraSession.Options(facing = Facing.BACK, detect = false, keepBitmap = true)
        camera = CameraSession(this, binding.readerPreview, options, ::onFrame, ::onCameraError).also { it.start() }
    }

    private fun pause() {
        running = false
        binding.readerButton.setText(R.string.reader_resume)
    }

    private fun resume() {
        running = true
        lastRunMs = 0L
        binding.readerButton.setText(R.string.reader_pause)
    }

    /** Analysis thread. */
    private fun onFrame(frame: VisionFrame) {
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

    /** Extras thread: a code wins over text in the same frame. */
    private fun read(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val spoken = try {
            val code = Tasks.await(scanner.process(image), 2, TimeUnit.SECONDS)
                .firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
            if (code != null) {
                ReaderPolicy.codePhrase(code, lang)
            } else {
                val text = Tasks.await(recognizer.process(image), 2, TimeUnit.SECONDS)
                ReaderPolicy.longestLine(text.textBlocks.flatMap { block -> block.lines.map { it.text } })
            }
        } catch (e: Exception) {
            Log.i("Nungil", "Reader failed: ${e.message}")
            null
        } ?: return
        if (leaving || !policy.shouldSpeak(spoken, SystemClock.elapsedRealtime())) return
        services.speaker.say(spoken)
        main.post { _binding?.readerText?.text = spoken }
    }

    private fun onCameraError(error: Throwable) {
        Log.i("Nungil", "Reader camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }
}

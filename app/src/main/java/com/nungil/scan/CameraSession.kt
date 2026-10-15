package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nungil.contract.Detection
import com.nungil.contract.Facing
import com.nungil.contract.ScanSettings
import com.nungil.contract.app.SwitchableCamera
import com.nungil.contract.app.VisionFrame
import com.nungil.core.scan.DetectionFilter
import com.nungil.core.scan.DetectorPlan
import com.nungil.core.scan.FrameSlots
import com.nungil.core.scan.FrameStats
import com.nungil.core.scan.ModelShelf
import com.nungil.data.SettingsStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Binds CameraX preview + analysis to [fragment]'s view lifecycle, runs the object detector chosen in
 * SettingsStore and delivers one [VisionFrame] per analysed frame to [onFrame] on the analysis thread.
 * Frames are made upright on a thread of their own: with the detector, the next frame is made while the detector
 * works on this one ([FrameSlots]); frames that find no slot are dropped. [onError] runs on the main thread.
 */
class CameraSession(
    private val fragment: Fragment,
    private val previewView: PreviewView,
    private val options: Options,
    private val onFrame: (VisionFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
) : SwitchableCamera {
    data class Options(
        val facing: Facing = Facing.BACK,
        /** Run the object detector. false = frames only (face enrolment). */
        val detect: Boolean = true,
        /** Attach the upright bitmap to every frame (faces, items, colours). */
        val keepBitmap: Boolean = false,
        /** Fixed detector score floor; null = per-label DetectionFilter with the saved settings. */
        val minScore: Float? = null,
        /** The analysis frame asked of the camera (landscape, 4:3); the camera gives the nearest it has. */
        val analysisSize: Size = Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
        /**
         * Asked on the analysis thread before each frame is made upright; false drops the frame unmade. The reader
         * reads one big frame in 600 ms or more, and making each of the others took the processor from it.
         */
        val wants: (() -> Boolean)? = null,
        /**
         * false: the kept bitmap stays as the camera gives it, on its side, with the turn that sets it upright in
         * VisionFrame.rotation (the reader: ML Kit turns it itself; turning a 1920 x 2560 frame took 170-210 ms on the
         * phone). Frames for the detector are always made upright.
         */
        val upright: Boolean = true,
    )

    override var facing: Facing = options.facing
        private set

    /** Horizontal field of view of the upright image; 65° until the camera reports its own. */
    val hfovDeg: Float
        get() = fov

    @Volatile
    private var fov = DEFAULT_HFOV_DEG

    /**
     * How far away the lens is focused, in metres, or null while it is not in focus or does not say. Rough at
     * best: many phones report their focus distance as uncalibrated. Only what is near (under 2 m) says much.
     */
    val focusDistanceM: Float?
        get() {
            val diopters = focusDiopters
            if (diopters <= 0f || SystemClock.elapsedRealtime() - focusAtMs > FOCUS_FRESH_MS) return null
            return 1f / diopters
        }

    @Volatile
    private var focusDiopters = 0f

    @Volatile
    private var focusAtMs = 0L

    /** Camera thread: keeps the focus distance of the last frame that was in focus. */
    private val focusReader = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val state = result.get(CaptureResult.CONTROL_AF_STATE)
            val focused = state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
            val diopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: return
            if (!focused || diopters <= 0f) return
            focusDiopters = diopters
            focusAtMs = SystemClock.elapsedRealtime()
        }
    }

    @Volatile
    private var running = false

    @Volatile
    private var currentFacing = options.facing

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var executor: ExecutorService? = null
    /** Makes the camera's frames upright, ahead of the analysis thread. */
    private var converter: ExecutorService? = null
    private val slots = FrameSlots(if (options.detect) 2 else 1)
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var heading: HeadingProvider? = null
    @Volatile
    private var settings = ScanSettings()
    private var stopListening: (() -> Unit)? = null

    // Touched only on the analysis thread.
    /** The shared detector (SharedModels): kept loaded for the next screen when this one stops. */
    private var detector: ModelShelf.Lease<SharedDetector>? = null
    private var detectorFailed = false
    private val stats = FrameStats()

    /** The screen in the Perf log lines: "ScanFragment", "ReaderFragment". */
    private val screen = fragment.javaClass.simpleName

    /** Call from onViewCreated after the camera permission is granted. */
    fun start() {
        if (running) return
        val context = fragment.requireContext().applicationContext
        appContext = context
        val store = SettingsStore(context)
        settings = store.load()
        // "More sure" / "less sure" said while the camera runs: taken at the next frame.
        stopListening = store.onMinScore { score -> settings = settings.copy(minScore = score) }
        executor = Executors.newSingleThreadExecutor()
        converter = Executors.newSingleThreadExecutor()
        heading = HeadingProvider(context).also { it.start() }
        fov = FovReader.read(context, facing)
        running = true
        stats.started(SystemClock.uptimeMillis())
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!running) return@addListener
            try {
                provider = future.get()
                bind()
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Turns to the camera [to] when it is not on already; null turns to the other one. */
    override fun useCamera(to: Facing?) {
        if (to != facing) switchCamera()
    }

    fun switchCamera() {
        facing = if (facing == Facing.BACK) Facing.FRONT else Facing.BACK
        currentFacing = facing
        appContext?.let { fov = FovReader.read(it, facing) }
        if (running && provider != null) bind()
    }

    /** Call from onDestroyView. Waits at most 2 s for the analysis thread. */
    fun stop() {
        running = false
        stopListening?.invoke()
        stopListening = null
        heading?.stop()
        heading = null
        val p = provider
        val useCases = listOfNotNull(preview, analysis)
        if (p != null && useCases.isNotEmpty()) {
            try {
                p.unbind(*useCases.toTypedArray())
            } catch (e: Exception) {
                Log.i(TAG, "Unbinding the camera threw: ${e.message}")
            }
        }
        preview = null
        analysis = null
        provider = null
        converter?.shutdown()
        converter = null
        val ex = executor ?: return
        executor = null
        // The detector goes back to SharedModels after the last frame (it is closed there, on its own thread, later).
        ex.execute {
            detector?.close()
            detector = null
        }
        ex.shutdown()
        try {
            if (!ex.awaitTermination(2, TimeUnit.SECONDS)) ex.shutdownNow()
        } catch (e: InterruptedException) {
            ex.shutdownNow()
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bind() {
        val p = provider ?: return
        val conv = converter ?: return
        if (fragment.view == null) return
        val owner = fragment.viewLifecycleOwner
        val fourByThree = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val newPreview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(fourByThree).build())
            .build()
        newPreview.setSurfaceProvider(previewView.surfaceProvider)
        val analysisSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(fourByThree)
            .setResolutionStrategy(
                ResolutionStrategy(options.analysisSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()
        val analysisBuilder = ImageAnalysis.Builder()
            .setResolutionSelector(analysisSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        Camera2Interop.Extender(analysisBuilder).setSessionCaptureCallback(focusReader)
        val newAnalysis = analysisBuilder.build()
        newAnalysis.setAnalyzer(conv) { image -> analyze(image) }
        val selector = if (facing == Facing.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            val old = listOfNotNull(preview, analysis)
            if (old.isNotEmpty()) p.unbind(*old.toTypedArray())
            p.bindToLifecycle(owner, selector, newPreview, newAnalysis)
            preview = newPreview
            analysis = newAnalysis
        } catch (e: Exception) {
            onError(e)
        }
    }

    /** A frame made upright, on its way to the analysis thread. */
    private class Made(
        val bitmap: Bitmap, val width: Int, val height: Int, val rotation: Int, val arrived: Long, val convertMs: Long,
    )

    /** Converter thread: the frame made upright, then handed to the analysis thread. */
    private fun analyze(image: ImageProxy) {
        val made = try {
            if (!running || options.wants?.invoke() == false || !slots.take()) return
            try {
                val arrived = SystemClock.uptimeMillis()
                val turn = image.imageInfo.rotationDegrees
                val keepTurn = !options.upright && !options.detect && turn != 0
                val bitmap = if (keepTurn) image.toBitmap() else upright(image)
                val width = if (turn % 180 != 0) image.height else image.width
                val height = if (turn % 180 != 0) image.width else image.height
                Made(bitmap, width, height, if (keepTurn) turn else 0, arrived, SystemClock.uptimeMillis() - arrived)
            } catch (t: Throwable) {
                slots.free()
                throw t
            }
        } catch (t: Throwable) {
            // A bad frame must never stop the session or disable the screen's buttons.
            Log.i(TAG, "Frame failed: ${t.message}")
            main.post { onError(t) }
            return
        } finally {
            image.close()
        }
        try {
            val ex = executor ?: throw RejectedExecutionException("stopped")
            ex.execute {
                try {
                    handle(made)
                } finally {
                    slots.free()
                }
            }
        } catch (e: RejectedExecutionException) {
            slots.free()
        }
    }

    /** Analysis thread: the detector, then the screen's own work. */
    private fun handle(made: Made) {
        // Stopping: the detector has gone back to SharedModels; a late frame must not take it again.
        if (!running) return
        try {
            val started = SystemClock.uptimeMillis()
            val detections = if (options.detect) detect(made.bitmap) else emptyList()
            val inferenceMs = SystemClock.uptimeMillis() - started
            stats.firstFrame(made.arrived)?.let { Log.i(TAG, "Perf ${screen}: first frame $it ms after start, detect $inferenceMs ms") }
            val frame = VisionFrame(
                detections = detections,
                bitmap = if (options.keepBitmap) made.bitmap else null,
                imageWidth = made.width,
                imageHeight = made.height,
                facing = currentFacing,
                headingDeg = heading?.headingDeg,
                hfovDeg = fov,
                timestampMs = System.currentTimeMillis(),
                inferenceMs = inferenceMs,
                rotation = made.rotation,
            )
            val handed = SystemClock.uptimeMillis()
            onFrame(frame)
            stats.add(made.arrived, made.convertMs, inferenceMs, SystemClock.uptimeMillis() - handed)?.let { w ->
                val shared = detector?.model
                val model = if (options.detect) " on ${shared?.compute} (${shared?.modelFile})" else ""
                Log.i(
                    TAG,
                    "Perf $screen: ${"%.1f".format(w.fps)} fps ${made.width}x${made.height}, convert ${w.convertMs} ms, " +
                        "detect ${w.detectMs} ms, after ${w.afterMs} ms, total ${w.totalMs} ms$model",
                )
            }
        } catch (t: Throwable) {
            // A bad frame must never stop the session or disable the screen's buttons.
            Log.i(TAG, "Frame failed: ${t.message}")
            main.post { onError(t) }
        }
    }

    private fun upright(image: ImageProxy): Bitmap = upright(image.toBitmap(), image.imageInfo.rotationDegrees)

    private fun detect(bitmap: Bitmap): List<Detection> {
        if (detectorFailed) return emptyList()
        val shared = detector?.model ?: try {
            val context = appContext ?: return emptyList()
            SharedModels.detector(context, DetectorPlan.modelFile(settings.model), settings.compute).also { detector = it }.model
        } catch (t: Throwable) {
            detectorFailed = true
            main.post { onError(t) }
            return emptyList()
        }
        // Not inside ModelLock.run: the detector's own thread takes it (SharedDetector).
        val raw = shared.detect(bitmap)
        val floor = options.minScore
        return if (floor != null) DetectionFilter.keepAtLeast(raw, floor) else DetectionFilter.keep(raw, settings.minScore)
    }

    companion object {
        const val DEFAULT_HFOV_DEG = 65f
        const val ANALYSIS_WIDTH = 640
        const val ANALYSIS_HEIGHT = 480

        /** A focus distance older than this is no longer where the lens is. */
        private const val FOCUS_FRESH_MS = 1_500L
        private const val TAG = "Nungil"

        /** [bitmap] turned by [rotation] degrees; [bitmap] itself when there is no turn. */
        fun upright(bitmap: Bitmap, rotation: Int): Bitmap {
            if (rotation == 0) return bitmap
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            // Filtered: on the phone faster than without (1920 x 2560: 168 against 213 ms, TurnBench, 2026-10-15),
            // though not on the emulator.
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        /** [frame] with its bitmap upright (CameraSession.Options.upright false keeps it on its side). */
        fun upright(frame: VisionFrame): VisionFrame {
            val bitmap = frame.bitmap
            if (frame.rotation == 0 || bitmap == null) return frame
            return VisionFrame(
                frame.detections, upright(bitmap, frame.rotation), frame.imageWidth, frame.imageHeight, frame.facing,
                frame.headingDeg, frame.hfovDeg, frame.timestampMs, frame.inferenceMs,
            )
        }
    }
}

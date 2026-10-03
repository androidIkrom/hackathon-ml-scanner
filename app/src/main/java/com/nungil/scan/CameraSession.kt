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
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.nungil.contract.Detection
import com.nungil.contract.Facing
import com.nungil.contract.ScanSettings
import com.nungil.contract.app.VisionFrame
import com.nungil.core.scan.DetectionFilter
import com.nungil.core.scan.DetectorPlan
import com.nungil.core.scan.FrameMath
import com.nungil.core.scan.InferenceStats
import com.nungil.data.SettingsStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Binds CameraX preview + analysis to [fragment]'s view lifecycle, runs the object detector chosen in
 * SettingsStore and delivers one [VisionFrame] per analysed frame to [onFrame] on the analysis thread.
 * Frames that arrive while [onFrame] is still running are dropped. [onError] runs on the main thread.
 */
class CameraSession(
    private val fragment: Fragment,
    private val previewView: PreviewView,
    private val options: Options,
    private val onFrame: (VisionFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    data class Options(
        val facing: Facing = Facing.BACK,
        /** Run the object detector. false = frames only (face enrolment). */
        val detect: Boolean = true,
        /** Attach the upright bitmap to every frame (faces, items, colours). */
        val keepBitmap: Boolean = false,
        /** Fixed detector score floor; null = per-label DetectionFilter with the saved settings. */
        val minScore: Float? = null,
    )

    var facing: Facing = options.facing
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
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var heading: HeadingProvider? = null
    private var settings = ScanSettings()

    // Touched only on the analysis thread.
    private var detector: DetectorFactory.Built? = null
    private var detectorFailed = false
    private val stats = InferenceStats()

    /** Call from onViewCreated after the camera permission is granted. */
    fun start() {
        if (running) return
        val context = fragment.requireContext().applicationContext
        appContext = context
        settings = SettingsStore(context).load()
        executor = Executors.newSingleThreadExecutor()
        heading = HeadingProvider(context).also { it.start() }
        fov = FovReader.read(context, facing)
        running = true
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

    fun switchCamera() {
        facing = if (facing == Facing.BACK) Facing.FRONT else Facing.BACK
        currentFacing = facing
        appContext?.let { fov = FovReader.read(it, facing) }
        if (running && provider != null) bind()
    }

    /** Call from onDestroyView. Waits at most 2 s for the analysis thread. */
    fun stop() {
        running = false
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
        val ex = executor ?: return
        executor = null
        // Close the detector on its own thread: the GPU delegate must be used and closed on one thread.
        ex.execute {
            try {
                detector?.detector?.close()
            } catch (t: Throwable) {
                Log.i(TAG, "Closing the detector threw: ${t.message}")
            }
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
        val ex = executor ?: return
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
                ResolutionStrategy(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()
        val analysisBuilder = ImageAnalysis.Builder()
            .setResolutionSelector(analysisSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        Camera2Interop.Extender(analysisBuilder).setSessionCaptureCallback(focusReader)
        val newAnalysis = analysisBuilder.build()
        newAnalysis.setAnalyzer(ex) { image -> analyze(image) }
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

    private fun analyze(image: ImageProxy) {
        try {
            if (!running) return
            val upright = upright(image)
            val started = SystemClock.uptimeMillis()
            val detections = if (options.detect) detect(upright) else emptyList()
            val inferenceMs = SystemClock.uptimeMillis() - started
            if (options.detect) {
                stats.add(inferenceMs)?.let { average ->
                    val built = detector
                    Log.i(TAG, "Inference $average ms average on ${built?.compute} (${built?.modelFile})")
                }
            }
            val frame = VisionFrame(
                detections = detections,
                bitmap = if (options.keepBitmap) upright else null,
                imageWidth = upright.width,
                imageHeight = upright.height,
                facing = currentFacing,
                headingDeg = heading?.headingDeg,
                hfovDeg = fov,
                timestampMs = System.currentTimeMillis(),
                inferenceMs = inferenceMs,
            )
            onFrame(frame)
        } catch (t: Throwable) {
            // A bad frame must never stop the session or disable the screen's buttons.
            Log.i(TAG, "Frame failed: ${t.message}")
            main.post { onError(t) }
        } finally {
            image.close()
        }
    }

    private fun upright(image: ImageProxy): Bitmap {
        val raw = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }

    private fun detect(bitmap: Bitmap): List<Detection> {
        if (detectorFailed) return emptyList()
        val built = detector ?: try {
            val context = appContext ?: return emptyList()
            DetectorFactory(context).create(DetectorPlan.modelFile(settings.model), settings.compute).also { detector = it }
        } catch (t: Throwable) {
            detectorFailed = true
            main.post { onError(t) }
            return emptyList()
        }
        val result = built.detector.detect(BitmapImageBuilder(bitmap).build())
        val raw = result.detections().mapNotNull { d ->
            val category = d.categories().firstOrNull() ?: return@mapNotNull null
            val r = d.boundingBox()
            Detection(
                category.categoryName(),
                category.score(),
                FrameMath.normalizeBox(r.left, r.top, r.right, r.bottom, bitmap.width, bitmap.height),
            )
        }
        val floor = options.minScore
        return if (floor != null) DetectionFilter.keepAtLeast(raw, floor) else DetectionFilter.keep(raw, settings.minScore)
    }

    companion object {
        const val DEFAULT_HFOV_DEG = 65f
        private const val ANALYSIS_WIDTH = 640
        private const val ANALYSIS_HEIGHT = 480

        /** A focus distance older than this is no longer where the lens is. */
        private const val FOCUS_FRESH_MS = 1_500L
        private const val TAG = "Nungil"
    }
}

package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.nungil.contract.Compute
import com.nungil.core.scan.DetectionFilter
import com.nungil.core.scan.DetectorPlan
import java.io.File

/**
 * Builds the MediaPipe object detector. Call it on the thread that will run the detector:
 * the GPU delegate must be created and used on the same thread.
 */
class DetectorFactory(private val context: Context) {

    class Built(val detector: ObjectDetector, val compute: Compute, val modelFile: String)

    /**
     * Tries the delegates from [DetectorPlan.delegateOrder] and proves each with one 64x64 detect:
     * a GPU that loads a model it cannot execute otherwise fails on the first real frame and crashes on close.
     *
     * The GPU compiles its programs for the model on every start, which took 4.5 s for efficientdet-lite2
     * on a Mali-G57. They are kept on disk after the first start; when a start with that copy fails, the
     * copy is deleted and the GPU gets one more try without it.
     */
    fun create(modelFile: String, compute: Compute): Built {
        var lastError: Throwable? = null
        for (candidate in DetectorPlan.delegateOrder(compute, modelFile)) {
            val cache = if (candidate == Compute.GPU) gpuCache(modelFile) else null
            for (attempt in if (cache != null) listOf(cache, null) else listOf(null)) {
                if (attempt == null) cache?.deleteRecursively()
                val detector = try {
                    build(modelFile, candidate, attempt)
                } catch (t: Throwable) {
                    Log.i(TAG, "Detector $modelFile could not load on $candidate: ${t.message}")
                    lastError = t
                    continue
                }
                if (prove(detector)) {
                    Log.i(TAG, "Detector $modelFile running on $candidate")
                    return Built(detector, candidate, modelFile)
                }
                Log.i(TAG, "Detector $modelFile failed its trial on $candidate, falling back")
                closeQuietly(detector)
            }
        }
        throw IllegalStateException("No delegate can run $modelFile", lastError)
    }

    /** Android empties the code cache when the app or the system is updated, so a stale copy cannot survive. */
    private fun gpuCache(modelFile: String): File? =
        File(context.codeCacheDir, "gpu-" + modelFile.substringBeforeLast('.')).takeIf { it.isDirectory || it.mkdirs() }

    private fun build(modelFile: String, compute: Compute, gpuCache: File?): ObjectDetector {
        val base = BaseOptions.builder()
            .setModelAssetPath(modelFile)
            .setDelegate(if (compute == Compute.GPU) Delegate.GPU else Delegate.CPU)
            .apply {
                if (gpuCache != null) {
                    setDelegateOptions(
                        BaseOptions.DelegateOptions.GpuOptions.builder()
                            .setCachedKernelPath(gpuCache.path)
                            .setSerializedModelDir(gpuCache.path)
                            .setModelToken(MODEL_TOKEN)
                            .build(),
                    )
                }
            }
            .build()
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(DetectionFilter.DETECTOR_THRESHOLD)
            .setMaxResults(DetectorPlan.MAX_RESULTS)
            .build()
        return ObjectDetector.createFromOptions(context, options)
    }

    private fun prove(detector: ObjectDetector): Boolean = try {
        val size = DetectorPlan.TRIAL_SIZE_PX
        val trial = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        detector.detect(BitmapImageBuilder(trial).build())
        true
    } catch (t: Throwable) {
        Log.i(TAG, "Delegate trial failed: ${t.message}")
        false
    }

    private fun closeQuietly(detector: ObjectDetector) {
        try {
            detector.close()
        } catch (t: Throwable) {
            Log.i(TAG, "Closing a failed detector threw: ${t.message}")
        }
    }

    companion object {
        const val TAG = "Nungil"
        private const val MODEL_TOKEN = "model"
    }
}

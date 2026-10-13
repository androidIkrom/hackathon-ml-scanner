package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.nungil.contract.Compute
import com.nungil.contract.Detection
import com.nungil.core.scan.FrameMath
import com.nungil.core.scan.ModelLock
import java.io.Closeable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * An object detector that screens share (SharedModels): a MediaPipe one (EfficientDet, SSD) or YOLO26 (YoloDetector),
 * both giving detections with boxes as shares of the frame. The GPU delegate is built, used and closed on one thread:
 * each screen's camera has its own analysis thread, so every call goes to the detector's own thread and waits there.
 * That also lets one call in at a time. Never call [detect] inside ModelLock.run: the detector's thread takes it
 * itself, and a load waiting between the two would wait for ever.
 */
class SharedDetector private constructor(
    private val thread: ExecutorService,
    /** The MediaPipe detector, or null for YOLO. */
    val built: DetectorFactory.Built?,
    private val yolo: YoloDetector?,
    val modelFile: String,
) : Closeable {
    /** What the detector runs on, for the log. */
    val compute: Compute get() = built?.compute ?: if (yolo?.onGpu == true) Compute.GPU else Compute.CPU

    /** Every thing found in [frame], at the detector's own threshold. */
    fun detect(frame: Bitmap): List<Detection> = onThread(thread) {
        ModelLock.shared.run {
            yolo?.detect(frame) ?: built!!.detector.detect(BitmapImageBuilder(frame).build()).detections().mapNotNull { d ->
                val category = d.categories().firstOrNull() ?: return@mapNotNull null
                val r = d.boundingBox()
                Detection(category.categoryName(), category.score(), FrameMath.normalizeBox(r.left, r.top, r.right, r.bottom, frame.width, frame.height))
            }
        }
    }

    override fun close() {
        try {
            onThread(thread) { ModelLock.shared.load { built?.detector?.close() ?: yolo?.close() } }
        } finally {
            thread.shutdown()
        }
    }

    companion object {
        fun create(context: Context, modelFile: String, compute: Compute): SharedDetector {
            val thread = Executors.newSingleThreadExecutor { Thread(it, "detector ${modelFile.substringBeforeLast('.')}") }
            try {
                return onThread(thread) {
                    if (DetectorPlanFiles.isYolo(modelFile)) {
                        SharedDetector(thread, null, ModelLock.shared.load { YoloDetector(context, modelFile, compute == Compute.GPU) }, modelFile)
                    } else {
                        SharedDetector(thread, DetectorFactory(context).create(modelFile, compute), null, modelFile)
                    }
                }
            } catch (t: Throwable) {
                thread.shutdown()
                throw t
            }
        }

        private fun <T> onThread(thread: ExecutorService, call: () -> T): T = try {
            thread.submit<T> { call() }.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}

/** Which model files are YOLO (TFLite interpreter) rather than MediaPipe. */
object DetectorPlanFiles {
    fun isYolo(modelFile: String): Boolean = modelFile.startsWith("yolo")
}

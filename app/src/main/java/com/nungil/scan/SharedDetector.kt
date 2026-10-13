package com.nungil.scan

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import com.nungil.contract.Compute
import com.nungil.core.scan.ModelLock
import java.io.Closeable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * An object detector that screens share (SharedModels). The GPU delegate is built, used and closed on one thread:
 * each screen's camera has its own analysis thread, so every call goes to the detector's own thread and waits there.
 * That also lets one call in at a time. Never call [detect] inside ModelLock.run: the detector's thread takes it
 * itself, and a load waiting between the two would wait for ever.
 */
class SharedDetector private constructor(private val thread: ExecutorService, val built: DetectorFactory.Built) : Closeable {
    fun detect(image: MPImage): ObjectDetectorResult = onThread(thread) { ModelLock.shared.run { built.detector.detect(image) } }

    override fun close() {
        try {
            onThread(thread) { ModelLock.shared.load { built.detector.close() } }
        } finally {
            thread.shutdown()
        }
    }

    companion object {
        fun create(context: Context, modelFile: String, compute: Compute): SharedDetector {
            val thread = Executors.newSingleThreadExecutor { Thread(it, "detector ${modelFile.substringBeforeLast('.')}") }
            try {
                return SharedDetector(thread, onThread(thread) { DetectorFactory(context).create(modelFile, compute) })
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

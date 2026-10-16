package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.nungil.core.items.SegmentInput
import com.nungil.core.scan.ModelLock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * MediaPipe's interactive segmenter model (magic_touch, 6 MB): the outline of the thing at one point of the picture,
 * whatever kind of thing it is: the middle of the frame to begin with, then wherever the thing went.
 * Straight through LiteRT since 2026-10-15, its input and answer made as MediaPipe's InteractiveSegmenterLegacy made
 * them (SegmentInput, ItemSegmenterParityTest), and on the GPU when the phone takes it: 45 ms there against 203 on
 * the CPU (the phone; in the app LiteRT's CPU kernels use one thread whatever is asked for), MediaPipe 236. In Live
 * it shares the GPU with YOLO: a cut took 130 ms (MediaPipe's 414), YOLO 86 ms a frame (65), Live 10.2 fps (11.1).
 * One worker thread only. Throws from the constructor when the model is missing.
 */
class ItemSegmenter(context: Context) : Closeable {
    /** One answer: [values] row by row for a [width] x [height] picture (ItemMask reads them). */
    class Answer(val values: FloatArray, val width: Int, val height: Int)

    private var gpu: GpuDelegate? = null
    /** The GPU's compiled model is kept here between starts (6.4 s to build on the phone). */
    private val gpuCache = File(context.codeCacheDir, "gpu-magic_touch").apply { mkdirs() }
    private val interpreter: Interpreter = ModelLock.shared.load { open(loadModel(context), context) }
    private val side = interpreter.getInputTensor(0).shape()[1]
    private val pixels = IntArray(side * side)
    private val floats = FloatArray(side * side * 4)
    private val input = ByteBuffer.allocateDirect(4 * floats.size).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(interpreter.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder())
    private val square = FloatArray(side * side)

    private var calls = 0

    init {
        Log.i("Nungil", "Item segmenter on ${if (gpu != null) "GPU" else "CPU"}")
    }

    /** The GPU when the phone takes it and it runs a first picture; else the CPU. */
    private fun open(model: ByteBuffer, context: Context): Interpreter {
        try {
            val list = CompatibilityList()
            if (list.isDelegateSupportedOnThisDevice) {
                val token = MODEL.substringBeforeLast('.') + "-" + context.assets.openFd(MODEL).use { it.length }
                val delegate = GpuDelegate(list.bestOptionsForThisDevice.setSerializationParams(gpuCache.path, token))
                val onGpu = Interpreter(model, Interpreter.Options().addDelegate(delegate))
                try {
                    // A first picture proves it: some GPUs take the model and fail on it.
                    onGpu.run(
                        ByteBuffer.allocateDirect(onGpu.getInputTensor(0).numBytes()).order(ByteOrder.nativeOrder()),
                        ByteBuffer.allocateDirect(onGpu.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder()),
                    )
                    gpu = delegate
                    return onGpu
                } catch (t: Throwable) {
                    onGpu.close()
                    delegate.close()
                    throw t
                }
            }
            Log.i("Nungil", "Item segmenter: this phone's GPU is not supported, CPU instead")
        } catch (t: Throwable) {
            Log.i("Nungil", "Item segmenter could not run on the GPU (${t.message}), CPU instead")
        }
        return Interpreter(model, Interpreter.Options().setNumThreads(THREADS))
    }

    /**
     * The thing at ([x], [y]) (0..1) of [frame], or null when the segmenter fails. The answer is the model's one
     * mask; whether it counts the thing high or low, ItemMask reads it by the point asked for.
     */
    @Synchronized
    fun at(frame: Bitmap, x: Float, y: Float): Answer? = try {
        val start = SystemClock.elapsedRealtimeNanos()
        val scaled = Bitmap.createScaledBitmap(frame, side, side, true)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== frame) scaled.recycle()
        SegmentInput.fill(pixels, side, x, y, frame.width, frame.height, floats)
        input.rewind()
        input.asFloatBuffer().put(floats)
        output.rewind()
        val ready = SystemClock.elapsedRealtimeNanos()
        ModelLock.shared.run { interpreter.run(input, output) }
        val ran = SystemClock.elapsedRealtimeNanos()
        output.rewind()
        output.asFloatBuffer().get(square)
        val answer = Answer(SegmentInput.stretch(square, side, frame.width, frame.height), frame.width, frame.height)
        if (calls++ % 8 == 0) {
            val ms = { a: Long, b: Long -> (b - a) / 1_000_000 }
            Log.i(
                "Nungil",
                "Item segmenter: ${about(square, side)}; ${frame.width}x${frame.height} in " +
                    "${ms(start, ready)} + ${ms(ready, ran)} + ${ms(ran, SystemClock.elapsedRealtimeNanos())} ms",
            )
        }
        answer
    } catch (e: Exception) {
        Log.i("Nungil", "Item segmentation failed: ${e.message}")
        null
    }

    override fun close() = ModelLock.shared.load {
        interpreter.close()
        gpu?.close()
        Unit
    }

    private companion object {
        const val MODEL = "magic_touch.tflite"
        /** benchmark_model: two threads 112 ms on the phone, four 103; in the app LiteRT used one either way. */
        const val THREADS = 2

        /** The middle's value, the highest, how much is 0.5 or more and where: what the logs read. */
        fun about(values: FloatArray, side: Int): String {
            var max = 0f
            var high = 0
            var sx = 0L
            var sy = 0L
            for (i in values.indices) {
                val v = values[i]
                if (v > max) max = v
                if (v >= 0.5f) {
                    high++
                    sx += i % side
                    sy += i / side
                }
            }
            val at = if (high > 0) "at ${sx / high},${sy / high}" else "nowhere"
            val mid = values[(side / 2) * side + side / 2]
            return String.format(Locale.US, "%dx%d mid %.2f max %.2f high %d%% %s", side, side, mid, max, high * 100 / values.size, at)
        }

        /** Memory-maps the model from assets (app/build.gradle keeps .tflite uncompressed). */
        fun loadModel(context: Context) =
            context.assets.openFd(MODEL).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}

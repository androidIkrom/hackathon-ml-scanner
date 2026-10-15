package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.nungil.contract.Detection
import com.nungil.core.scan.DetectionFilter
import com.nungil.core.scan.FrameStats
import com.nungil.core.scan.YoloOutput
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Ultralytics YOLO26n (AGPL-3.0) on the TFLite interpreter (tools/yolo26/export.py makes the model), on the GPU when
 * [wantGpu] and the phone takes it, else on the CPU. The frame is fitted into the input with grey bars where the
 * shapes differ (YoloOutput.fit); the model gives one row per thing with no overlaps (YOLO26 needs no NMS). Built,
 * used and closed on one thread, one call at a time: SharedDetector's.
 */
class YoloDetector(context: Context, modelFile: String, wantGpu: Boolean) : Closeable {
    private var gpu: GpuDelegate? = null
    /** The GPU's compiled model is kept here between starts (3 s a start without it on the phone). */
    private val gpuCache = java.io.File(context.codeCacheDir, "gpu-" + modelFile.substringBeforeLast('.')).apply { mkdirs() }
    private val gpuToken = modelFile.substringBeforeLast('.') + "-" + modelFileSize(context, modelFile)
    private val interpreter: Interpreter = open(map(context, modelFile), wantGpu)

    /** The model runs on the GPU. */
    val onGpu: Boolean get() = gpu != null
    private val inShape = interpreter.getInputTensor(0).shape()

    /** The LiteRT export takes [1, 3, h, w] (colour planes first); [1, h, w, 3] is read too. */
    private val planes = inShape[1] == 3
    private val inputH = if (planes) inShape[2] else inShape[1]
    private val inputW = if (planes) inShape[3] else inShape[2]
    private val outShape = interpreter.getOutputTensor(0).shape()

    /** [1, rows, 6] as exported; [1, 6, rows] is read across. */
    private val across = outShape.size == 3 && outShape[1] in 4..8 && outShape[2] > outShape[1]
    private val rows = if (across) outShape[2] else outShape[1]
    private val rowSize = if (across) outShape[1] else outShape[2]

    private val input: ByteBuffer = ByteBuffer.allocateDirect(4 * inputW * inputH * 3).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(inputW * inputH)
    private val values = FloatArray(inputW * inputH * 3)
    private val canvasBitmap = Bitmap.createBitmap(inputW, inputH, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(canvasBitmap)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        Log.i("Nungil", "YOLO $modelFile on ${if (onGpu) "GPU" else "CPU"}: input ${inShape.contentToString()}, output ${outShape.contentToString()}")
    }

    /** The GPU when asked for and it runs a first frame; else the CPU. */
    private fun open(model: MappedByteBuffer, wantGpu: Boolean): Interpreter {
        if (wantGpu) {
            try {
                val list = CompatibilityList()
                if (list.isDelegateSupportedOnThisDevice) {
                    val delegate = GpuDelegate(list.bestOptionsForThisDevice.setSerializationParams(gpuCache.path, gpuToken))
                    val onGpu = Interpreter(model, Interpreter.Options().addDelegate(delegate))
                    try {
                        // A first frame proves it: some GPUs take the model and fail on it.
                        val inp = onGpu.getInputTensor(0)
                        val out = onGpu.getOutputTensor(0)
                        onGpu.run(
                            ByteBuffer.allocateDirect(inp.numBytes()).order(ByteOrder.nativeOrder()),
                            ByteBuffer.allocateDirect(out.numBytes()).order(ByteOrder.nativeOrder()),
                        )
                        gpu = delegate
                        return onGpu
                    } catch (t: Throwable) {
                        onGpu.close()
                        delegate.close()
                        throw t
                    }
                }
                Log.i("Nungil", "YOLO: this phone's GPU is not supported, CPU instead")
            } catch (t: Throwable) {
                Log.i("Nungil", "YOLO could not run on the GPU (${t.message}), CPU instead")
            }
        }
        return Interpreter(model, Interpreter.Options().setNumThreads(THREADS))
    }

    /** Things in [frame] scoring DetectionFilter.DETECTOR_THRESHOLD or more, boxes as shares of the frame. */
    fun detect(frame: Bitmap): List<Detection> {
        val started = SystemClock.uptimeMillis()
        val fit = YoloOutput.fit(frame.width, frame.height, inputW, inputH)
        canvas.drawColor(Color.rgb(PAD, PAD, PAD))
        canvas.drawBitmap(
            frame, null,
            RectF(fit.padX, fit.padY, fit.padX + frame.width * fit.scale, fit.padY + frame.height * fit.scale), paint,
        )
        canvasBitmap.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        // Into an array, then into the buffer at once: a put per value was a third of detect's time (2026-10-15).
        val n = pixels.size
        if (planes) {
            for (i in 0 until n) {
                val p = pixels[i]
                values[i] = SHARE[(p shr 16) and 0xFF]
                values[n + i] = SHARE[(p shr 8) and 0xFF]
                values[2 * n + i] = SHARE[p and 0xFF]
            }
        } else {
            for (i in 0 until n) {
                val p = pixels[i]
                values[3 * i] = SHARE[(p shr 16) and 0xFF]
                values[3 * i + 1] = SHARE[(p shr 8) and 0xFF]
                values[3 * i + 2] = SHARE[p and 0xFF]
            }
        }
        input.rewind()
        input.asFloatBuffer().put(values)
        val raw = Array(1) { Array(outShape[1]) { FloatArray(outShape[2]) } }
        val prepared = SystemClock.uptimeMillis()
        interpreter.run(input, raw)
        val ran = SystemClock.uptimeMillis()
        val flat = FloatArray(rows * rowSize)
        for (r in 0 until rows) for (c in 0 until rowSize) flat[r * rowSize + c] = if (across) raw[0][c][r] else raw[0][r][c]
        // The export gives the corners as shares of the input or as its pixels: shares are never over about 1.
        var biggest = 0f
        for (r in 0 until rows) if (flat[r * rowSize + 4] >= DetectionFilter.DETECTOR_THRESHOLD) {
            for (c in 0 until 4) biggest = maxOf(biggest, flat[r * rowSize + c])
        }
        val found = YoloOutput.detections(
            flat, rowSize, fit, inputW, inputH, frame.width, frame.height, DetectionFilter.DETECTOR_THRESHOLD,
            normalized = biggest <= NORMALIZED_MAX,
        )
        steps.add(started, prepared - started, ran - prepared, SystemClock.uptimeMillis() - ran)?.let {
            Log.i("Nungil", "Perf YOLO: prepare ${it.convertMs} ms, model ${it.detectMs} ms, read ${it.afterMs} ms on ${if (onGpu) "GPU" else "CPU"}")
        }
        return found
    }

    /** Where detect's time goes: the frame made into the input, the model, its output read (the Perf lines). */
    private val steps = FrameStats()

    override fun close() {
        interpreter.close()
        gpu?.close()
    }

    private companion object {
        const val THREADS = 4

        /** A colour byte as the model takes it, 0..1. */
        private val SHARE = FloatArray(256) { it / 255f }

        /** Ultralytics pads with this grey. */
        const val PAD = 114
        const val NORMALIZED_MAX = 1.5f

        fun modelFileSize(context: Context, file: String): Long = context.assets.openFd(file).use { it.declaredLength }

        fun map(context: Context, file: String): MappedByteBuffer =
            context.assets.openFd(file).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}

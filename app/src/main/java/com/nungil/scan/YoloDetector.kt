package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.nungil.contract.Detection
import com.nungil.core.scan.DetectionFilter
import com.nungil.core.scan.YoloOutput
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Ultralytics YOLO26n (AGPL-3.0) on the TFLite interpreter, CPU (tools/yolo26/export.py makes the model). The frame
 * is fitted into the square input with grey bars (YoloOutput.fit); the model gives one row per thing with no
 * overlaps (YOLO26 needs no NMS). One call at a time: SharedDetector runs it on its own thread.
 */
class YoloDetector(context: Context, modelFile: String) : Closeable {
    private val interpreter = Interpreter(map(context, modelFile), Interpreter.Options().setNumThreads(THREADS))
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
    private val canvasBitmap = Bitmap.createBitmap(inputW, inputH, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(canvasBitmap)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        Log.i("Nungil", "YOLO $modelFile: input ${inShape.contentToString()}, output ${outShape.contentToString()}")
    }

    /** Things in [frame] scoring DetectionFilter.DETECTOR_THRESHOLD or more, boxes as shares of the frame. */
    fun detect(frame: Bitmap): List<Detection> {
        val fit = YoloOutput.fit(frame.width, frame.height, inputW, inputH)
        canvas.drawColor(Color.rgb(PAD, PAD, PAD))
        canvas.drawBitmap(
            frame, null,
            RectF(fit.padX, fit.padY, fit.padX + frame.width * fit.scale, fit.padY + frame.height * fit.scale), paint,
        )
        canvasBitmap.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        input.rewind()
        val floats = input.asFloatBuffer()
        if (planes) {
            for (shift in intArrayOf(16, 8, 0)) for (p in pixels) floats.put(((p shr shift) and 0xFF) / 255f)
        } else {
            for (p in pixels) {
                floats.put(((p shr 16) and 0xFF) / 255f)
                floats.put(((p shr 8) and 0xFF) / 255f)
                floats.put((p and 0xFF) / 255f)
            }
        }
        val raw = Array(1) { Array(outShape[1]) { FloatArray(outShape[2]) } }
        interpreter.run(input, raw)
        val flat = FloatArray(rows * rowSize)
        for (r in 0 until rows) for (c in 0 until rowSize) flat[r * rowSize + c] = if (across) raw[0][c][r] else raw[0][r][c]
        // The export gives the corners as shares of the input or as its pixels: shares are never over about 1.
        var biggest = 0f
        for (r in 0 until rows) if (flat[r * rowSize + 4] >= DetectionFilter.DETECTOR_THRESHOLD) {
            for (c in 0 until 4) biggest = maxOf(biggest, flat[r * rowSize + c])
        }
        return YoloOutput.detections(
            flat, rowSize, fit, inputW, inputH, frame.width, frame.height, DetectionFilter.DETECTOR_THRESHOLD,
            normalized = biggest <= NORMALIZED_MAX,
        )
    }

    override fun close() = interpreter.close()

    private companion object {
        const val THREADS = 4

        /** Ultralytics pads with this grey. */
        const val PAD = 114
        const val NORMALIZED_MAX = 1.5f

        fun map(context: Context, file: String): MappedByteBuffer =
            context.assets.openFd(file).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}

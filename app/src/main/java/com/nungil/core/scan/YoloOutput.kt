package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Detection

/**
 * The YOLO26 detector's input and output, as pure maths (YoloDetector runs the model). The frame is fitted into the
 * square input without stretching, with grey bars ([fit]); the model gives one row per thing, x1, y1, x2, y2, score,
 * class, already without overlaps (YOLO26 needs no NMS), and [detections] turns them into boxes on the frame (0..1)
 * with the COCO names the rest of the app uses.
 */
object YoloOutput {
    /** How the frame sits in the input: scaled by [scale], then moved by [padX], [padY] pixels. */
    data class Fit(val scale: Float, val padX: Float, val padY: Float)

    fun fit(frameW: Int, frameH: Int, inputW: Int, inputH: Int): Fit {
        val scale = minOf(inputW.toFloat() / frameW, inputH.toFloat() / frameH)
        return Fit(scale, (inputW - frameW * scale) / 2f, (inputH - frameH * scale) / 2f)
    }

    /**
     * Detections from the model's [rows] (each [rowSize] floats: x1, y1, x2, y2, score, class) at [minScore] or
     * more. [normalized]: the coordinates are shares of the input, not pixels.
     */
    fun detections(
        rows: FloatArray, rowSize: Int, fit: Fit, inputW: Int, inputH: Int, frameW: Int, frameH: Int, minScore: Float,
        normalized: Boolean = false,
    ): List<Detection> {
        val out = mutableListOf<Detection>()
        val sx = if (normalized) inputW.toFloat() else 1f
        val sy = if (normalized) inputH.toFloat() else 1f
        for (i in 0 until rows.size / rowSize) {
            val o = i * rowSize
            val score = rows[o + 4]
            if (score < minScore) continue
            val label = COCO.getOrNull(rows[o + 5].toInt()) ?: continue
            fun x(v: Float) = ((v * sx - fit.padX) / fit.scale / frameW).coerceIn(0f, 1f)
            fun y(v: Float) = ((v * sy - fit.padY) / fit.scale / frameH).coerceIn(0f, 1f)
            out += Detection(label, score, Box(x(rows[o]), y(rows[o + 1]), x(rows[o + 2]), y(rows[o + 3])))
        }
        return out
    }

    /** COCO's 80 classes in the order YOLO numbers them, named as the MediaPipe detectors name them. */
    val COCO = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
        "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush",
    )
}

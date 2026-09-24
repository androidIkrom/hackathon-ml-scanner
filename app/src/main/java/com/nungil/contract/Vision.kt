package com.nungil.contract

/**
 * A box in the upright image as the user sees it, normalised to 0..1.
 * (0, 0) is the top-left corner. For the front camera the box is NOT mirrored;
 * only the on-screen overlay mirrors.
 */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width.coerceAtLeast(0f) * height.coerceAtLeast(0f)
}

/** One detector result. [label] is the COCO English label exactly as the model outputs it, e.g. "cell phone". */
data class Detection(val label: String, val score: Float, val box: Box)

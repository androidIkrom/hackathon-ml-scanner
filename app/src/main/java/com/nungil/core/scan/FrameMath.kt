package com.nungil.core.scan

import com.nungil.contract.Box

/** Pixel bookkeeping between the camera buffer and the upright image. */
object FrameMath {
    /** Size of the image after rotating a [width] x [height] buffer by [rotationDegrees] (0, 90, 180 or 270). */
    fun uprightSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 == 0) width to height else height to width

    /** Pixel rectangle of an upright [width] x [height] image to a normalised [Box], clamped to 0..1. */
    fun normalizeBox(left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int): Box {
        val w = width.toFloat()
        val h = height.toFloat()
        return Box(
            (left / w).coerceIn(0f, 1f),
            (top / h).coerceIn(0f, 1f),
            (right / w).coerceIn(0f, 1f),
            (bottom / h).coerceIn(0f, 1f),
        )
    }
}

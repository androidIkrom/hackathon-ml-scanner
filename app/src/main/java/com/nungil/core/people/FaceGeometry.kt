package com.nungil.core.people

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Pixel maths for face crops (no Android types, so it is unit-tested). Rects are [left, top, right, bottom]. */
object FaceGeometry {
    const val CROP_MARGIN = 0.1f
    const val ALIGNED_MARGIN = 0.35f
    const val LEVEL_MIN_DEG = 3f
    const val MIN_CROP_PX = 24

    /** Tilt of the line between the eyes, in degrees (-90..90); positive = the right-hand eye in the image is lower. */
    fun eyeAngleDeg(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val (x1, y1, x2, y2) = if (ax <= bx) listOf(ax, ay, bx, by) else listOf(bx, by, ax, ay)
        return Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()
    }

    fun needsLeveling(angleDeg: Float): Boolean = abs(angleDeg) >= LEVEL_MIN_DEG

    /** The rect grown by [margin] of its width/height on every side, clamped to a [width] x [height] image. */
    fun expand(left: Int, top: Int, right: Int, bottom: Int, margin: Float, width: Int, height: Int): IntArray {
        val dx = (right - left) * margin
        val dy = (bottom - top) * margin
        return clamp(left - dx, top - dy, right + dx, bottom + dy, width, height)
    }

    /** A rect of half-size [halfW] x [halfH] around (cx, cy), clamped to the image. */
    fun around(cx: Float, cy: Float, halfW: Float, halfH: Float, width: Int, height: Int): IntArray =
        clamp(cx - halfW, cy - halfH, cx + halfW, cy + halfH, width, height)

    /** Big enough to embed: both sides at least [MIN_CROP_PX]. */
    fun bigEnough(rect: IntArray): Boolean = rect[2] - rect[0] >= MIN_CROP_PX && rect[3] - rect[1] >= MIN_CROP_PX

    private fun clamp(l: Float, t: Float, r: Float, b: Float, width: Int, height: Int): IntArray = intArrayOf(
        l.roundToInt().coerceIn(0, width),
        t.roundToInt().coerceIn(0, height),
        r.roundToInt().coerceIn(0, width),
        b.roundToInt().coerceIn(0, height),
    )
}

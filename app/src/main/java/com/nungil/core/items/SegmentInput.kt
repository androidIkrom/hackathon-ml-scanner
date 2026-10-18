package com.nungil.core.items

import kotlin.math.roundToInt

/**
 * magic_touch's input and output as MediaPipe's InteractiveSegmenterLegacy makes them (its graph, read 2026-10-15):
 * the picture stretched to the model's square, red, green, blue from 0 to 1, and a fourth channel that is 1 on a dot
 * where the thing was pointed at and 0 elsewhere. MediaPipe draws that dot on the whole picture, as wide as the
 * picture is to the square (at least one pixel), and shrinks it with the picture. Its answer comes back square and
 * is stretched back over the picture with straight-line blending.
 */
object SegmentInput {
    private val SHARE = FloatArray(256) { it / 255f }

    /**
     * [pixels] (ARGB, [side] x [side], the picture already stretched) into [out], four floats each, with the dot at
     * ([x], [y]) (0..1) of the [width] x [height] picture the pixels were made from.
     */
    fun fill(pixels: IntArray, side: Int, x: Float, y: Float, width: Int, height: Int, out: FloatArray) {
        // MediaPipe gets the point in the picture's pixels, whole ones, and draws a dot that many pixels wide.
        val px = (x * width).toInt()
        val py = (y * height).toInt()
        val radius = maxOf(width.toFloat() / side, height.toFloat() / side, 1f).roundToInt()
        // The dot's middle and size on the square.
        val cx = (px + 0.5f) * side / width - 0.5f
        val cy = (py + 0.5f) * side / height - 0.5f
        val rx = radius * side.toFloat() / width
        val ry = radius * side.toFloat() / height
        var o = 0
        for (row in 0 until side) {
            val dy = (row - cy) / ry
            for (col in 0 until side) {
                val p = pixels[row * side + col]
                out[o++] = SHARE[(p shr 16) and 0xFF]
                out[o++] = SHARE[(p shr 8) and 0xFF]
                out[o++] = SHARE[p and 0xFF]
                val dx = (col - cx) / rx
                out[o++] = if (dx * dx + dy * dy <= 1f) 1f else 0f
            }
        }
    }

    /** The square answer [values] ([side] x [side]) stretched to [width] x [height], blending neighbours. */
    fun stretch(values: FloatArray, side: Int, width: Int, height: Int): FloatArray {
        val out = FloatArray(width * height)
        val sx = side.toFloat() / width
        val sy = side.toFloat() / height
        for (row in 0 until height) {
            val fy = ((row + 0.5f) * sy - 0.5f).coerceIn(0f, side - 1f)
            val y0 = fy.toInt()
            val y1 = minOf(y0 + 1, side - 1)
            val wy = fy - y0
            for (col in 0 until width) {
                val fx = ((col + 0.5f) * sx - 0.5f).coerceIn(0f, side - 1f)
                val x0 = fx.toInt()
                val x1 = minOf(x0 + 1, side - 1)
                val wx = fx - x0
                val top = values[y0 * side + x0] * (1 - wx) + values[y0 * side + x1] * wx
                val bottom = values[y1 * side + x0] * (1 - wx) + values[y1 * side + x1] * wx
                out[row * width + col] = top * (1 - wy) + bottom * wy
            }
        }
        return out
    }
}

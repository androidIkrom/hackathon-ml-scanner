package com.nungil.core.scan

import com.nungil.contract.Box
import kotlin.math.roundToInt

/**
 * Colour of one object: sample a 24x24 grid from the centre 50% of its box, white-balance each pixel with the
 * frame's gains, let every pixel vote for a name, and let a colourful name win once colourful pixels make up
 * at least 30% of the named pixels (a blue chair is mostly shadow and gray seat).
 */
object ColorVote {
    const val SAMPLE_GRID = 24
    const val CENTER_SHARE = 0.5f
    const val MIN_CHROMATIC_SHARE = 0.3f

    /** Grid used over the whole frame for white balance and darkness. */
    const val FRAME_GRID = 32

    /** A frame whose mean brightness (HSV value) is below this is too dark to name colours. */
    const val DARK_FRAME_V = 0.25f

    /** The centre [CENTER_SHARE] of [box]: half its width and half its height, same centre. */
    fun sampleRegion(box: Box): Box {
        val halfW = box.width * CENTER_SHARE / 2f
        val halfH = box.height * CENTER_SHARE / 2f
        return Box(box.centerX - halfW, box.centerY - halfH, box.centerX + halfW, box.centerY + halfH)
    }

    /** Pixel coordinates (x0, y0, x1, y1, …) of a [grid] x [grid] sample over [region] of a [width] x [height] image. */
    fun gridPoints(region: Box, width: Int, height: Int, grid: Int = SAMPLE_GRID): IntArray {
        val out = IntArray(grid * grid * 2)
        var k = 0
        for (row in 0 until grid) {
            val y = ((region.top + (row + 0.5f) * region.height / grid) * height).toInt().coerceIn(0, height - 1)
            for (col in 0 until grid) {
                val x = ((region.left + (col + 0.5f) * region.width / grid) * width).toInt().coerceIn(0, width - 1)
                out[k++] = x
                out[k++] = y
            }
        }
        return out
    }

    /** True when the frame's mean HSV value is below [DARK_FRAME_V]. */
    fun isDark(framePixels: IntArray): Boolean {
        if (framePixels.isEmpty()) return true
        var sum = 0f
        for (p in framePixels) {
            val maxC = maxOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            sum += maxC / 255f
        }
        return sum / framePixels.size < DARK_FRAME_V
    }

    /** Majority colour of [pixels] after white balance with [gains]; null in a dark frame. */
    fun vote(pixels: IntArray, gains: FloatArray, frameIsDark: Boolean): ColorName? {
        if (frameIsDark || pixels.isEmpty()) return null
        val counts = IntArray(ColorName.entries.size)
        for (p in pixels) {
            val q = WhiteBalance.apply(p, gains)
            val hsv = ColorMapper.hsv((q shr 16) and 0xFF, (q shr 8) and 0xFF, q and 0xFF)
            val name = ColorMapper.nameFromHsv(hsv[0], hsv[1], hsv[2], frameIsDark = false) ?: continue
            counts[name.ordinal]++
        }
        val named = counts.sum()
        if (named == 0) return null
        val chromatic = ColorName.entries.filter { it.isChromatic }
        val chromaticTotal = chromatic.sumOf { counts[it.ordinal] }
        // Whole percents, so exactly 30% counts (0.3f * 100 is 30.000002 in floating point).
        val sharePercent = (MIN_CHROMATIC_SHARE * 100).roundToInt()
        val pool = if (chromaticTotal > 0 && chromaticTotal * 100 >= sharePercent * named) chromatic else ColorName.entries
        return pool.maxByOrNull { counts[it.ordinal] }
    }
}

/** Which labels may carry a colour. People never get one. */
object ColorPolicy {
    fun hasColor(label: String): Boolean = label != "person"
}

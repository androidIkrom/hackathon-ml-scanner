package com.nungil.core.items

import com.nungil.core.scan.ColorMapper
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.WhiteBalance

/**
 * The colour of the thing being learned, from pixels that are all on the thing (ItemMask.samplePoints), after
 * scan's white balance of the whole frame (under warm light a white remote is orange without it). The most
 * common name wins outright: scan's rule that a colourful third beats a dull majority is for detector boxes
 * with background in them, and it called a black hat blue (the logs).
 */
object ItemColor {
    /** Below this value and saturation a pixel is black: a glossy dark surface shows its sheen, not its colour. */
    const val DARK_V = 0.35f
    const val DARK_S = 0.45f

    /** The most common colour of [pixels] (ARGB) under the frame's white-balance [gains], or null in a dark frame or with no pixels. */
    fun name(pixels: IntArray, gains: FloatArray, frameIsDark: Boolean): ColorName? {
        if (frameIsDark || pixels.isEmpty()) return null
        val counts = counts(pixels, gains)
        if (counts.all { it == 0 }) return null
        return ColorName.entries.maxByOrNull { counts[it.ordinal] }
    }

    /** How many of [pixels] have each colour under [gains], by ColorName ordinal. */
    fun counts(pixels: IntArray, gains: FloatArray): IntArray {
        val counts = IntArray(ColorName.entries.size)
        for (p in pixels) {
            val q = WhiteBalance.apply(p, gains)
            val name = of((q shr 16) and 0xFF, (q shr 8) and 0xFF, q and 0xFF) ?: continue
            counts[name.ordinal]++
        }
        return counts
    }

    /** The colours seen, most common first, as "black 53%, blue 18%" (for the log). */
    fun shares(pixels: IntArray, gains: FloatArray): String {
        val counts = counts(pixels, gains)
        val total = counts.sum().coerceAtLeast(1)
        return ColorName.entries.filter { counts[it.ordinal] > 0 }.sortedByDescending { counts[it.ordinal] }
            .joinToString(", ") { "${it.en} ${counts[it.ordinal] * 100 / total}%" }
    }

    /** One pixel's name. */
    fun of(r: Int, g: Int, b: Int): ColorName? {
        val hsv = ColorMapper.hsv(r, g, b)
        if (hsv[2] < DARK_V && hsv[1] < DARK_S) return ColorName.BLACK
        return ColorMapper.nameFromHsv(hsv[0], hsv[1], hsv[2], frameIsDark = false)
    }
}

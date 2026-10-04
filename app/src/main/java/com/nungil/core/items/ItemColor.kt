package com.nungil.core.items

import com.nungil.core.scan.ColorMapper
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.WhiteBalance

/**
 * The colour of the thing being learned, from pixels that are all on the thing (ItemMask.samplePoints), after
 * scan's white balance of the whole frame (under warm light a white remote is orange without it). The most
 * common name wins outright: scan's rule that a colourful third beats a dull majority is for detector boxes
 * with background in them, and it called a black hat blue (the logs).
 *
 * Light and dark are judged against the frame: the camera exposes a dim room so that its brightest parts come
 * out at 0.7, and a white bottle in it at 0.6 (the logs), which is gray by the fixed rule. The frame's
 * [whiteLevel] is what its brightest parts came out at, and a pixel's brightness is read as a part of that.
 */
object ItemColor {
    /** Below this value and saturation a pixel is black: a glossy dark surface shows its sheen, not its colour. */
    const val DARK_V = 0.35f
    const val DARK_S = 0.45f

    /** The brightest parts of the frame are its brightest [WHITE_PERCENTILE] of pixels. */
    const val WHITE_PERCENTILE = 95

    /** A frame is never stretched more than this: at night everything stays dark, not white. */
    const val MIN_WHITE_LEVEL = 0.6f

    /**
     * White balance takes a cast away and never paints one on: a pixel the camera shows with less saturation
     * than this (scan's line between white or gray and a colour) stays white, gray or black. A white charger,
     * a little cool (0.11), on a light wooden table that filled the frame was taken to 0.2 by the frame's
     * gains and called blue (the logs).
     */
    const val NEUTRAL_S = 0.15f

    /**
     * The most common colour of [pixels] (ARGB) under the frame's white-balance [gains], with brightness read
     * against the frame's [whiteLevel]; null in a dark frame or with no pixels.
     */
    fun name(pixels: IntArray, gains: FloatArray, frameIsDark: Boolean, whiteLevel: Float = 1f): ColorName? {
        if (frameIsDark || pixels.isEmpty()) return null
        val counts = counts(pixels, gains, whiteLevel)
        if (counts.all { it == 0 }) return null
        return ColorName.entries.maxByOrNull { counts[it.ordinal] }
    }

    /** How many of [pixels] have each colour under [gains] and [whiteLevel], by ColorName ordinal. */
    fun counts(pixels: IntArray, gains: FloatArray, whiteLevel: Float = 1f): IntArray {
        val counts = IntArray(ColorName.entries.size)
        val stretch = 1f / whiteLevel.coerceIn(MIN_WHITE_LEVEL, 1f)
        for (p in pixels) {
            val neutral = ColorMapper.hsv((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)[1] < NEUTRAL_S
            val q = WhiteBalance.apply(p, gains)
            val r = (((q shr 16) and 0xFF) * stretch).toInt().coerceAtMost(255)
            val g = (((q shr 8) and 0xFF) * stretch).toInt().coerceAtMost(255)
            val b = ((q and 0xFF) * stretch).toInt().coerceAtMost(255)
            val name = of(r, g, b, neutral) ?: continue
            counts[name.ordinal]++
        }
        return counts
    }

    /**
     * What the brightest parts of the frame came out at (0..1): the [WHITE_PERCENTILE] of the pixels' brightest
     * channel after [gains], at least [MIN_WHITE_LEVEL]. [framePixels] are spread over the whole frame.
     */
    fun whiteLevel(framePixels: IntArray, gains: FloatArray): Float {
        if (framePixels.isEmpty()) return 1f
        val bright = IntArray(framePixels.size) { i ->
            val q = WhiteBalance.apply(framePixels[i], gains)
            maxOf((q shr 16) and 0xFF, (q shr 8) and 0xFF, q and 0xFF)
        }
        bright.sort()
        val at = (bright.size * WHITE_PERCENTILE / 100).coerceAtMost(bright.size - 1)
        return (bright[at] / 255f).coerceIn(MIN_WHITE_LEVEL, 1f)
    }

    /** The colours seen, most common first, as "black 53%, blue 18%" (for the log). */
    fun shares(pixels: IntArray, gains: FloatArray, whiteLevel: Float = 1f): String {
        val counts = counts(pixels, gains, whiteLevel)
        val total = counts.sum().coerceAtLeast(1)
        return ColorName.entries.filter { counts[it.ordinal] > 0 }.sortedByDescending { counts[it.ordinal] }
            .joinToString(", ") { "${it.en} ${counts[it.ordinal] * 100 / total}%" }
    }

    /** One pixel's name; one that was [neutral] before white balance is named by its brightness alone. */
    fun of(r: Int, g: Int, b: Int, neutral: Boolean = false): ColorName? {
        val hsv = ColorMapper.hsv(r, g, b)
        val s = if (neutral) 0f else hsv[1]
        if (hsv[2] < DARK_V && s < DARK_S) return ColorName.BLACK
        return ColorMapper.nameFromHsv(hsv[0], s, hsv[2], frameIsDark = false)
    }
}

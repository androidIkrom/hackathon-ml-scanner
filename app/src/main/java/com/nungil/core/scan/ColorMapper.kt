package com.nungil.core.scan

import kotlin.math.max
import kotlin.math.min

/** HSV rules that give 11 basic colour names (build-guide §8.4). */
object ColorMapper {
    /** HSV of 8-bit RGB: hue 0 until 360, saturation and value 0..1. Pure Kotlin so it runs in unit tests. */
    fun hsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC
        val h = when {
            delta == 0f -> 0f
            maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        val s = if (maxC == 0f) 0f else delta / maxC
        return floatArrayOf(AngleMath.normalize(h), s, maxC)
    }

    /**
     * v < 0.2 black; s < 0.15 white above v 0.8, else gray; hues: red < 15 or >= 345, orange 15-45 (brown below
     * v 0.6), yellow 45-70, green 70-170, blue 170-260, purple 260-290, pink 290-345; a light red
     * (s < 0.5, v > 0.7) is pink. A dark frame gives no colour at all.
     */
    fun nameFromHsv(h: Float, s: Float, v: Float, frameIsDark: Boolean): ColorName? {
        if (frameIsDark) return null
        if (v < 0.2f) return ColorName.BLACK
        if (s < 0.15f) return if (v > 0.8f) ColorName.WHITE else ColorName.GRAY
        return when {
            h < 15f || h >= 345f -> if (s < 0.5f && v > 0.7f) ColorName.PINK else ColorName.RED
            h < 45f -> if (v < 0.6f) ColorName.BROWN else ColorName.ORANGE
            h < 70f -> ColorName.YELLOW
            h < 170f -> ColorName.GREEN
            h < 260f -> ColorName.BLUE
            h < 290f -> ColorName.PURPLE
            else -> ColorName.PINK
        }
    }
}

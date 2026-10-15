package com.nungil.core.items

import kotlin.math.sqrt

/**
 * MobileNet's input and output as MediaPipe's ImageEmbedder made them (its vectors and these agree to a cosine of
 * 0.992 at least on desk photos, the phone, 2026-10-15): red, green, blue from 0 to 1, and a vector one long.
 */
object EmbedInput {
    private val SHARE = FloatArray(256) { it / 255f }

    /** [pixels] (ARGB) into [out], three floats each. */
    fun fill(pixels: IntArray, out: FloatArray) {
        var o = 0
        for (p in pixels) {
            out[o++] = SHARE[(p shr 16) and 0xFF]
            out[o++] = SHARE[(p shr 8) and 0xFF]
            out[o++] = SHARE[p and 0xFF]
        }
    }

    /** [v] scaled to length one; all zero stays all zero. */
    fun unit(v: FloatArray): FloatArray {
        val n = sqrt(v.sumOf { it.toDouble() * it }).toFloat()
        return if (n == 0f) v.copyOf() else FloatArray(v.size) { v[it] / n }
    }
}

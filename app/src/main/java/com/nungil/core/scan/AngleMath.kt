package com.nungil.core.scan

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Compass maths in degrees. 0 = the reference direction, angles grow clockwise. */
object AngleMath {
    /** Any angle into 0 until 360. */
    fun normalize(deg: Float): Float {
        val r = deg % 360f
        val n = if (r < 0f) r + 360f else r
        return if (n >= 360f) 0f else n
    }

    /** Signed shortest turn from [b] to [a], in -180 until 180. diff(10, 350) = 20. */
    fun diff(a: Float, b: Float): Float {
        val d = normalize(a - b)
        return if (d >= 180f) d - 360f else d
    }

    /** 8 sectors of 45°: 0 front, 1 front right, 2 right, 3 back right, 4 back, 5 back left, 6 left, 7 front left. */
    fun sector8(relDeg: Float): Int = ((normalize(relDeg) + 22.5f) / 45f).toInt() % 8

    /** 4 sectors of 90° for speech: 0 front, 1 right, 2 behind, 3 left. */
    fun sector4(relDeg: Float): Int = ((normalize(relDeg) + 45f) / 90f).toInt() % 4

    /**
     * Circular mean of a running mean over [count] samples and one new [sample].
     * Stays stable across 0°/360°, where a plain average of 350 and 10 would give 180.
     */
    fun weightedMean(mean: Float, count: Int, sample: Float): Float {
        val m = Math.toRadians(mean.toDouble())
        val s = Math.toRadians(sample.toDouble())
        val x = cos(m) * count + cos(s)
        val y = sin(m) * count + sin(s)
        if (abs(x) < 1e-9 && abs(y) < 1e-9) return normalize(sample)
        return normalize(Math.toDegrees(atan2(y, x)).toFloat())
    }
}

package com.nungil.core.ui

/** A Canvas arc: degrees, 0 = three o'clock, positive = clockwise. */
data class Arc(val startDeg: Float, val sweepDeg: Float)

/** Geometry of the coverage ring: segment 0 starts at twelve o'clock, segments run clockwise. */
object RingGeometry {
    const val GAP_DEG = 2f

    /** Ring thickness as a share of the ring's diameter. */
    const val STROKE_SHARE = 0.09f

    fun segment(index: Int, count: Int): Arc {
        require(count > 0 && index in 0 until count) { "segment $index of $count" }
        val slice = 360f / count
        val gap = if (count == 1) 0f else GAP_DEG
        return Arc(-90f + index * slice + gap / 2f, slice - gap)
    }

    fun clampPercent(percent: Int): Int = percent.coerceIn(0, 100)
}

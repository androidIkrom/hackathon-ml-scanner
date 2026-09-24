package com.nungil.core.scan

import kotlin.math.abs

/**
 * Which parts of a 360° turn the camera has seen: [binCount] bins of 360 / binCount degrees,
 * bin 0 starting at the heading where the scan began. Drives the coverage ring and the auto-stop.
 */
class CoverageTracker(val binCount: Int = BIN_COUNT) {
    private val seen = BooleanArray(binCount)
    private val binDeg = 360f / binCount

    /** Marks the bin that holds [relHeading] (degrees from the start heading). */
    fun mark(relHeading: Float) {
        seen[binOf(relHeading)] = true
    }

    /**
     * Marks both ends and, when the turn between them is at most [MAX_ARC_DEG], every [STEP_DEG] in between,
     * so a normal turn leaves no holes but a jump (a fast spin or a sensor glitch) cannot claim the room.
     */
    fun markArc(from: Float, to: Float) {
        mark(from)
        mark(to)
        val turn = AngleMath.diff(to, from)
        if (abs(turn) > MAX_ARC_DEG) return
        val direction = if (turn >= 0f) 1f else -1f
        var step = STEP_DEG
        while (step < abs(turn)) {
            mark(from + direction * step)
            step += STEP_DEG
        }
    }

    fun percent(): Int = seen.count { it } * 100 / binCount

    fun isComplete(): Boolean = seen.all { it }

    /** Copy of the bins for the coverage ring; index 0 = the start heading. */
    fun bins(): BooleanArray = seen.copyOf()

    private fun binOf(relHeading: Float): Int = (AngleMath.normalize(relHeading) / binDeg).toInt().coerceIn(0, binCount - 1)

    companion object {
        const val BIN_COUNT = 36
        const val MAX_ARC_DEG = 45f
        const val STEP_DEG = 5f
    }
}

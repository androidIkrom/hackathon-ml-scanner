package com.nungil.core.walk

/**
 * The destination announced on the way in, at [THRESHOLDS] metres left (spec §5). A threshold counts only when
 * the walk started more than [ARM_MARGIN_M] beyond it: a route of 40 m does not begin with "in 50 metres".
 * When a GPS gap jumps over more than one, the nearest is said and both are spent.
 */
class GoApproach {
    private var armed: MutableList<Int>? = null

    /** The threshold just reached by [remainingM], or null. The first call only arms. */
    fun next(remainingM: Float): Int? {
        val left = armed ?: THRESHOLDS.filter { remainingM > it + ARM_MARGIN_M }.toMutableList().also { armed = it }
        val crossed = left.filter { remainingM <= it }
        if (crossed.isEmpty()) return null
        left.removeAll(crossed)
        return crossed.min()
    }

    companion object {
        val THRESHOLDS = listOf(50, 20)
        const val ARM_MARGIN_M = 10f
    }
}

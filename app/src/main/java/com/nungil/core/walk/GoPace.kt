package com.nungil.core.walk

/**
 * How fast the walker goes while they walk, for the time left (spec §2). [add] takes the progress towards the
 * destination: along the route, or how much the straight-line distance has shrunk.
 *
 * Only the last [WINDOW_MS] count, and of them only the chunks of at least [CHUNK_MS] in which the walker got
 * on by [MOVING_MPS] or more, and by more than twice the fix's accuracy. So standing at a crossing does not make
 * the time grow, walking back does not count, and GPS jitter is not walking: net progress over a chunk, not
 * the sum of steps, and more than the fixes wander. Until there are [MIN_MOVING_MS] and [MIN_MOVING_M] of walking the last speed
 * stands, [DEFAULT_MPS] before any: about what a walker with a cane keeps.
 */
class GoPace {
    private val times = ArrayDeque<Long>()
    private val progress = ArrayDeque<Float>()
    private val accuracy = ArrayDeque<Float>()

    var speedMps: Float = DEFAULT_MPS
        private set

    /** [accuracyM]: how good the fix is (0 unknown); a chunk must get on by twice it to count as walking. */
    fun add(nowMs: Long, progressM: Float, accuracyM: Float = 0f) {
        times.addLast(nowMs)
        progress.addLast(progressM)
        accuracy.addLast(accuracyM)
        while (times.isNotEmpty() && nowMs - times.first() > WINDOW_MS) {
            times.removeFirst()
            progress.removeFirst()
            accuracy.removeFirst()
        }
        measure()
    }

    /** A new route: its progress starts again from 0, the speed found so far stays. */
    fun restart() {
        times.clear()
        progress.clear()
        accuracy.clear()
    }

    fun secondsFor(metres: Float): Float = metres / speedMps

    private fun measure() {
        var movingMs = 0L
        var movingM = 0f
        var from = 0
        for (i in 1 until times.size) {
            val dt = times[i] - times[from]
            if (dt < CHUNK_MS) continue
            val dp = progress[i] - progress[from]
            // Fixes good to 8 m wander 14 m apart while the walker stands: that is not walking (the review).
            val jitter = JITTER_FACTOR * maxOf(accuracy[i], accuracy[from])
            if (dp >= maxOf(MOVING_MPS * dt / 1000f, jitter)) {
                movingMs += dt
                movingM += dp
            }
            from = i
        }
        if (movingMs >= MIN_MOVING_MS && movingM >= MIN_MOVING_M) {
            speedMps = (movingM / (movingMs / 1000f)).coerceIn(MIN_MPS, MAX_MPS)
        }
    }

    companion object {
        const val DEFAULT_MPS = 1.0f
        const val WINDOW_MS = 120_000L
        /** 20 s, not 10: a walker at 0.5 m/s must get past twice a 5 m fix's accuracy within one chunk. */
        const val CHUNK_MS = 20_000L
        const val JITTER_FACTOR = 2f
        const val MOVING_MPS = 0.3f
        const val MIN_MOVING_MS = 20_000L
        const val MIN_MOVING_M = 10f
        const val MIN_MPS = 0.5f
        const val MAX_MPS = 2.0f
    }
}

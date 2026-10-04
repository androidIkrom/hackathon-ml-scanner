package com.nungil.core.walk

/**
 * How fast the walker goes while they walk, for the time left (spec §2). [add] takes the progress towards the
 * destination: along the route, or how much the straight-line distance has shrunk.
 *
 * Only the last [WINDOW_MS] count, and of them only the chunks of at least [CHUNK_MS] in which the walker got
 * on by [MOVING_MPS] or more. So standing at a crossing does not make the time grow, walking back does not
 * count, and GPS jitter of ±8 m a second, which is metres a second step by step, is nothing over 10 s: net
 * progress, not the sum of steps. Until there are [MIN_MOVING_MS] and [MIN_MOVING_M] of walking the last speed
 * stands, [DEFAULT_MPS] before any: about what a walker with a cane keeps.
 */
class GoPace {
    private val times = ArrayDeque<Long>()
    private val progress = ArrayDeque<Float>()

    var speedMps: Float = DEFAULT_MPS
        private set

    fun add(nowMs: Long, progressM: Float) {
        times.addLast(nowMs)
        progress.addLast(progressM)
        while (times.isNotEmpty() && nowMs - times.first() > WINDOW_MS) {
            times.removeFirst()
            progress.removeFirst()
        }
        measure()
    }

    /** A new route: its progress starts again from 0, the speed found so far stays. */
    fun restart() {
        times.clear()
        progress.clear()
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
            if (dp / (dt / 1000f) >= MOVING_MPS) {
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
        const val CHUNK_MS = 10_000L
        const val MOVING_MPS = 0.3f
        const val MIN_MOVING_MS = 20_000L
        const val MIN_MOVING_M = 10f
        const val MIN_MPS = 0.5f
        const val MAX_MPS = 2.0f
    }
}

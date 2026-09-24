package com.nungil.core.ui

import com.nungil.contract.Buzz

/**
 * The haptic vocabulary: one distinct vibration per [Buzz], so a blind user can tell "found" from
 * "obstacle" without waiting for speech. Waveforms use Android's format: [off, on, off, on, …] in ms,
 * with amplitudes 0 for off and 1..255 for on.
 */
object HapticPatterns {
    private class Wave(val timings: LongArray, val amplitudes: IntArray)

    private val waves: Map<Buzz, Wave> = mapOf(
        Buzz.TAP to Wave(longArrayOf(0, 20), intArrayOf(0, 160)),
        Buzz.FOUND to Wave(longArrayOf(0, 40, 60, 40), intArrayOf(0, 200, 0, 200)),
        Buzz.CENTERED to Wave(longArrayOf(0, 60), intArrayOf(0, 255)),
        Buzz.LOST to Wave(longArrayOf(0, 150), intArrayOf(0, 120)),
        Buzz.OBSTACLE to Wave(longArrayOf(0, 80, 60, 80, 60, 80), intArrayOf(0, 255, 0, 255, 0, 255)),
        Buzz.DONE to Wave(longArrayOf(0, 30, 50, 50, 50, 70), intArrayOf(0, 100, 0, 170, 0, 255)),
        Buzz.ERROR to Wave(longArrayOf(0, 300), intArrayOf(0, 255)),
    )

    fun timings(kind: Buzz): LongArray = waves.getValue(kind).timings.copyOf()

    fun amplitudes(kind: Buzz): IntArray = waves.getValue(kind).amplitudes.copyOf()

    /** Number of separate pulses. */
    fun pulses(kind: Buzz): Int = timings(kind).indices.count { it % 2 == 1 }

    /** Total vibrating time in ms. */
    fun onMs(kind: Buzz): Long = timings(kind).filterIndexed { i, _ -> i % 2 == 1 }.sum()
}

/** Timing rules for the repeating search beep. */
object BeepPolicy {
    const val BEEP_MS = 60
    const val MIN_INTERVAL_MS = 100L

    /** Start at once when the beep was off, or when it must go faster; slowing down waits for the next beep. */
    fun restartNow(oldIntervalMs: Long, newIntervalMs: Long): Boolean =
        oldIntervalMs <= 0 || newIntervalMs < oldIntervalMs

    fun clamp(intervalMs: Long): Long = maxOf(intervalMs, MIN_INTERVAL_MS)
}

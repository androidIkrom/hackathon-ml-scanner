package com.nungil.core.scan

import java.util.concurrent.atomic.AtomicInteger

/**
 * How many camera frames may be in hand at once, from made upright to done: 2 lets the next frame be made upright
 * while the detector works on this one (Live on the phone: 13 ms of an 85 ms frame the GPU stood idle, 2026-10-15),
 * 1 is one frame at a time. A frame that finds no slot is dropped, so a slow detector never queues stale frames.
 */
class FrameSlots(private val limit: Int) {
    private val used = AtomicInteger()

    /** A slot for one frame, or false when all are taken. Any thread. */
    fun take(): Boolean {
        while (true) {
            val n = used.get()
            if (n >= limit) return false
            if (used.compareAndSet(n, n + 1)) return true
        }
    }

    /** The frame from [take] is done (or dropped). */
    fun free() {
        used.decrementAndGet()
    }
}

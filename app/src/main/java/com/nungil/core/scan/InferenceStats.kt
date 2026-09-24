package com.nungil.core.scan

/** Averages inference times and reports once every [every] frames, to decide CPU versus GPU on the real phone. */
class InferenceStats(private val every: Int = EVERY_FRAMES) {
    private var count = 0
    private var total = 0L

    /** Adds one timing; returns the average of the last [every] frames when it is time to log, else null. */
    fun add(ms: Long): Long? {
        count++
        total += ms
        if (count < every) return null
        val average = total / count
        count = 0
        total = 0
        return average
    }

    companion object {
        const val EVERY_FRAMES = 30
    }
}

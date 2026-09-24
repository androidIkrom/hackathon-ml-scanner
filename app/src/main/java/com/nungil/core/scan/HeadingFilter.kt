package com.nungil.core.scan

/** Low-pass filter for a compass heading that turns the short way across 0°/360°. */
class HeadingFilter(private val alpha: Float = ALPHA) {
    private var value: Float? = null

    /** Feeds one raw heading (degrees) and returns the smoothed heading in 0 until 360. */
    fun update(sample: Float): Float {
        val prev = value
        val next = if (prev == null) AngleMath.normalize(sample) else AngleMath.normalize(prev + alpha * AngleMath.diff(sample, prev))
        value = next
        return next
    }

    companion object {
        /** Share of each new reading; 0.2 settles within about ten sensor events at SENSOR_DELAY_GAME. */
        const val ALPHA = 0.2f
    }
}

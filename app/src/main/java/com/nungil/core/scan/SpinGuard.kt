package com.nungil.core.scan

import kotlin.math.abs

/** Says when the user turns faster than [maxDegPerSec], at most once every [repeatMs]. */
class SpinGuard(val maxDegPerSec: Float = MAX_DEG_PER_SEC, val repeatMs: Long = REPEAT_MS) {
    private var refTime: Long? = null
    private var refHeading = 0f
    private var lastWarning: Long? = null

    /** Feed every heading; true means "say Slow down now". Speed is measured over at least [MIN_WINDOW_MS]. */
    fun update(nowMs: Long, headingDeg: Float): Boolean {
        val since = refTime
        if (since == null) {
            refTime = nowMs
            refHeading = headingDeg
            return false
        }
        val dt = nowMs - since
        if (dt < MIN_WINDOW_MS) return false
        val speed = abs(AngleMath.diff(headingDeg, refHeading)) * 1000f / dt
        refTime = nowMs
        refHeading = headingDeg
        if (speed <= maxDegPerSec) return false
        val last = lastWarning
        if (last != null && nowMs - last < repeatMs) return false
        lastWarning = nowMs
        return true
    }

    companion object {
        const val MAX_DEG_PER_SEC = 60f
        const val REPEAT_MS = 5_000L

        /**
         * Live: "Slow down" at most this often. Every 5 s it was said four times in a minute and a half while the
         * user looked around, and talked over what was found (the logs, 2026-10-13). Look around keeps [REPEAT_MS]:
         * there turning too fast leaves gaps in the scan.
         */
        const val LIVE_REPEAT_MS = 20_000L
        const val MIN_WINDOW_MS = 100L
    }
}

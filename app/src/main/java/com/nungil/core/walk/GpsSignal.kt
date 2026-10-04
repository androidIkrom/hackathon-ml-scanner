package com.nungil.core.walk

/**
 * Whether to tell the walker that the GPS is too weak to trust (spec §4): its accuracy has been worse than
 * [WEAK_M] for [WEAK_FOR_MS]. Said once, and again only after it has been [GOOD_M] or better. An accuracy of 0
 * is unknown, not perfect, and never counts as weak.
 */
class GpsSignal {
    private var weakSince: Long? = null
    private var warned = false

    /** True when the warning is to be said now. */
    fun update(nowMs: Long, accuracyM: Float): Boolean {
        if (accuracyM > 0f && accuracyM <= GOOD_M) warned = false
        if (accuracyM <= WEAK_M) {
            weakSince = null
            return false
        }
        val since = weakSince ?: nowMs.also { weakSince = it }
        if (warned || nowMs - since < WEAK_FOR_MS) return false
        warned = true
        return true
    }

    companion object {
        const val WEAK_M = 30f
        const val WEAK_FOR_MS = 10_000L
        const val GOOD_M = 20f
    }
}

/**
 * Whether a fix still tells where the walker is. The phone's last known place can be minutes and kilometres
 * old, and "where am I" named the wrong street from it with confidence (the review).
 */
object FixAge {
    const val FRESH_MS = 10_000L

    fun fresh(fixAtMs: Long?, nowMs: Long): Boolean = fixAtMs != null && nowMs - fixAtMs <= FRESH_MS

    /**
     * Whether the fix tells where the walker is: one measured since the location updates started at
     * [liveSinceMs] always does, however old, because a new one comes only after 1 m of moving (standing
     * indoors, "where am I" waited for one that never came, the logs); the last known place from before only
     * while [fresh].
     */
    fun usable(fixAtMs: Long?, liveSinceMs: Long?, nowMs: Long): Boolean =
        fixAtMs != null && (fresh(fixAtMs, nowMs) || liveSinceMs != null && fixAtMs >= liveSinceMs)
}

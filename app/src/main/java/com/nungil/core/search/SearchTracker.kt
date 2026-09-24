package com.nungil.core.search

/**
 * Decides what to say while hunting one target. Feed it every analysed result, in time order, from one thread.
 *
 * - The first sighting (at the start, or after "Lost it") is announced at once.
 * - A new zone is announced at most once every [repeatMs]; the same zone is never repeated.
 * - "Lost it" once, after [lostAfterMs] without the target.
 */
class SearchTracker(
    private val lostAfterMs: Long = LOST_AFTER_MS,
    private val repeatMs: Long = REPEAT_MS,
) {
    sealed interface Say {
        data class Where(val zone: Zone) : Say
        data object Lost : Say
    }

    /** [say] null = stay quiet; [enteredCenter] = the target just moved into the ahead zone (vibrate). */
    data class Update(val say: Say?, val enteredCenter: Boolean)

    private var lastSeenMs = -1L
    private var lastSpokenMs = Long.MIN_VALUE / 2
    private var lastSpokenZone: Zone? = null
    private var lastZone: Zone? = null
    private var visible = false

    fun update(nowMs: Long, zone: Zone?): Update {
        if (zone == null) {
            lastZone = null
            if (visible && nowMs - lastSeenMs >= lostAfterMs) {
                visible = false
                lastSpokenZone = null
                lastSpokenMs = nowMs
                return Update(Say.Lost, false)
            }
            return Update(null, false)
        }
        val enteredCenter = zone == Zone.AHEAD && lastZone != Zone.AHEAD
        lastZone = zone
        lastSeenMs = nowMs
        val firstSighting = !visible
        visible = true
        val speak = firstSighting || (zone != lastSpokenZone && nowMs - lastSpokenMs >= repeatMs)
        if (!speak) return Update(null, enteredCenter)
        lastSpokenZone = zone
        lastSpokenMs = nowMs
        return Update(Say.Where(zone), enteredCenter)
    }

    companion object {
        const val LOST_AFTER_MS = 3_000L
        const val REPEAT_MS = 2_000L
    }
}

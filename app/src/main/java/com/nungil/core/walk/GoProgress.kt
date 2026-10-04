package com.nungil.core.walk

/**
 * When Go mode says the distance and the time left (spec §1): every [EVERY_MS] while a destination is set. It
 * is put off, never dropped, while the next turn is within [TURN_NEAR_M] (its own announcement is near),
 * another navigation sentence was said in the last [QUIET_AFTER_NAV_MS], or a new route is being fetched; it is
 * said at the first moment none holds. An answer to "how far" counts as one ([said]). [quiet]: the walker said
 * "quiet updates"; turns and warnings are not this class's and go on.
 */
class GoProgress(quiet: Boolean = false) {
    enum class Verdict { OFF, QUIET, NOT_YET, TURN_NEAR, JUST_SPOKE, REROUTING, SAY }

    private var dueAt: Long? = null

    var quiet: Boolean = quiet
        private set

    /**
     * "Quiet updates" / "updates on". Back on, the next update is a minute away: one overdue from the quiet
     * minutes cut off "Minute updates on." 200 ms after it began (the review).
     */
    fun setQuiet(quiet: Boolean, nowMs: Long) {
        if (this.quiet && !quiet) said(nowMs)
        this.quiet = quiet
    }

    /** A destination is set: the first update a minute from now. */
    fun start(nowMs: Long) {
        dueAt = nowMs + EVERY_MS
    }

    /** No destination any more. */
    fun stop() {
        dueAt = null
    }

    val started: Boolean get() = dueAt != null

    /** Whether to say the update now ([Verdict.SAY]), or why not. [nextTurnM] is null after the last turn and by beacon. */
    fun check(nowMs: Long, nextTurnM: Float?, lastNavSaidMs: Long, rerouting: Boolean): Verdict {
        val due = dueAt ?: return Verdict.OFF
        return when {
            quiet -> Verdict.QUIET
            nowMs < due -> Verdict.NOT_YET
            rerouting -> Verdict.REROUTING
            nextTurnM != null && nextTurnM <= TURN_NEAR_M -> Verdict.TURN_NEAR
            nowMs - lastNavSaidMs < QUIET_AFTER_NAV_MS -> Verdict.JUST_SPOKE
            else -> Verdict.SAY
        }
    }

    /** An update, or an answer to "how far", was said: the next one a minute from now. */
    fun said(nowMs: Long) {
        if (dueAt != null) dueAt = nowMs + EVERY_MS
    }

    companion object {
        const val EVERY_MS = 60_000L
        const val TURN_NEAR_M = 25f
        const val QUIET_AFTER_NAV_MS = 15_000L
    }
}

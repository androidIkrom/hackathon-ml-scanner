package com.nungil.core.walk

import com.nungil.contract.Lang

/**
 * Tells the user when walk mode stops measuring distance (walls, steps and stairs go unchecked) and why,
 * and when it measures again. The notice stays a candidate every frame while depth is missing, so a
 * busier alert cannot swallow it, and it is repeated every [REMIND_MS] while the problem lasts.
 */
class DepthStatus {
    private var missingSince: Long? = null
    private var workingSince: Long? = null
    private var lostSaid = false
    private var dark = false
    private var blankAhead = false
    private var workedBefore = false

    /**
     * [depthWorking]: this frame had depth. [tooDark]: ARCore says there is not enough light. [blank]:
     * ARCore finds nothing to hold on to in the picture, which is what a plain wall right ahead looks like.
     */
    fun update(now: Long, depthWorking: Boolean, tooDark: Boolean, lang: Lang, blank: Boolean = false): Alert? {
        if (depthWorking) {
            missingSince = null
            dark = false
            blankAhead = false
            workedBefore = true
            val since = workingSince ?: now.also { workingSince = it }
            if (!lostSaid || now - since < BACK_AFTER_MS) return null
            if (now - since >= BACK_AFTER_MS + BACK_WINDOW_MS) {
                lostSaid = false
                return null
            }
            return Alert(AlertKind.DEPTH, "depth:back", WalkPhrases.depthBack(lang))
        }
        workingSince = null
        // Once it was too dark, keep saying so until depth is back: the reason flickers while tracking fails.
        if (tooDark) dark = true
        if (blank) blankAhead = true
        val missing = now - (missingSince ?: now.also { missingSince = it })
        val after = when {
            dark -> DARK_AFTER_MS
            blankAhead -> BLANK_AFTER_MS
            workedBefore -> LOST_AGAIN_AFTER_MS
            else -> LOST_AFTER_MS
        }
        if (missing < after) return null
        lostSaid = true
        val round = (missing - after) / REMIND_MS
        return when {
            dark -> Alert(AlertKind.DEPTH, "depth:dark:$round", WalkPhrases.tooDark(lang))
            // A possible wall is about what is straight ahead: it is said at once, before anything else.
            blankAhead -> Alert(AlertKind.DEPTH, "depth:blank:$round", WalkPhrases.blankAhead(lang), ahead = true)
            else -> Alert(AlertKind.DEPTH, "depth:lost:$round", WalkPhrases.depthLost(lang))
        }
    }

    companion object {
        /** Too dark is certain at once; "no depth, no reason" is also how ARCore starts, so wait longer. */
        const val DARK_AFTER_MS = 3_000L
        const val LOST_AFTER_MS = 8_000L

        /** Once depth has worked, a loss is not ARCore starting up any more. */
        const val LOST_AGAIN_AFTER_MS = 3_000L
        const val BLANK_AFTER_MS = 1_500L
        const val REMIND_MS = 60_000L

        /** Depth must work this long before "measuring again" (no chatter when it flickers back for a frame). */
        const val BACK_AFTER_MS = 1_500L
        const val BACK_WINDOW_MS = 3_000L
    }
}

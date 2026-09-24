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

    /** [depthWorking]: this frame had depth. [tooDark]: ARCore says there is not enough light. */
    fun update(now: Long, depthWorking: Boolean, tooDark: Boolean, lang: Lang): Alert? {
        if (depthWorking) {
            missingSince = null
            dark = false
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
        val missing = now - (missingSince ?: now.also { missingSince = it })
        val after = if (dark) DARK_AFTER_MS else LOST_AFTER_MS
        if (missing < after) return null
        lostSaid = true
        val round = (missing - after) / REMIND_MS
        val why = if (dark) "dark" else "lost"
        return Alert(AlertKind.DEPTH, "depth:$why:$round", if (dark) WalkPhrases.tooDark(lang) else WalkPhrases.depthLost(lang))
    }

    companion object {
        /** Too dark is certain at once; "no depth, no reason" is also how ARCore starts, so wait longer. */
        const val DARK_AFTER_MS = 3_000L
        const val LOST_AFTER_MS = 8_000L
        const val REMIND_MS = 60_000L

        /** Depth must work this long before "measuring again" (no chatter when it flickers back for a frame). */
        const val BACK_AFTER_MS = 1_500L
        const val BACK_WINDOW_MS = 3_000L
    }
}

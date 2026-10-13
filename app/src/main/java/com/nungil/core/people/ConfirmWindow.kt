package com.nungil.core.people

/** Voice "Delete" twice within [windowMs] confirms; once only arms it. Nothing is deleted by one misheard word. */
class ConfirmWindow(private val windowMs: Long = WINDOW_MS) {
    private var armed = false
    private var armedAt = 0L

    /** false = armed (ask again); true = confirmed (and disarmed). */
    fun press(nowMs: Long, withinMs: Long = windowMs): Boolean {
        if (armed && nowMs - armedAt <= withinMs) {
            armed = false
            return true
        }
        armed = true
        armedAt = nowMs
        return false
    }

    companion object {
        const val WINDOW_MS = 5_000L

        /**
         * By voice the second "Delete" may come later: said 7-8 s apart, three of them only armed it (the logs,
         * 2026-10-13). A button's second press stays within [WINDOW_MS] (its label says so for that long).
         */
        const val VOICE_WINDOW_MS = 15_000L
    }
}

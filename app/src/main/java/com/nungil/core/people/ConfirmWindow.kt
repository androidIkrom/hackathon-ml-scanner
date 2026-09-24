package com.nungil.core.people

/** Voice "Delete" twice within [windowMs] confirms; once only arms it. Nothing is deleted by one misheard word. */
class ConfirmWindow(private val windowMs: Long = WINDOW_MS) {
    private var armed = false
    private var armedAt = 0L

    /** false = armed (ask again); true = confirmed (and disarmed). */
    fun press(nowMs: Long): Boolean {
        if (armed && nowMs - armedAt <= windowMs) {
            armed = false
            return true
        }
        armed = true
        armedAt = nowMs
        return false
    }

    companion object {
        const val WINDOW_MS = 5_000L
    }
}

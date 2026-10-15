package com.nungil.core.scan

/**
 * Where a camera frame's time goes: the frame made upright ([Window.convertMs]),
 * the detector ([Window.detectMs]), the screen's own work after it ([Window.afterMs]), and how many frames a second
 * came through, reported every [everyMs] (a slow phone gives a few frames a second). To find what to make faster on
 * the real phone (the user, 2026-10-15: "measure first").
 */
class FrameStats(private val everyMs: Long = EVERY_MS) {
    class Window(val fps: Float, val convertMs: Long, val detectMs: Long, val afterMs: Long) {
        val totalMs: Long get() = convertMs + detectMs + afterMs
    }

    private var count = 0
    private var firstAt = 0L
    private var convert = 0L
    private var detect = 0L
    private var after = 0L
    private var startedAt: Long? = null

    /** The camera was asked for at [nowMs]. */
    fun started(nowMs: Long) {
        startedAt = nowMs
    }

    /** Milliseconds from [started] to the first frame, once; null after that. */
    fun firstFrame(nowMs: Long): Long? = startedAt?.let { nowMs - it }.also { startedAt = null }

    /** One frame that started at [atMs]; the averages since the last report when [everyMs] has gone by, else null. */
    fun add(atMs: Long, convertMs: Long, detectMs: Long, afterMs: Long): Window? {
        if (count == 0) firstAt = atMs
        count++
        convert += convertMs
        detect += detectMs
        after += afterMs
        val span = atMs - firstAt
        if (span < everyMs) return null
        val window = Window(
            fps = if (span > 0) (count - 1) * 1000f / span else 0f,
            convertMs = convert / count, detectMs = detect / count, afterMs = after / count,
        )
        count = 0
        convert = 0
        detect = 0
        after = 0
        return window
    }

    companion object {
        const val EVERY_MS = 5_000L
    }
}

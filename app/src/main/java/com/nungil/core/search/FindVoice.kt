package com.nungil.core.search

import com.nungil.core.ui.Announcer
import com.nungil.core.ui.Notice

/**
 * When Find says where its target is. SearchTracker decides that a place is worth saying, once per change; the
 * announcer decides when (no queue, no talking over). A sentence held back stays [heard] until it is said or a newer
 * one replaces it; a pause drops it, so "start" does not bring back a place the target has left (review).
 * Not thread-safe: the screen calls it under one lock.
 */
class FindVoice(rules: Announcer.Rules = Announcer.FIND) {
    private val announcer = Announcer(rules)
    private var pending: Notice? = null
    private var heardAt = 0L

    /** The tracker's latest sentence, held until said. */
    fun heard(notice: Notice, nowMs: Long) {
        pending = notice
        heardAt = nowMs
    }

    /** The sentence to say now, or null. One held longer than [STALE_MS] is dropped: the target has moved on. */
    fun next(nowMs: Long): Notice? {
        if (pending != null && nowMs - heardAt > STALE_MS) pending = null
        return announcer.choose(nowMs, listOfNotNull(pending))?.also { pending = null }
    }

    fun pause() {
        pending = null
    }

    /** [text] was said by the screen itself ("Looking for…", "Paused."): nothing held is said over it. */
    fun said(nowMs: Long, text: String) {
        pending = null
        announcer.said(nowMs, text)
    }

    companion object {
        /** A place held back this long is old news: held while the user talked, one was said 5 s late (the logs). */
        const val STALE_MS = 3_000L
    }
}

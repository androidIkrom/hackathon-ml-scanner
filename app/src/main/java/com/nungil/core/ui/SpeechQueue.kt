package com.nungil.core.ui

/**
 * What to say next, and when. Pure logic behind TtsSpeaker, driven by a clock and polled every [POLL_MS].
 * Not thread-safe: TtsSpeaker only touches it on the main thread.
 *
 * - [add] queues a phrase; when more than [MAX_PENDING] wait, the stalest are dropped.
 * - [addLive] queues news of the moment (a live scan's things): news still waiting is joined into one sentence
 *   with it, the newest [MAX_LIVE_PARTS] kept, and it follows after [LIVE_GAP_MS]. One at a time with the long
 *   gap, things were named many seconds after they came into view, on a side the phone had turned from.
 * - [next] hands out the next phrase once the previous one ended at least [GAP_MS] ago.
 * - [now] drops the queue and speaks at once; [final] does the same and holds the queue until it ends.
 * - A phrase the engine never finishes is given up after [SHUTDOWN_SAFETY_MS].
 */
class SpeechQueue(private val clock: () -> Long) {
    private class Entry(val parts: MutableList<String>, val live: Boolean) {
        val text: String get() = parts.joinToString(", ")
    }

    private val pending = ArrayDeque<Entry>()
    private var speakingSince: Long? = null
    private var lastEndAt: Long? = null
    private var finalSince: Long? = null

    /** The last phrase handed out; what "repeat" says again. */
    var lastSpoken: String? = null
        private set

    val pendingCount: Int get() = pending.size
    val isSpeaking: Boolean get() = speakingSince != null

    /** Nothing is being said and nothing waits to be said. */
    val isQuiet: Boolean get() = speakingSince == null && pending.isEmpty()

    fun add(text: String) {
        val t = clean(text) ?: return
        push(Entry(mutableListOf(t), live = false))
    }

    fun addLive(text: String) {
        val t = clean(text) ?: return
        val last = pending.lastOrNull()
        if (last != null && last.live) {
            last.parts += t
            while (last.parts.size > MAX_LIVE_PARTS) last.parts.removeAt(0)
        } else {
            push(Entry(mutableListOf(t), live = true))
        }
    }

    private fun push(entry: Entry) {
        pending.addLast(entry)
        while (pending.size > MAX_PENDING) pending.removeFirst()
    }

    /** Clears the queue; returns the phrase to speak immediately (interrupting), or null if blank. */
    fun now(text: String): String? {
        val t = clean(text) ?: return null
        pending.clear()
        start(t)
        return t
    }

    /** Like [now], and nothing else is handed out until [finalFinished] has returned true. */
    fun final(text: String): String? {
        val t = now(text) ?: return null
        finalSince = clock()
        return t
    }

    fun next(): String? {
        val since = speakingSince
        if (since != null) {
            if (clock() - since < SHUTDOWN_SAFETY_MS) return null
            done()
        }
        if (finalSince != null) return null
        val head = pending.firstOrNull() ?: return null
        val end = lastEndAt
        if (end != null && clock() - end < if (head.live) LIVE_GAP_MS else GAP_MS) return null
        val t = pending.removeFirst().text
        start(t)
        return t
    }

    /** The engine finished (or failed) the current phrase. */
    fun done() {
        if (speakingSince == null) return
        speakingSince = null
        lastEndAt = clock()
    }

    /** True exactly once after the final phrase ended or ran past the safety limit. */
    fun finalFinished(): Boolean {
        val since = finalSince ?: return false
        if (speakingSince != null && clock() - since < SHUTDOWN_SAFETY_MS) return false
        finalSince = null
        if (speakingSince != null) {
            speakingSince = null
            lastEndAt = clock()
        }
        return true
    }

    fun clear() {
        pending.clear()
        speakingSince = null
        finalSince = null
    }

    private fun start(text: String) {
        speakingSince = clock()
        lastSpoken = text
    }

    private fun clean(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

    companion object {
        const val GAP_MS = 1_500L
        const val POLL_MS = 250L
        const val MAX_PENDING = 3
        const val LIVE_GAP_MS = 400L
        const val MAX_LIVE_PARTS = 3
        const val SHUTDOWN_SAFETY_MS = 15_000L
    }
}

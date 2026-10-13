package com.nungil.core.ui

/**
 * What to say next, and when. Pure logic behind TtsSpeaker, driven by a clock and polled every [POLL_MS].
 * Not thread-safe: TtsSpeaker only touches it on the main thread.
 *
 * - [add] queues a phrase; when more than [MAX_PENDING] wait, the stalest are dropped.
 * - [addLines] queues the lines of a text read aloud: none is dropped, and they follow each other after [LINE_GAP_MS].
 * - [next] hands out the next phrase once the previous one ended at least [GAP_MS] ago.
 * - [now] drops the queue and speaks at once; [final] does the same and holds the queue until it ends.
 * - [hold] (someone talks) pauses a text read aloud: the line cut is said again afterwards, announcements go.
 * - [cutLine]: the line of a text a stop cut, where "continue" goes on.
 * - A phrase the engine never finishes is given up after [SHUTDOWN_SAFETY_MS].
 */
class SpeechQueue(private val clock: () -> Long) {
    /** [line]: a line of a text read aloud (addLines), never dropped. */
    private class Entry(val text: String, val line: Boolean)

    private val pending = ArrayDeque<Entry>()
    private var current: Entry? = null
    private var lastWasLine = false
    private var speakingSince: Long? = null
    private var lastEndAt: Long? = null
    private var finalSince: Long? = null

    /** The last phrase handed out; what "repeat" says again. */
    var lastSpoken: String? = null
        private set

    /** The line of a text read aloud that a stop cut (or the next one, between lines); null after a new text. */
    var cutLine: String? = null
        private set

    val pendingCount: Int get() = pending.size

    /** A text is being read aloud: a line is being said or waits. */
    val reading: Boolean get() = (speakingSince != null && current?.line == true) || pending.any { it.line }
    val isSpeaking: Boolean get() = speakingSince != null

    /** Nothing is being said and nothing waits to be said. */
    val isQuiet: Boolean get() = speakingSince == null && pending.isEmpty()

    fun add(text: String) {
        val t = clean(text) ?: return
        pending.addLast(Entry(t, line = false))
        while (pending.count { !it.line } > MAX_PENDING) pending.remove(pending.first { !it.line })
    }

    /**
     * The lines of a text read aloud, in order. They are not dropped like announcements: the reader queued 31 lines
     * and 3 were said (the logs, 2026-10-12).
     */
    fun addLines(lines: List<String>) {
        cutLine = null
        for (line in lines) clean(line)?.let { pending.addLast(Entry(it, line = true)) }
    }

    /** Clears the queue; returns the phrase to speak immediately (interrupting), or null if blank. */
    fun now(text: String): String? {
        val t = clean(text) ?: return null
        drop()
        lastWasLine = false
        start(Entry(t, line = false))
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
        val entry = pending.firstOrNull() ?: return null
        val gap = if (lastWasLine && entry.line) LINE_GAP_MS else GAP_MS
        val end = lastEndAt
        if (end != null && clock() - end < gap) return null
        pending.removeFirst()
        lastWasLine = entry.line
        start(entry)
        return entry.text
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
        drop()
        speakingSince = null
        current = null
        finalSince = null
    }

    /**
     * Someone is talking: what is being said stops. A text read aloud waits, the line cut first, and goes on after
     * [LINE_GAP_MS]; announcements are dropped as stale. Other voices in a room did this, and the whole text was
     * dropped: 27 lines queued, none said (2026-10-13).
     */
    fun hold() {
        val cut = current?.takeIf { it.line && speakingSince != null }
        pending.removeAll { !it.line }
        cut?.let { pending.addFirst(it) }
        if (speakingSince != null) lastEndAt = clock()
        speakingSince = null
        current = null
        finalSince = null
    }

    /** Drops the queue; a text read aloud leaves the line it was at in [cutLine]. */
    private fun drop() {
        val at = current?.takeIf { it.line && speakingSince != null } ?: pending.firstOrNull { it.line }
        at?.let { cutLine = it.text }
        pending.clear()
    }

    private fun start(entry: Entry) {
        speakingSince = clock()
        current = entry
        lastSpoken = entry.text
    }

    private fun clean(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

    companion object {
        const val GAP_MS = 1_500L

        /** Between the lines of a text read aloud: a breath, not the pause between announcements. */
        const val LINE_GAP_MS = 150L
        const val POLL_MS = 250L
        const val MAX_PENDING = 3
        const val SHUTDOWN_SAFETY_MS = 15_000L
    }
}

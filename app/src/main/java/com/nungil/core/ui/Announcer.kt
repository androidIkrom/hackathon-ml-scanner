package com.nungil.core.ui

/**
 * One thing a screen could say about what it sees. [key] identifies the situation ("wall:ahead", "obj:12",
 * "find:LEFT"): it is not said again while it lasts, so only changes are spoken. [priority]: lower is more
 * important. [ahead]: about what is straight ahead, said before the sides and cutting into a sentence; [cutsIn]
 * cuts in too (Walk's "nothing close ahead"). Notices of one [topic] are said once per topic window unless
 * [urgent] or clearly closer ([level]: steps or metres as spoken, 0 very close, [FAR] unknown).
 */
data class Notice(
    val key: String,
    val text: String,
    val priority: Int,
    val ahead: Boolean = false,
    val urgent: Boolean = false,
    val topic: String? = null,
    val level: Int = FAR,
    val cutsIn: Boolean = false,
) {
    companion object {
        const val FAR = Int.MAX_VALUE
    }
}

/**
 * Chooses what a screen says about what it sees, at most one sentence per frame (spec 2026-10-05-shared-announcer):
 * Walk's rules, now for Live and Find too.
 *
 * - "New" is per key: a key said stays said while it keeps coming back, and only after it has been gone for
 *   [Rules.goneMs] may it be said again. (One key per kind made a corridor alternate "wall left", "wall right",
 *   "wall ahead" every second.)
 * - The same sentence is not said within [Rules.repeatMs]; a topic within [Rules.topicRepeatMs] only when urgent or
 *   clearly closer.
 * - Nothing is queued. The speaker played queued sentences one after another, and "Obstacle on your right" was heard
 *   6 s after it was true, Live's findings seconds late (the logs). What is straight ahead is said at once and cuts
 *   off the sentence being said; anything else waits until the last sentence has had time to finish, and is said
 *   then only if it is still a candidate. A candidate not said is not lost: it is chosen in a later frame.
 *
 * It never says "all clear" by itself: when nothing is found it stays quiet. Not thread-safe.
 */
class Announcer(private val rules: Rules) {
    data class Rules(val goneMs: Long, val repeatMs: Long, val topicRepeatMs: Long)

    /** Keys said, with the last time they were among the candidates. */
    private val said = HashMap<String, Long>()
    private val lastSaidAt = HashMap<String, Long>()
    private val topics = HashMap<String, Pair<Long, Int>>()
    private var busyUntil = Long.MIN_VALUE / 2

    fun choose(nowMs: Long, candidates: List<Notice>): Notice? {
        val keys = candidates.map { it.key }.toSet()
        // Not seen for goneMs: it went away, and may be said when it is back.
        said.entries.removeAll { (_, seen) -> nowMs - seen >= rules.goneMs }
        for (k in keys) if (k in said) said[k] = nowMs
        val pick = candidates
            .sortedWith(compareBy<Notice>({ !it.ahead }, { it.priority }))
            .firstOrNull { it.key !in said && mayCutIn(it, nowMs) && textAllowed(it, nowMs) && topicAllowed(it, nowMs) }
            ?: return null
        said[pick.key] = nowMs
        lastSaidAt[pick.text] = nowMs
        pick.topic?.let { topics[it] = nowMs to pick.level }
        busyUntil = nowMs + durationMs(pick.text)
        return pick
    }

    /** [text] was said outside the announcer (an answer to a question): waiting notices do not talk over it. */
    fun said(nowMs: Long, text: String) {
        busyUntil = nowMs + durationMs(text)
    }

    fun reset() {
        said.clear()
        lastSaidAt.clear()
        topics.clear()
        busyUntil = Long.MIN_VALUE / 2
    }

    private fun mayCutIn(n: Notice, now: Long) = now >= busyUntil || n.ahead || n.cutsIn

    private fun textAllowed(n: Notice, now: Long) = lastSaidAt[n.text]?.let { now - it >= rules.repeatMs } ?: true

    /** The same topic again only after a while, when urgent, or when clearly closer. */
    private fun topicAllowed(n: Notice, now: Long): Boolean {
        val topic = n.topic ?: return true
        if (n.urgent) return true
        val (at, level) = topics[topic] ?: return true
        if (now - at >= rules.topicRepeatMs) return true
        return n.level <= level - 2 || (n.level <= NEAR_LEVEL && n.level < level)
    }

    companion object {
        val WALK = Rules(goneMs = 2_000L, repeatMs = 6_000L, topicRepeatMs = 12_000L)

        /** A thing in view is said once; out of view for 10 s and back, it is said again (the user's choice). */
        val LIVE = Rules(goneMs = 10_000L, repeatMs = 6_000L, topicRepeatMs = 12_000L)

        /** Find's tracker already spaces its sentences (SearchTracker); this keeps them from talking over each other. */
        val FIND = Rules(goneMs = 2_000L, repeatMs = 2_000L, topicRepeatMs = 12_000L)

        /** Within this many steps any step closer is worth saying. */
        const val NEAR_LEVEL = 3

        private const val BASE_MS = 300L
        private const val LATIN_MS = 70L
        private const val HANGUL_MS = 140L

        /** About how long the speaker needs for [text]. */
        fun durationMs(text: String): Long =
            BASE_MS + text.length * (if (text.any { it in '가'..'힣' }) HANGUL_MS else LATIN_MS)
    }
}

package com.nungil.core.items

import com.nungil.contract.Box
import com.nungil.contract.Detection
import java.util.Locale

/**
 * How far a screen's search for saved items reaches. Look around, Live and Find search the whole frame, about a
 * second a frame. Walk's worker thread also gives the hazard alerts, which that second would delay: it takes the
 * detector's boxes and the squares near an item it has just seen.
 */
enum class Reach { WHOLE_FRAME, BOXES_AND_NEAR }

/** The thing in the middle of a square, cut out: its [outline], the [square] it is learned in, its best saved [match] alone. */
class CutThing(val outline: Box, val square: Box, val match: ItemMatcher.Match?)

/**
 * The one search for saved items, for every screen (spec 2026-10-05-shared-recognition). It sees the frame only
 * through [search]'s two functions: the saved item a square looks most like ([ItemMatcher.Match], best over all
 * saved items), and the thing in the middle of a square cut out ([CutThing], null without one).
 *
 * A target is a saved item and every other saved under its name (ItemMatcher.sameName): a square counts for it
 * only when its best match is one of them, so a square that looks more like another saved item counts for none.
 * A target found in one call is looked for near its square in the next and kept there at
 * [ItemMatcher.KEEP_THRESHOLD]; else it is found at [ItemMatcher.FIND_THRESHOLD] among squares all over the frame,
 * the best place looked at more closely, and the thing there alone may add a find (ItemMatcher.seen).
 *
 * Not thread-safe: one screen's worker thread calls it.
 */
class ItemSearch(
    private val names: Map<Long, String>,
    private val learnedAlone: Set<Long>,
    private val reach: Reach,
) {
    /** [id]: the target's smallest saved id; [box]: what is shown (the thing's outline, or the square). */
    data class Hit(val id: Long, val box: Box, val detectionIndex: Int?, val score: Float, val seen: ItemMatcher.Seen)

    /** [log]: the scores that decided, for a log line after "<looked> squares in <ms> ms, ". */
    class Result(val hits: List<Hit>, val looked: Int, val log: String)

    /** What one place comes to: is the item there, the square to look near next time, the box shown. */
    private class Verdict(
        val seen: ItemMatcher.Seen,
        val square: Box,
        val shown: Box,
        val outlined: Boolean,
        val alone: Float?,
        val score: Float,
    )

    /** The square each target was found in by the last call, and its score, by the target's smallest id. */
    private val last = HashMap<Long, Pair<Box, Float>>()

    /** Calls made for all saved items; the whole frame is searched on every other one. */
    private var allCalls = 0

    /** What decided about one target in this call, for the log. */
    private class Trace(val needs: Float) {
        val scored = mutableListOf<String>()
        var verdict: Verdict? = null
    }

    /**
     * The saved items in one frame: [only] and the items saved under its name, or every saved item when null.
     * In this order: the detector's boxes (not people), each as the square it would be learned in; near the square
     * of each item found by the last call; and, with [Reach.WHOLE_FRAME], squares all over the frame for the one
     * item not found yet that the frame looks most like. Searching the frame for every saved item would take a
     * second per item.
     */
    fun search(
        detections: List<Detection>,
        width: Int,
        height: Int,
        only: Long?,
        match: (Box) -> ItemMatcher.Match?,
        cut: (Box) -> CutThing?,
    ): Result {
        // The thing alone is cut out for Find only. Walk's worker thread also gives the hazard alerts (review), and
        // a live scan looks for every saved item: a search there took up to 4.2 s (the logs).
        val thingIn: (Box) -> CutThing? = if (reach == Reach.WHOLE_FRAME && only != null) cut else { _ -> null }
        // All saved items: the whole frame on every other call, the boxes and the near squares on each.
        val wholeFrameNow = only != null || allCalls++ % 2 == 0
        val targets = (if (only != null) listOf(only) else names.keys).map { ItemMatcher.sameName(it, names) }.distinct()
        val byKey = targets.associateBy { it.min() }
        val keyOf = HashMap<Long, Long>()
        for ((key, target) in byKey) for (id in target) keyOf[id] = key
        val hits = LinkedHashMap<Long, Hit>()
        val found = HashMap<Long, Pair<Box, Float>>()
        val traces = LinkedHashMap<Long, Trace>()
        var looked = 0

        fun record(key: Long, v: Verdict) {
            if (v.seen == ItemMatcher.Seen.NO) return
            hits[key] = Hit(key, v.shown, boxIn(v.square, detections), v.score, v.seen)
            found[key] = v.square to v.score
        }

        for (index in ItemCrop.candidates(detections, width, height)) {
            val square = ItemWindows.square(detections[index].box, width, height)
            looked++
            val m = match(square) ?: continue
            val key = keyOf[m.id] ?: continue
            traces.getOrPut(key) { Trace(ItemMatcher.FIND_THRESHOLD) }.scored += fmt("box %.2f", m.score)
            if (m.score < ItemMatcher.FIND_THRESHOLD || (hits[key]?.score ?: -1f) >= m.score) continue
            hits[key] = Hit(key, detections[index].box, index, m.score, ItemMatcher.Seen.BY_SQUARES)
            found[key] = square to m.score
        }

        // Seen a moment ago: look only where it was and next to it (nine squares, not forty). The whole frame is
        // searched again only when it is not there any more; every frame took a second otherwise (the logs).
        // Only the [MAX_NEAR] best of them: each is nine more squares, and a live scan kept six, false finds among
        // them, and searched for up to 4.2 s (the logs).
        for ((key, kept) in last.entries.sortedByDescending { it.value.second }.take(MAX_NEAR).map { it.key to it.value.first }) {
            val target = byKey[key] ?: continue
            if (key in hits) continue
            val trace = traces.getOrPut(key) { Trace(ItemMatcher.KEEP_THRESHOLD) }
            val near = ItemWindows.near(kept)
            val scores = FloatArray(near.size) { score(match(near[it]), target) }
            val best = scores.indices.maxBy { scores[it] }
            looked += near.size
            trace.scored += fmt("near %.2f", scores[best])
            if (scores[best] >= ItemMatcher.LOOK_CLOSER) {
                trace.verdict = judge(near[best], scores[best], ItemMatcher.KEEP_THRESHOLD, target, thingIn).also { record(key, it) }
            }
        }

        val open = byKey.filterKeys { it !in hits }
        if (reach == Reach.WHOLE_FRAME && wholeFrameNow && open.isNotEmpty()) {
            val windows = ItemWindows.grid(width, height)
            val matches = windows.map(match)
            looked += windows.size
            var key = -1L
            var scores = FloatArray(0)
            for ((k, target) in open) {
                val s = FloatArray(windows.size) { score(matches[it], target) }
                if (key < 0 || s.max() > scores.max()) {
                    key = k
                    scores = s
                }
            }
            val needs = if (key in last) ItemMatcher.KEEP_THRESHOLD else ItemMatcher.FIND_THRESHOLD
            val trace = traces.getOrPut(key) { Trace(needs) }
            val wide = wholeFrame(windows, scores, needs, open.getValue(key), match, thingIn)
            looked += wide.looked
            trace.scored += wide.log
            wide.verdict?.let {
                trace.verdict = it
                record(key, it)
            }
        }
        last.clear()
        last.putAll(found)
        val log = traces.entries.joinToString(" | ") { (key, t) ->
            (if (only == null) "\"${names[key]}\" " else "") + logLine(t.scored, t.needs, t.verdict, hits[key])
        }
        return Result(hits.values.toList(), looked, log)
    }

    /** A detector box that is the thing found in [place]: its middle in the place, not much bigger than it, not a person. */
    private fun boxIn(place: Box, detections: List<Detection>): Int? = detections.indices.firstOrNull { i ->
        val b = detections[i].box
        detections[i].label != PERSON && b.area <= place.area * MAX_BOX_SHARE &&
            b.centerX in place.left..place.right && b.centerY in place.top..place.bottom
    }

    private class Wide(val verdict: Verdict?, val looked: Int, val log: String)

    /**
     * The grid's [scores] for [target]: where the squares say it is, else their best guess when it is worth a look
     * at the thing there. The grid's best window may hold only a part of the item, or the item and much else, so
     * it is looked at more closely first.
     */
    private fun wholeFrame(
        windows: List<Box>,
        scores: FloatArray,
        needs: Float,
        target: Set<Long>,
        match: (Box) -> ItemMatcher.Match?,
        cut: (Box) -> CutThing?,
    ): Wide {
        val best = scores.indices.maxBy { scores[it] }
        var looked = 0
        var closer: Box? = null
        var closerScore = 0f
        if (scores[best] >= ItemMatcher.LOOK_CLOSER) {
            for (window in ItemWindows.around(windows[best])) {
                looked++
                val s = score(match(window), target)
                if (s > closerScore) {
                    closer = window
                    closerScore = s
                }
            }
        }
        val bySquares = closer?.takeIf { closerScore >= needs && closerScore > scores[best] }
            ?: ItemWindows.locate(windows, scores, needs)
        val top = maxOf(scores[best], closerScore)
        val place = bySquares
            ?: (closer?.takeIf { closerScore > scores[best] } ?: windows[best]).takeIf { top >= ItemMatcher.LOOK_CLOSER }
        val verdict = place?.let { judge(it, top, needs, target, cut) }
        return Wide(verdict, looked, fmt("grid %.2f, closer %.2f", scores[best], closerScore))
    }

    /**
     * [square] scored [squareScore]: is the item there, where is it looked for next time, and what is shown. When
     * the thing in its middle is the item by itself, that thing's outline is shown and its own square is followed.
     * Else the squares decide as they did, and the outline is shown only when it is the item's (ItemMatcher.outlined).
     */
    private fun judge(square: Box, squareScore: Float, needs: Float, target: Set<Long>, cut: (Box) -> CutThing?): Verdict {
        val thing = cut(square)
        val alone = thing?.let { score(it.match, target) }
        val seen = ItemMatcher.seen(squareScore, needs, alone)
        if (thing == null || alone == null) return Verdict(seen, square, square, false, null, squareScore)
        if (alone >= ItemMatcher.ALONE_MIN) return Verdict(seen, thing.square, thing.outline, true, alone, squareScore)
        val o = thing.outline
        val inside = o.area < square.area && o.centerX in square.left..square.right && o.centerY in square.top..square.bottom
        val outlined = ItemMatcher.outlined(alone, target.any { it in learnedAlone }, inside)
        return Verdict(seen, square, if (outlined) o else square, outlined, alone, squareScore)
    }

    /** How much a square looks like [target]: 0 when it looks more like another saved item. */
    private fun score(m: ItemMatcher.Match?, target: Set<Long>): Float = m?.takeIf { it.id in target }?.score ?: 0f

    private fun logLine(scored: List<String>, needs: Float, verdict: Verdict?, hit: Hit?): String {
        val alone = verdict?.alone?.let { fmt("%.2f", it) } ?: "-"
        val seen = when (hit?.seen) {
            ItemMatcher.Seen.BY_SQUARES -> "seen by squares"
            ItemMatcher.Seen.BY_ITEM_ALONE -> "seen by the item alone"
            else -> "not seen"
        } + if (hit != null && verdict?.outlined == true) ", outlined" else ""
        val box = hit?.box?.let { fmt(", box %.2f x %.2f at %.2f, %.2f", it.width, it.height, it.centerX, it.centerY) } ?: ""
        return scored.joinToString(", ") + fmt(" (needs %.2f), alone %s (needs %.2f), ", needs, alone, ItemMatcher.ALONE_MIN) + seen + box
    }

    private fun fmt(pattern: String, vararg args: Any): String = String.format(Locale.US, pattern, *args)

    private companion object {
        const val PERSON = "person"

        /** How many items found by the last call are looked for near where they were. */
        const val MAX_NEAR = 2

        /** A detector box up to this many times the found place's area may be the thing's own box. */
        const val MAX_BOX_SHARE = 1.5f
    }
}

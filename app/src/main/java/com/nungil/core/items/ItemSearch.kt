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

    /** The square each target was found in by the last call, by the target's smallest id. */
    private val last = HashMap<Long, Box>()

    fun search(
        detections: List<Detection>,
        width: Int,
        height: Int,
        only: Long?,
        match: (Box) -> ItemMatcher.Match?,
        cut: (Box) -> CutThing?,
    ): Result {
        if (only == null) TODO("All saved items: task 2")
        val target = ItemMatcher.sameName(only, names)
        val key = target.min()
        var looked = 0
        val scored = mutableListOf<String>()
        val kept = last[key]
        val needs = if (kept != null) ItemMatcher.KEEP_THRESHOLD else ItemMatcher.FIND_THRESHOLD
        var verdict: Verdict? = null

        // Seen a moment ago: look only where it was and next to it (nine squares, not forty). The whole frame is
        // searched again only when it is not there any more; every frame took a second otherwise (the logs).
        if (kept != null) {
            val near = ItemWindows.near(kept)
            val scores = FloatArray(near.size) { score(match(near[it]), target) }
            val best = scores.indices.maxBy { scores[it] }
            looked += near.size
            scored += fmt("near %.2f", scores[best])
            if (scores[best] >= ItemMatcher.LOOK_CLOSER) verdict = judge(near[best], scores[best], needs, target, cut)
        }
        if ((verdict == null || verdict.seen == ItemMatcher.Seen.NO) && reach == Reach.WHOLE_FRAME) {
            val windows = ItemWindows.grid(width, height)
            looked += windows.size
            val scores = FloatArray(windows.size) { score(match(windows[it]), target) }
            val wide = wholeFrame(windows, scores, needs, target, match, cut)
            looked += wide.looked
            scored += wide.log
            if (wide.verdict != null) verdict = wide.verdict
        }
        val hit = verdict?.takeIf { it.seen != ItemMatcher.Seen.NO }
        if (hit != null) last[key] = hit.square else last.remove(key)
        val hits = listOfNotNull(hit?.let { Hit(key, it.shown, null, it.score, it.seen) })
        return Result(hits, looked, logLine(scored, needs, verdict, hit))
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

    private fun logLine(scored: List<String>, needs: Float, verdict: Verdict?, hit: Verdict?): String {
        val alone = verdict?.alone?.let { fmt("%.2f", it) } ?: "-"
        val seen = when (hit?.seen) {
            ItemMatcher.Seen.BY_SQUARES -> "seen by squares"
            ItemMatcher.Seen.BY_ITEM_ALONE -> "seen by the item alone"
            else -> "not seen"
        } + if (hit?.outlined == true) ", outlined" else ""
        val box = hit?.shown?.let { fmt(", box %.2f x %.2f at %.2f, %.2f", it.width, it.height, it.centerX, it.centerY) } ?: ""
        return scored.joinToString(", ") + fmt(" (needs %.2f), alone %s (needs %.2f), ", needs, alone, ItemMatcher.ALONE_MIN) + seen + box
    }

    private fun fmt(pattern: String, vararg args: Any): String = String.format(Locale.US, pattern, *args)
}

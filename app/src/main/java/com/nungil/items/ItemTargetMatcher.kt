package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.nungil.contract.Box
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.ItemEnrollmentGuide
import com.nungil.core.items.ItemMask
import com.nungil.core.items.ItemMatcher
import com.nungil.core.items.ItemWindows
import com.nungil.search.TargetMatcher
import java.util.Locale

/**
 * Search camera target "a saved item": squares all over the frame (ItemWindows) are compared with the saved
 * samples, and the item is where the squares that look like it are. A square counts only when the item (or
 * another saved under its name) is its best match among all saved items, and the best square gets a closer look (smaller, bigger, a little to each
 * side). The detector is not used: it gave no box for things it does not know.
 *
 * A square holds the item and whatever it stands on, so on another background the squares only half know it:
 * 86 of 115 such frames were found (measured). There the thing in the middle of the best square is cut out
 * (ItemSegmenter) and compared alone with the samples of the item alone, which found 108 of them and nothing
 * in a view without the item. That check adds finds and never takes one away (ItemMatcher.seen). The box shown
 * is the outline of the thing, so the user sees the item, not its surroundings.
 */
class ItemTargetMatcher(context: Context, private val itemId: Long) : TargetMatcher {
    private val embedder = ItemEmbedder(context)
    private val recognizer = ItemRecognizer(context).also { it.reload() }

    /** The item looked for, and any other saved under its name: one thing to the search. */
    private val targets: Set<Long> = recognizer.sameName(itemId)

    /** The item has samples of itself alone (two per sample taken), not squares only. */
    private val learnedAlone: Boolean = targets.any { ItemEnrollmentGuide.learnedAlone(recognizer.samples(it)) }

    private val segmenter: ItemSegmenter? = try {
        ItemSegmenter(context)
    } catch (e: Exception) {
        Log.i(TAG, "Item search without outlines", e)
        null
    }
    private var lastLogMs = 0L

    /** The square the item was found in last frame, if any: it is kept at [ItemMatcher.KEEP_THRESHOLD]. */
    private var lastSquare: Box? = null

    /** The thing in the middle of a square: its outline, the square it would be learned in, how much it alone looks like the item. */
    private class Thing(val box: Box, val square: Box, val score: Float)

    /**
     * What one place comes to: whether the item is there, the [square] to look around in the next frame, the
     * box [shown] ([outlined] when it is the thing's outline) and the thing's score [alone] (null: no thing).
     */
    private class Verdict(val seen: ItemMatcher.Seen, val square: Box, val shown: Box, val outlined: Boolean, val alone: Float?)

    override val slow: Boolean = true

    override fun locate(frame: VisionFrame): Box? {
        val bitmap = frame.bitmap ?: return null
        val started = SystemClock.elapsedRealtime()
        val kept = lastSquare
        val needs = if (kept != null) ItemMatcher.KEEP_THRESHOLD else ItemMatcher.FIND_THRESHOLD
        var looked = 0
        val scored = mutableListOf<String>()
        var verdict: Verdict? = null

        // Seen a moment ago: look only where it was and next to it (nine squares, not forty). The whole frame is
        // searched again only when it is not there any more; every frame took a second otherwise (the logs).
        if (kept != null) {
            val near = ItemWindows.near(kept)
            val nearScores = FloatArray(near.size) { score(bitmap, near[it]) }
            val best = nearScores.indices.maxBy { nearScores[it] }
            looked += near.size
            scored += String.format(Locale.US, "near %.2f", nearScores[best])
            if (nearScores[best] >= ItemMatcher.LOOK_CLOSER) verdict = judge(bitmap, near[best], nearScores[best], needs)
        }
        if (verdict == null || verdict.seen == ItemMatcher.Seen.NO) {
            val windows = ItemWindows.grid(bitmap.width, bitmap.height)
            val scores = FloatArray(windows.size) { score(bitmap, windows[it]) }
            val best = scores.indices.maxBy { scores[it] }
            looked += windows.size

            // The grid's best window may hold only a part of the item, or the item and much else: look closer there.
            var closer: Box? = null
            var closerScore = 0f
            if (scores[best] >= ItemMatcher.LOOK_CLOSER) {
                for (window in ItemWindows.around(windows[best])) {
                    looked++
                    val s = score(bitmap, window)
                    if (s > closerScore) {
                        closer = window
                        closerScore = s
                    }
                }
            }
            scored += String.format(Locale.US, "grid %.2f, closer %.2f", scores[best], closerScore)
            val bySquares = closer?.takeIf { closerScore >= needs && closerScore > scores[best] }
                ?: ItemWindows.locate(windows, scores, needs)
            val top = maxOf(scores[best], closerScore)
            // Where the squares say it is; else their best guess, when it is worth a look at the thing there.
            val place = bySquares
                ?: (closer?.takeIf { closerScore > scores[best] } ?: windows[best]).takeIf { top >= ItemMatcher.LOOK_CLOSER }
            if (place != null) verdict = judge(bitmap, place, top, needs)
        }
        val hit = verdict?.takeIf { it.seen != ItemMatcher.Seen.NO }
        lastSquare = hit?.square

        val now = SystemClock.elapsedRealtime()
        if (now - lastLogMs >= LOG_EVERY_MS) {
            lastLogMs = now
            val alone = verdict?.alone?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
            val seen = when (hit?.seen) {
                ItemMatcher.Seen.BY_SQUARES -> "seen by squares"
                ItemMatcher.Seen.BY_ITEM_ALONE -> "seen by the item alone"
                else -> "not seen"
            } + if (hit?.outlined == true) ", outlined" else ""
            val limits = String.format(Locale.US, "(needs %.2f), alone %s (needs %.2f)", needs, alone, ItemMatcher.ALONE_MIN)
            val box = hit?.shown?.let { String.format(Locale.US, ", box %.2f x %.2f at %.2f, %.2f", it.width, it.height, it.centerX, it.centerY) } ?: ""
            Log.i(TAG, "Item search: $looked squares in ${now - started} ms, ${scored.joinToString(", ")} $limits, $seen$box")
        }
        return hit?.shown
    }

    /**
     * [square] scored [squareScore] against the samples: is the item there, where is it looked for in the next
     * frame, and what is shown. When the thing in its middle is the item by itself, that thing's outline is
     * shown and its own square (the one it would be learned in) is tracked. Else the squares decide as they did,
     * and the outline is shown only when it is the item's (ItemMatcher.outlined), the square otherwise.
     */
    private fun judge(bitmap: Bitmap, square: Box, squareScore: Float, needs: Float): Verdict {
        val thing = thingIn(bitmap, square)
        val seen = ItemMatcher.seen(squareScore, needs, thing?.score)
        if (thing == null) return Verdict(seen, square, square, false, null)
        if (thing.score >= ItemMatcher.ALONE_MIN) return Verdict(seen, thing.square, thing.box, true, thing.score)
        val inside = thing.box.area < square.area &&
            thing.box.centerX in square.left..square.right && thing.box.centerY in square.top..square.bottom
        val outlined = ItemMatcher.outlined(thing.score, learnedAlone, inside)
        return Verdict(seen, square, if (outlined) thing.box else square, outlined, thing.score)
    }

    /**
     * The thing in the middle of [square], or null without a segmenter or when what is there is the table, the
     * wall or a speck.
     */
    private fun thingIn(bitmap: Bitmap, square: Box): Thing? {
        val answer = segmenter?.at(bitmap, square.centerX, square.centerY) ?: return null
        val mask = ItemMask.of(answer.values, answer.width, answer.height, square.centerX, square.centerY) ?: return null
        val own = ItemWindows.square(mask.box, bitmap.width, bitmap.height)
        return Thing(mask.box, own, embedder.embedAlone(bitmap, own, mask)?.let { score(it) } ?: 0f)
    }

    /** How much the part of [bitmap] in [window] looks like the item; 0 when it looks more like another saved item. */
    private fun score(bitmap: Bitmap, window: Box): Float = embedder.embed(bitmap, window)?.let { score(it) } ?: 0f

    private fun score(vector: FloatArray): Float =
        recognizer.identify(vector, 0f)?.takeIf { it.id in targets }?.score ?: 0f

    override fun close() {
        embedder.close()
        segmenter?.close()
    }

    private companion object {
        const val TAG = "Nungil"
        const val LOG_EVERY_MS = 1_000L
    }
}

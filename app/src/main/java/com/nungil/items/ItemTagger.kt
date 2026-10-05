package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.nungil.contract.Box
import com.nungil.contract.app.NameTag
import com.nungil.contract.app.NameTagger
import com.nungil.contract.app.TagKind
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.ItemCrop
import com.nungil.core.items.ItemMask
import com.nungil.core.items.ItemMatcher
import com.nungil.core.items.ItemWindows
import java.util.Locale

/**
 * Names saved items inside A's scan: embeds at most 3 non-person boxes per frame (largest first) and renames the
 * box whose look matches a saved item. Each saved item names at most one box per frame.
 *
 * A box is embedded as the square it would be learned in (ItemWindows.square), not as the detector's own box:
 * the samples are such squares, and a tight box of another shape scored below them. The match takes
 * [ItemMatcher.TAG_THRESHOLD]; at 0.75 a live scan named no saved item at all, four bottles and a remote among
 * the things it saw (the logs).
 *
 * [byLook]: the detector draws no box for things it does not know (a towel, a charger, a box), so a live scan
 * never named them while Find found them. The frame is then also searched the way Find searches it (squares all
 * over it, a closer look at the best, the thing there alone: ItemTargetMatcher), for the one saved item that
 * looks most like a part of it. A detector box in the middle of that place is the item's box; else the place is.
 */
class ItemTagger(context: Context, private val byLook: Boolean = false) : NameTagger {
    private val embedder = ItemEmbedder(context)
    private val recognizer = ItemRecognizer(context).also { it.reload() }
    private val segmenter: ItemSegmenter? = if (!byLook) null else try {
        ItemSegmenter(context)
    } catch (e: Exception) {
        Log.i(TAG, "Item look without outlines: ${e.message}")
        null
    }
    private var lastBoxLogMs = 0L

    override fun tag(frame: VisionFrame): List<NameTag> {
        val bitmap = frame.bitmap ?: return emptyList()
        if (recognizer.isEmpty) return emptyList()
        val best = HashMap<Long, Pair<Int, Float>>()
        var top: ItemMatcher.Match? = null
        for (index in ItemCrop.candidates(frame.detections, bitmap.width, bitmap.height)) {
            val square = ItemWindows.square(frame.detections[index].box, bitmap.width, bitmap.height)
            val vector = embedder.embed(bitmap, square) ?: continue
            val match = recognizer.identify(vector, 0f) ?: continue
            if (top == null || match.score > top.score) top = match
            if (match.score < ItemMatcher.TAG_THRESHOLD) continue
            val previous = best[match.id]
            if (previous == null || match.score > previous.second) best[match.id] = index to match.score
        }
        logBoxes(top)
        val tags = best.mapNotNull { (id, hit) -> recognizer.nameOf(id)?.let { NameTag(hit.first, it, TagKind.ITEM) } }
        if (!byLook) return tags
        val named = tags.map { it.name }.toSet()
        val look = byLook(frame, bitmap)?.takeIf { it.name !in named }
        return if (look != null) tags + look else tags
    }

    /** The saved item that looks most like a part of the frame, if it is there by Find's measure. */
    private fun byLook(frame: VisionFrame, bitmap: Bitmap): NameTag? {
        val started = SystemClock.elapsedRealtime()
        val windows = ItemWindows.grid(bitmap.width, bitmap.height)
        var looked = windows.size
        var id = -1L
        var gridScore = 0f
        var place: Box? = null
        for (window in windows) {
            val match = match(bitmap, window) ?: continue
            if (match.score > gridScore) {
                id = match.id
                gridScore = match.score
                place = window
            }
        }
        if (place == null || gridScore < ItemMatcher.LOOK_CLOSER) return null
        var score = gridScore
        val same = recognizer.sameName(id)
        for (window in ItemWindows.around(place)) {
            looked++
            val match = match(bitmap, window) ?: continue
            if (match.id in same && match.score > score) {
                score = match.score
                place = window
            }
        }
        val square = place!!
        val alone = if (score < ItemMatcher.FIND_THRESHOLD) aloneScore(bitmap, square, id) else null
        val seen = ItemMatcher.seen(score, ItemMatcher.FIND_THRESHOLD, alone)
        val name = recognizer.nameOf(id)
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "Item look: %d squares in %d ms, best \"%s\" grid %.2f, closer %.2f (needs %.2f), alone %s (needs %.2f), %s",
                looked, SystemClock.elapsedRealtime() - started, name, gridScore, score, ItemMatcher.FIND_THRESHOLD,
                alone?.let { String.format(Locale.US, "%.2f", it) } ?: "-", ItemMatcher.ALONE_MIN,
                if (seen == ItemMatcher.Seen.NO) "not seen" else "seen",
            ),
        )
        if (seen == ItemMatcher.Seen.NO || name == null) return null
        // A detector box whose middle is in the square and that is not much bigger than it is the item.
        val index = frame.detections.indices.firstOrNull { i ->
            val b = frame.detections[i].box
            frame.detections[i].label != PERSON && b.area <= square.area * MAX_BOX_SHARE &&
                b.centerX in square.left..square.right && b.centerY in square.top..square.bottom
        }
        return if (index != null) NameTag(index, name, TagKind.ITEM) else NameTag(-1, name, TagKind.ITEM, square)
    }

    private fun match(bitmap: Bitmap, window: Box): ItemMatcher.Match? =
        embedder.embed(bitmap, window)?.let { recognizer.identify(it, 0f) }

    /** How much the thing in the middle of [square], cut out, looks like item [id]; null without a thing. */
    private fun aloneScore(bitmap: Bitmap, square: Box, id: Long): Float? {
        val answer = segmenter?.at(bitmap, square.centerX, square.centerY) ?: return null
        val mask = ItemMask.of(answer.values, answer.width, answer.height, square.centerX, square.centerY) ?: return null
        val own = ItemWindows.square(mask.box, bitmap.width, bitmap.height)
        val match = embedder.embedAlone(bitmap, own, mask)?.let { recognizer.identify(it, 0f) } ?: return 0f
        return if (match.id in recognizer.sameName(id)) match.score else 0f
    }

    /** Once a second: the saved item the boxes looked most like, so one device run shows how close it came. */
    private fun logBoxes(top: ItemMatcher.Match?) {
        val now = SystemClock.elapsedRealtime()
        if (top == null || now - lastBoxLogMs < LOG_EVERY_MS) return
        lastBoxLogMs = now
        val name = recognizer.nameOf(top.id)
        Log.i(TAG, String.format(Locale.US, "Item tag: best \"%s\" %.2f (needs %.2f)", name, top.score, ItemMatcher.TAG_THRESHOLD))
    }

    override fun close() {
        embedder.close()
        segmenter?.close()
    }

    private companion object {
        const val TAG = "Nungil"
        const val PERSON = "person"
        const val LOG_EVERY_MS = 1_000L

        /** A detector box up to this many times the square's area may be the item's own box. */
        const val MAX_BOX_SHARE = 1.5f
    }
}

package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.nungil.contract.Box
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.ItemMask
import com.nungil.core.items.ItemMatcher
import com.nungil.core.items.ItemWindows
import com.nungil.search.TargetMatcher
import java.util.Locale

/**
 * Search camera target "a saved item": squares all over the frame (ItemWindows) are compared with the saved
 * samples, and the item is where the squares that look like it are. A square counts only when [itemId] is its
 * best match among all saved items, and the best square gets a closer look (smaller, bigger, a little to each
 * side). The detector is not used: it gave no box for things it does not know. The square that found the item
 * holds some of what is around it; the box shown is the outline of the thing in its middle (ItemSegmenter) when
 * there is one inside the square, so the user sees the item, not its surroundings.
 */
class ItemTargetMatcher(context: Context, private val itemId: Long) : TargetMatcher {
    private val embedder = ItemEmbedder(context)
    private val recognizer = ItemRecognizer(context).also { it.reload() }
    private val segmenter: ItemSegmenter? = try {
        ItemSegmenter(context)
    } catch (e: Exception) {
        Log.i(TAG, "Item search without outlines", e)
        null
    }
    private var lastLogMs = 0L

    /** The square the item was found in last frame, if any: it is kept at [ItemMatcher.KEEP_THRESHOLD]. */
    private var lastSquare: Box? = null

    override val slow: Boolean = true

    override fun locate(frame: VisionFrame): Box? {
        val bitmap = frame.bitmap ?: return null
        val started = SystemClock.elapsedRealtime()
        val kept = lastSquare
        val needs = if (kept != null) ItemMatcher.KEEP_THRESHOLD else ItemMatcher.FIND_THRESHOLD

        // Seen a moment ago: look only around where it was (ten squares, not forty). The whole frame is
        // searched again only when it is not there any more; every frame took a second otherwise (the logs).
        var looked = 0
        var found: Box? = null
        var text = ""
        if (kept != null) {
            val near = ItemWindows.around(kept)
            val nearScores = FloatArray(near.size) { score(bitmap, near[it]) }
            val best = nearScores.indices.maxBy { nearScores[it] }
            looked += near.size
            if (nearScores[best] >= needs) found = near[best]
            text = String.format(Locale.US, "near %.2f", nearScores[best])
        }
        if (found == null) {
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
            found = closer?.takeIf { closerScore >= needs && closerScore > scores[best] }
                ?: ItemWindows.locate(windows, scores, needs)
            text = listOf(text, String.format(Locale.US, "grid %.2f, closer %.2f", scores[best], closerScore)).filter { it.isNotEmpty() }.joinToString(", ")
        }
        lastSquare = found
        val outline = found?.let { outline(bitmap, it) }

        val now = SystemClock.elapsedRealtime()
        if (now - lastLogMs >= LOG_EVERY_MS) {
            lastLogMs = now
            val shown = if (found == null) "not seen" else if (outline != null) "seen, outlined" else "seen"
            Log.i(TAG, "Item search: $looked squares in ${now - started} ms, $text (needs ${String.format(Locale.US, "%.2f", needs)}), $shown")
        }
        return outline ?: found
    }

    /**
     * The outline of the thing in the middle of [square], when it is a thing inside the square: its box is
     * smaller than the square and its middle is in the square. Null without a segmenter or when the thing there
     * is the table or the wall.
     */
    private fun outline(bitmap: Bitmap, square: Box): Box? {
        val answer = segmenter?.at(bitmap, square.centerX, square.centerY) ?: return null
        val mask = ItemMask.of(answer.values, answer.width, answer.height, square.centerX, square.centerY) ?: return null
        val box = mask.box
        val inside = box.centerX in square.left..square.right && box.centerY in square.top..square.bottom
        return box.takeIf { inside && it.area < square.area }
    }

    /** How much the part of [bitmap] in [window] looks like the item; 0 when it looks more like another saved item. */
    private fun score(bitmap: Bitmap, window: Box): Float {
        val vector = embedder.embed(bitmap, window) ?: return 0f
        return recognizer.identify(vector, 0f)?.takeIf { it.id == itemId }?.score ?: 0f
    }

    override fun close() {
        embedder.close()
        segmenter?.close()
    }

    private companion object {
        const val TAG = "Nungil"
        const val LOG_EVERY_MS = 1_000L
    }
}

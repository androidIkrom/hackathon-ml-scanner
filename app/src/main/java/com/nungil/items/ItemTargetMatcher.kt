package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.nungil.contract.Box
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.ItemMatcher
import com.nungil.core.items.ItemWindows
import com.nungil.search.TargetMatcher
import java.util.Locale

/**
 * Search camera target "a saved item": squares all over the frame (ItemWindows) are compared with the saved
 * samples, and the item is where the squares that look like it are. A square counts only when [itemId] is its
 * best match among all saved items, and the best square gets a closer look (smaller, bigger, a little to each
 * side). The detector is not used: it gave no box for things it does not know.
 */
class ItemTargetMatcher(context: Context, private val itemId: Long) : TargetMatcher {
    private val embedder = ItemEmbedder(context)
    private val recognizer = ItemRecognizer(context).also { it.reload() }
    private var lastLogMs = 0L

    /** The item was in the last frame: it is kept at [ItemMatcher.KEEP_THRESHOLD]. */
    private var seen = false

    override val slow: Boolean = true

    override fun locate(frame: VisionFrame): Box? {
        val bitmap = frame.bitmap ?: return null
        val started = SystemClock.elapsedRealtime()
        val windows = ItemWindows.grid(bitmap.width, bitmap.height)
        val scores = FloatArray(windows.size) { score(bitmap, windows[it]) }
        val best = scores.indices.maxByOrNull { scores[it] } ?: return null
        val needs = if (seen) ItemMatcher.KEEP_THRESHOLD else ItemMatcher.FIND_THRESHOLD

        // The grid's best window may hold only a part of the item, or the item and much else: look closer there.
        var closer: Box? = null
        var closerScore = 0f
        var looked = 0
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
        val found = closer?.takeIf { closerScore >= needs && closerScore > scores[best] }
            ?: ItemWindows.locate(windows, scores, needs)
        seen = found != null

        val now = SystemClock.elapsedRealtime()
        if (now - lastLogMs >= LOG_EVERY_MS) {
            lastLogMs = now
            val text = String.format(Locale.US, "grid %.2f, closer %.2f (needs %.2f)", scores[best], closerScore, needs)
            Log.i(TAG, "Item search: ${windows.size + looked} squares in ${now - started} ms, $text, ${if (seen) "seen" else "not seen"}")
        }
        return found
    }

    /** How much the part of [bitmap] in [window] looks like the item; 0 when it looks more like another saved item. */
    private fun score(bitmap: Bitmap, window: Box): Float {
        val vector = embedder.embed(bitmap, window) ?: return 0f
        return recognizer.identify(vector, 0f)?.takeIf { it.id == itemId }?.score ?: 0f
    }

    override fun close() = embedder.close()

    private companion object {
        const val TAG = "Nungil"
        const val LOG_EVERY_MS = 1_000L
    }
}

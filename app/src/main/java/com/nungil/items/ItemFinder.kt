package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.nungil.contract.Box
import com.nungil.contract.app.Found
import com.nungil.contract.app.SavedFinder
import com.nungil.contract.app.TagKind
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.CutThing
import com.nungil.core.items.ItemMask
import com.nungil.core.items.ItemSearch
import com.nungil.core.items.ItemWindows
import com.nungil.core.items.Reach

/**
 * Saved items for every camera screen: ItemSearch over the MobileNet embedder, the saved samples and the
 * segmenter. [reach] is the screen's: the whole frame for Look around, Live and Find, the detector's boxes and the
 * squares near a found item for Walk. Loads the samples on creation; worker thread only.
 */
class ItemFinder(private val context: Context, reach: Reach) : SavedFinder {
    private val embedder = ItemEmbedder(context)
    private val recognizer = ItemRecognizer(context).also { it.reload() }
    private val search = ItemSearch(recognizer.allNames, recognizer.learnedAlone(), reach)

    /** Made at the first look at a thing; null when it cannot load (the squares then decide alone). */
    private var segmenter: ItemSegmenter? = null
    private var segmenterTried = false
    private var lastLogMs = 0L

    override fun find(frame: VisionFrame, only: Long?): List<Found> {
        val bitmap = frame.bitmap ?: return emptyList()
        if (recognizer.isEmpty) return emptyList()
        val started = SystemClock.elapsedRealtime()
        val result = search.search(
            frame.detections, bitmap.width, bitmap.height, only,
            match = { box -> embedder.embed(bitmap, box)?.let { recognizer.identify(it, 0f) } },
            cut = { square -> cut(bitmap, square) },
        )
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogMs >= LOG_EVERY_MS) {
            lastLogMs = now
            Log.i(TAG, "Item search: ${result.looked} squares in ${now - started} ms, ${result.log}")
        }
        return result.hits.mapNotNull { hit ->
            recognizer.nameOf(hit.id)?.let { Found(TagKind.ITEM, hit.id, it, hit.box, hit.detectionIndex, hit.score) }
        }
    }

    /** The thing in the middle of [square], cut out, or null without a segmenter or when it is the table, the wall or a speck. */
    private fun cut(bitmap: Bitmap, square: Box): CutThing? {
        val answer = segmenter()?.at(bitmap, square.centerX, square.centerY) ?: return null
        val mask = ItemMask.of(answer.values, answer.width, answer.height, square.centerX, square.centerY) ?: return null
        val own = ItemWindows.square(mask.box, bitmap.width, bitmap.height)
        return CutThing(mask.box, own, embedder.embedAlone(bitmap, own, mask)?.let { recognizer.identify(it, 0f) })
    }

    private fun segmenter(): ItemSegmenter? {
        if (!segmenterTried) {
            segmenterTried = true
            segmenter = try {
                ItemSegmenter(context)
            } catch (e: Exception) {
                Log.i(TAG, "Item search without outlines: ${e.message}")
                null
            }
        }
        return segmenter
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

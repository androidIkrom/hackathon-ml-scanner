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
import com.nungil.core.scan.ModelShelf
import com.nungil.scan.SharedModels

/**
 * Saved items for every camera screen: ItemSearch over the MobileNet embedder, the saved samples and the
 * segmenter. [reach] is the screen's: the whole frame for Look around, Live and Find, the detector's boxes and the
 * squares near a found item for Walk. Loads the samples on creation; worker thread only.
 */
class ItemFinder(private val context: Context, reach: Reach) : SavedFinder {
    private val embedderLease = SharedModels.itemEmbedder(context)
    private val embedder get() = embedderLease.model
    private val recognizer = ItemRecognizer(context).also { it.reload() }
    private val search = ItemSearch(recognizer.allNames, recognizer.learnedAlone(), reach)

    /** Made at the first look at a thing; null when it cannot load (the squares then decide alone). */
    private var segmenter: ModelShelf.Lease<ItemSegmenter>? = null
    private var segmenterTried = false
    private var lastLogMs = 0L
    /** Where a search's time goes, for the log: squares embedded, things cut out (segmenter and their own embedding). */
    private var embeds = 0
    private var embedMs = 0L
    private var cuts = 0
    private var cutMs = 0L

    override fun find(frame: VisionFrame, only: Long?): List<Found> {
        val bitmap = frame.bitmap ?: return emptyList()
        if (recognizer.isEmpty) return emptyList()
        val started = SystemClock.elapsedRealtime()
        embeds = 0; embedMs = 0; cuts = 0; cutMs = 0
        val result = search.search(
            frame.detections, bitmap.width, bitmap.height, only,
            match = { box -> timed({ embeds++; embedMs += it }) { embedder.embed(bitmap, box) }?.let { recognizer.identify(it, 0f) } },
            cut = { square -> timed({ cuts++; cutMs += it }) { cut(bitmap, square) } },
        )
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogMs >= LOG_EVERY_MS) {
            lastLogMs = now
            Log.i(
                TAG,
                "Item search: ${result.looked} squares in ${now - started} ms (embed $embeds x ${embedMs / maxOf(1, embeds)} ms, " +
                    "cut $cuts x ${cutMs / maxOf(1, cuts)} ms), ${result.log}",
            )
        }
        return result.hits.mapNotNull { hit ->
            recognizer.nameOf(hit.id)?.let { Found(TagKind.ITEM, hit.id, it, hit.box, hit.detectionIndex, hit.score, hit.outlined) }
        }
    }

    /** The thing in the middle of [square], cut out, or null without a segmenter or when it is the table, the wall or a speck. */
    private fun cut(bitmap: Bitmap, square: Box): CutThing? {
        val answer = segmenter()?.at(bitmap, square.centerX, square.centerY) ?: return null
        val mask = ItemMask.of(answer.values, answer.width, answer.height, square.centerX, square.centerY) ?: return null
        val own = ItemWindows.square(mask.box, bitmap.width, bitmap.height)
        return CutThing(mask.box, own, embedder.embedAlone(bitmap, own, mask)?.let { recognizer.identify(it, 0f) })
    }

    private inline fun <T> timed(add: (Long) -> Unit, work: () -> T): T {
        val t = SystemClock.elapsedRealtime()
        return work().also { add(SystemClock.elapsedRealtime() - t) }
    }

    private fun segmenter(): ItemSegmenter? {
        if (!segmenterTried) {
            segmenterTried = true
            segmenter = try {
                SharedModels.itemSegmenter(context)
            } catch (e: Exception) {
                Log.i(TAG, "Item search without outlines: ${e.message}")
                null
            }
        }
        return segmenter?.model
    }

    override fun close() {
        embedderLease.close()
        segmenter?.close()
    }

    private companion object {
        const val TAG = "Nungil"
        const val LOG_EVERY_MS = 1_000L
    }
}

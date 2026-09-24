package com.nungil.items

import android.content.Context
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.ItemCrop
import com.nungil.search.TargetMatcher

/**
 * Search camera target "a saved item": any non-person box (items are matched by how they look, not by label),
 * boxes with the item's label tried first, at most 3 per frame. The box counts only when [itemId] is the best
 * match among all saved items.
 */
class ItemTargetMatcher(context: Context, private val itemId: Long, private val label: String) : TargetMatcher {
    private val embedder = ItemEmbedder(context)
    private val recognizer = ItemRecognizer(context).also { it.reload() }

    override val slow: Boolean = true

    override fun find(frame: VisionFrame): Int {
        val bitmap = frame.bitmap ?: return -1
        var best = -1
        var bestScore = 0f
        for (index in ItemCrop.candidates(frame.detections, bitmap.width, bitmap.height, preferLabel = label)) {
            val vector = embedder.embed(bitmap, frame.detections[index].box) ?: continue
            val match = recognizer.identify(vector) ?: continue
            if (match.id == itemId && match.score > bestScore) {
                best = index
                bestScore = match.score
            }
        }
        return best
    }

    override fun close() = embedder.close()
}

package com.nungil.items

import android.content.Context
import com.nungil.contract.app.NameTag
import com.nungil.contract.app.NameTagger
import com.nungil.contract.app.TagKind
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.ItemCrop

/**
 * Names saved items inside A's scan: embeds at most 3 non-person boxes per frame (largest first) and renames the
 * box whose look matches a saved item. Each saved item names at most one box per frame.
 */
class ItemTagger(context: Context) : NameTagger {
    private val embedder = ItemEmbedder(context)
    private val recognizer = ItemRecognizer(context).also { it.reload() }

    override fun tag(frame: VisionFrame): List<NameTag> {
        val bitmap = frame.bitmap ?: return emptyList()
        if (recognizer.isEmpty) return emptyList()
        val best = HashMap<Long, Pair<Int, Float>>()
        for (index in ItemCrop.candidates(frame.detections, bitmap.width, bitmap.height)) {
            val vector = embedder.embed(bitmap, frame.detections[index].box) ?: continue
            val match = recognizer.identify(vector) ?: continue
            val previous = best[match.id]
            if (previous == null || match.score > previous.second) best[match.id] = index to match.score
        }
        return best.mapNotNull { (id, hit) -> recognizer.nameOf(id)?.let { NameTag(hit.first, it, TagKind.ITEM) } }
    }

    override fun close() = embedder.close()
}

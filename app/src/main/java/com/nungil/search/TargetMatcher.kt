package com.nungil.search

import com.nungil.contract.app.VisionFrame
import java.io.Closeable

/** Finds the search target inside one analysed frame. */
interface TargetMatcher : Closeable {
    /** true = slow (faces, embeddings): run on the extras thread and drop frames while it is busy. */
    val slow: Boolean

    /** Index into [VisionFrame.detections] of the target, or -1. Called by one thread at a time. */
    fun find(frame: VisionFrame): Int

    override fun close() = Unit
}

/** A COCO label target: the highest-scoring detection with that label. */
class LabelMatcher(private val label: String) : TargetMatcher {
    override val slow: Boolean = false

    override fun find(frame: VisionFrame): Int {
        var best = -1
        var bestScore = -1f
        frame.detections.forEachIndexed { i, d ->
            if (d.label == label && d.score > bestScore) {
                best = i
                bestScore = d.score
            }
        }
        return best
    }
}

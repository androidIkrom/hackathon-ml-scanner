package com.nungil.search

import com.nungil.contract.Box
import com.nungil.contract.app.VisionFrame
import java.io.Closeable

/** Finds the search target inside one analysed frame. */
interface TargetMatcher : Closeable {
    /** true = slow (faces, embeddings): run on the extras thread and drop frames while it is busy. */
    val slow: Boolean

    /** Where the target is in the upright image, or null. Called by one thread at a time. */
    fun locate(frame: VisionFrame): Box?

    override fun close() = Unit
}

/** A COCO label target: the highest-scoring detection with that label. */
class LabelMatcher(private val label: String) : TargetMatcher {
    override val slow: Boolean = false

    override fun locate(frame: VisionFrame): Box? =
        frame.detections.filter { it.label == label }.maxByOrNull { it.score }?.box
}

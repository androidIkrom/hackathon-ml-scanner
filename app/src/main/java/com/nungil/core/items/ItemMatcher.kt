package com.nungil.core.items

import com.nungil.core.people.FaceMatcher

/** Saved objects are matched by how they look: cosine between MobileNetV3-Small embeddings of at least [THRESHOLD]. */
object ItemMatcher {
    const val THRESHOLD = 0.75f

    /**
     * Finding one item the user asked for takes a looser match than naming things unasked: the same item seen
     * a little to the side or farther away scored 0.5 to 0.75 against its samples, other things 0.25 and less
     * (the logs), so 0.75 found it only from where it was learned.
     */
    const val FIND_THRESHOLD = 0.55f

    /**
     * Once the item is found it is kept at a lower score, so it is not lost and found again with every frame:
     * a remote on a table scored 0.47 to 0.61 while the phone pointed at it, and at most 0.32 when it did not
     * (the logs).
     */
    const val KEEP_THRESHOLD = 0.45f

    /** A grid window that scores at least this is worth a closer look (ItemWindows.around). */
    const val LOOK_CLOSER = 0.35f

    data class Match(val id: Long, val score: Float)

    /** Best saved item for [vector] by its closest sample, or null below [threshold]. */
    fun bestMatch(vector: FloatArray, known: Map<Long, List<FloatArray>>, threshold: Float = THRESHOLD): Match? =
        known
            .filterValues { it.isNotEmpty() }
            .map { (id, samples) -> Match(id, samples.maxOf { FaceMatcher.cosine(vector, it) }) }
            .filter { it.score >= threshold }
            .maxByOrNull { it.score }
}

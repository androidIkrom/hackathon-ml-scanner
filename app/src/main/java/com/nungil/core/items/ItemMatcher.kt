package com.nungil.core.items

import com.nungil.core.people.FaceMatcher

/** Saved objects are matched by how they look: cosine between MobileNetV3-Small embeddings of at least [THRESHOLD]. */
object ItemMatcher {
    const val THRESHOLD = 0.75f

    data class Match(val id: Long, val score: Float)

    /** Best saved item for [vector] by its closest sample, or null below [threshold]. */
    fun bestMatch(vector: FloatArray, known: Map<Long, List<FloatArray>>, threshold: Float = THRESHOLD): Match? =
        known
            .filterValues { it.isNotEmpty() }
            .map { (id, samples) -> Match(id, samples.maxOf { FaceMatcher.cosine(vector, it) }) }
            .filter { it.score >= threshold }
            .maxByOrNull { it.score }

    /** The label seen most often while enrolling (ties: the one seen first), instead of the first frame's guess. */
    fun mostCommon(labels: List<String>): String? {
        if (labels.isEmpty()) return null
        val counts = labels.groupingBy { it }.eachCount()
        val top = counts.values.max()
        return labels.first { counts[it] == top }
    }
}

package com.nungil.core.people

import kotlin.math.sqrt

/**
 * Face matching tuned so strangers are not greeted by a saved name (brief §6): cosine at least [THRESHOLD],
 * the winner ahead of the runner-up by [MARGIN], and each person scored by the mean of their [TOP_SAMPLES]
 * best samples instead of one lucky sample. The reference repository's 0.3 on one sample matched strangers.
 */
object FaceMatcher {
    const val THRESHOLD = 0.5f
    const val MARGIN = 0.08f
    const val TOP_SAMPLES = 3

    data class Match(val id: Long, val score: Float)

    /** Cosine similarity in -1..1; 0 when either vector is all zeros. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "vector sizes differ: ${a.size} vs ${b.size}" }
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    /** Mean cosine of the [TOP_SAMPLES] samples closest to [vector]; -1 without samples. */
    fun personScore(vector: FloatArray, samples: List<FloatArray>): Float {
        if (samples.isEmpty()) return -1f
        return samples.map { cosine(vector, it) }.sortedDescending().take(TOP_SAMPLES).average().toFloat()
    }

    /** The saved person [vector] belongs to, or null. [known] maps person id to that person's samples. */
    fun bestMatch(
        vector: FloatArray,
        known: Map<Long, List<FloatArray>>,
        threshold: Float = THRESHOLD,
        margin: Float = MARGIN,
    ): Match? {
        val ranked = known
            .filterValues { it.isNotEmpty() }
            .map { (id, samples) -> Match(id, personScore(vector, samples)) }
            .sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return null
        if (best.score < threshold) return null
        val second = ranked.getOrNull(1)
        if (second != null && best.score - second.score < margin) return null
        return best
    }
}

package com.nungil.core.scan

import kotlin.math.abs
import kotlin.math.max

/**
 * Deduplication, the single most important algorithm.
 * A detection joins a cluster when the label matches and the angle is within [mergeDeg].
 * A cluster's count is the MAXIMUM seen in one frame, never a sum across frames: summing turns three chairs
 * into three hundred over a hundred frames. A cluster is spoken only after [confirmFrames] frames.
 * Colour is a majority vote inside the cluster and is not part of the key, so colour flicker cannot split
 * one chair into two.
 */
class ObjectClusterer(val mergeDeg: Float = MERGE_DEG, val confirmFrames: Int = CONFIRM_FRAMES) {

    private class Cluster(val label: String, var angle: Float, val isName: Boolean, var wasPerson: Boolean) {
        var samples = 0
        var framesSeen = 0
        var count = 0
        val colorVotes = LinkedHashMap<ColorName, Int>()

        /** The winning colour: at least [MIN_COLOR_VOTES] votes and strictly more than any other colour. */
        fun color(): ColorName? {
            val sorted = colorVotes.entries.sortedByDescending { it.value }
            val best = sorted.firstOrNull() ?: return null
            if (best.value < MIN_COLOR_VOTES) return null
            val runnerUp = sorted.getOrNull(1)?.value ?: 0
            return if (best.value > runnerUp) best.key else null
        }

        fun summary() = ObjectSummary(label, count, color(), angle, isName, wasPerson)
    }

    private val clusters = mutableListOf<Cluster>()

    /** Adds one frame; returns the clusters that became confirmed with this frame (for live announcements). */
    fun addFrame(detections: List<FrameDetection>): List<ObjectSummary> {
        val inThisFrame = LinkedHashMap<Cluster, Int>()
        for (d in detections) {
            val cluster = clusters
                .filter { it.label == d.label && abs(AngleMath.diff(it.angle, d.angle)) <= mergeDeg }
                .minByOrNull { abs(AngleMath.diff(it.angle, d.angle)) }
                ?: Cluster(d.label, d.angle, d.isName, d.wasPerson).also { clusters += it }
            cluster.angle = AngleMath.weightedMean(cluster.angle, cluster.samples, d.angle)
            cluster.samples++
            cluster.wasPerson = cluster.wasPerson || d.wasPerson
            d.color?.let { cluster.colorVotes[it] = (cluster.colorVotes[it] ?: 0) + 1 }
            inThisFrame[cluster] = (inThisFrame[cluster] ?: 0) + 1
        }
        val newlyConfirmed = mutableListOf<ObjectSummary>()
        for ((cluster, n) in inThisFrame) {
            cluster.framesSeen++
            cluster.count = max(cluster.count, n)
            if (cluster.framesSeen == confirmFrames) newlyConfirmed += cluster.summary()
        }
        return newlyConfirmed
    }

    /** Every cluster seen in at least [confirmFrames] frames. Unconfirmed clusters are never spoken. */
    fun confirmed(): List<ObjectSummary> = clusters.filter { it.framesSeen >= confirmFrames }.map { it.summary() }

    companion object {
        const val MERGE_DEG = 20f
        const val CONFIRM_FRAMES = 3
        const val MIN_COLOR_VOTES = 2
    }
}

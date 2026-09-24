package com.nungil.core.scan

import com.nungil.contract.Box

/**
 * Once a tracked box has been recognised as a saved name it keeps that name across frames instead of
 * flickering back to "person". Not thread-safe: call it from one thread (the analysis thread).
 */
class StickyNames(
    val confirmHits: Int = CONFIRM_HITS,
    val forgetAfterFrames: Int = FORGET_AFTER_FRAMES,
    val minIou: Float = MIN_IOU,
) {
    data class Sticky(val name: String, val isPerson: Boolean)

    private class Track(var box: Box, var name: String, var isPerson: Boolean, var hits: Int, var lastSeen: Long)

    private val tracks = mutableListOf<Track>()
    private var frame = 0L

    /** A recogniser said [box] is [name]. Two agreeing hits on overlapping boxes make the name stick. */
    fun recognized(box: Box, name: String, isPerson: Boolean) {
        val track = bestTrack(box)
        when {
            track == null -> tracks += Track(box, name, isPerson, 1, frame)
            track.name == name -> {
                track.hits++
                track.box = box
                track.lastSeen = frame
            }
            else -> {
                track.name = name
                track.isPerson = isPerson
                track.hits = 1
                track.box = box
                track.lastSeen = frame
            }
        }
    }

    /** Call once per frame with that frame's boxes; returns the sticky name for each box, or null. */
    fun apply(boxes: List<Box>): List<Sticky?> {
        frame++
        tracks.removeAll { frame - it.lastSeen > forgetAfterFrames }
        return boxes.map { box ->
            val track = bestTrack(box)
            if (track == null) {
                null
            } else {
                track.box = box
                track.lastSeen = frame
                if (track.hits >= confirmHits) Sticky(track.name, track.isPerson) else null
            }
        }
    }

    private fun bestTrack(box: Box): Track? =
        tracks.map { it to BoxGeometry.iou(it.box, box) }
            .filter { it.second >= minIou }
            .maxByOrNull { it.second }
            ?.first

    companion object {
        const val CONFIRM_HITS = 2
        const val FORGET_AFTER_FRAMES = 15
        const val MIN_IOU = 0.3f
    }
}

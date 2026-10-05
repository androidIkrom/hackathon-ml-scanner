package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Detection

/**
 * Detector labels corrected by a closer look (LaptopCheck): a box checked once keeps its corrected label while it is
 * tracked from frame to frame, and is not checked again. Not thread-safe: the scan's analysis thread only.
 */
class Relabels(private val forgetAfterFrames: Int = FORGET_AFTER_FRAMES, private val minIou: Float = MIN_IOU) {
    private class Track(var box: Box, val label: String, var lastSeen: Long)

    private val tracks = mutableListOf<Track>()
    private var frame = 0L

    /** Whether [d] is a label worth a closer look that has not had one. */
    fun needsCheck(d: Detection): Boolean = d.label == LaptopCheck.KEYBOARD && track(d.box) == null

    /** [box] was looked at and is [label]. */
    fun checked(box: Box, label: String) {
        tracks += Track(box, label, frame)
    }

    /** Once per frame: the detections with corrected labels; tracks not seen for a while are forgotten. */
    fun apply(detections: List<Detection>): List<Detection> {
        frame++
        tracks.removeAll { frame - it.lastSeen > forgetAfterFrames }
        return detections.map { d ->
            val t = track(d.box) ?: return@map d
            t.box = d.box
            t.lastSeen = frame
            if (t.label == d.label) d else d.copy(label = t.label)
        }
    }

    private fun track(box: Box): Track? =
        tracks.map { it to BoxGeometry.iou(it.box, box) }.filter { it.second >= minIou }.maxByOrNull { it.second }?.first

    companion object {
        const val FORGET_AFTER_FRAMES = 15
        const val MIN_IOU = 0.3f
    }
}

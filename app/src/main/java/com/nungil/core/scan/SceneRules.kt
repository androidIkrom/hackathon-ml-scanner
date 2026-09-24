package com.nungil.core.scan

import com.nungil.contract.Detection
import kotlin.math.abs

/** Rules for "what is this": prefer a detector box at the centre, else classify the middle of the frame. */
object SceneRules {
    /** The 1000-class classifier's guess is spoken only above this score. */
    const val MIN_SCORE = 0.35f

    /** Share of the width and height the classifier looks at, around the centre. */
    const val CENTER_SHARE = 0.5f

    /** Pixel crop (left, top, width, height) of the middle [CENTER_SHARE] of a [width] x [height] image. */
    fun centerCrop(width: Int, height: Int): IntArray {
        val w = (width * CENTER_SHARE).toInt().coerceAtLeast(1)
        val h = (height * CENTER_SHARE).toInt().coerceAtLeast(1)
        return intArrayOf((width - w) / 2, (height - h) / 2, w, h)
    }

    /** The detection whose box contains the image centre, highest score first; null when none does. */
    fun centerDetection(detections: List<Detection>): Detection? =
        detections
            .filter { it.box.left <= 0.5f && it.box.right >= 0.5f && it.box.top <= 0.5f && it.box.bottom >= 0.5f }
            .maxByOrNull { it.score }

    /** Of the given indices into [detections], the one whose box centre is closest to the middle; null if empty. */
    fun nearestToCenter(detections: List<Detection>, indices: List<Int>): Int? =
        indices.minByOrNull { abs(detections[it].box.centerX - 0.5f) }
}

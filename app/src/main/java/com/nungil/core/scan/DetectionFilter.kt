package com.nungil.core.scan

import com.nungil.contract.Detection
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The detector itself runs at [DETECTOR_THRESHOLD]; this decides per label what is kept.
 * People are accepted 0.2 lower than everything else (far and half-hidden people score low),
 * but never below the detector threshold. Maths in tenths, so 0.7 - 0.2 is exactly 0.5.
 */
object DetectionFilter {
    const val DETECTOR_THRESHOLD = 0.3f
    const val PERSON_BONUS_TENTHS = 2
    private const val MIN_TENTHS = 3

    /** Score a detection of [label] needs when the user's setting is [minScore]. */
    fun minScoreFor(label: String, minScore: Float): Float {
        val tenths = (minScore * 10f).roundToInt()
        return if (label == "person") max(MIN_TENTHS, tenths - PERSON_BONUS_TENTHS) / 10f else tenths / 10f
    }

    /** Per-label filtering for scans. */
    fun keep(detections: List<Detection>, minScore: Float): List<Detection> =
        detections.filter { it.score >= minScoreFor(it.label, minScore) }

    /** One fixed floor for every label (search runs at 0.4, enrolment at 0.3). */
    fun keepAtLeast(detections: List<Detection>, floor: Float): List<Detection> =
        detections.filter { it.score >= floor }
}

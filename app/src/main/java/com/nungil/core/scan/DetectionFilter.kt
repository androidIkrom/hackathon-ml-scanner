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

    /**
     * Big furniture and appliances filling at least [BIG_SHARE] of the view need [BIG_EXTRA_TENTHS] more: a plain
     * wall was "refrigerator 0.72" over 59 % of a desk photo, and Live said "a gray bed" at a desk at 0.70-0.72
     * (live-quality plan, Task 5). A real one seen whole, or surer, is still said.
     */
    val BIG_THINGS = setOf("bed", "couch", "refrigerator", "dining table", "oven")
    const val BIG_SHARE = 0.5f
    const val BIG_EXTRA_TENTHS = 1

    /** Score [d] needs when the user's setting is [minScore]: by its label, and more for a big thing filling the view. */
    fun minScoreFor(d: Detection, minScore: Float): Float {
        val base = minScoreFor(d.label, minScore)
        return if (d.label in BIG_THINGS && d.box.area >= BIG_SHARE) ((base * 10f).roundToInt() + BIG_EXTRA_TENTHS) / 10f else base
    }

    /** Per-label filtering for scans. */
    fun keep(detections: List<Detection>, minScore: Float): List<Detection> =
        detections.filter { it.score >= minScoreFor(it, minScore) }

    /** One fixed floor for every label (search runs at 0.4, enrolment at 0.3). */
    fun keepAtLeast(detections: List<Detection>, floor: Float): List<Detection> =
        detections.filter { it.score >= floor }
}

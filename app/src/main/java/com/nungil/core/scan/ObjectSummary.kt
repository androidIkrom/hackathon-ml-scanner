package com.nungil.core.scan

/**
 * One detection in one frame, already turned into a direction.
 * @param label COCO label, or the saved name when [isName] is true.
 * @param angle degrees relative to the scan's start heading.
 * @param wasPerson true when the detector saw a "person" (so a name on it is a person's name).
 */
data class FrameDetection(
    val label: String,
    val angle: Float,
    val color: ColorName?,
    val isName: Boolean = false,
    val wasPerson: Boolean = false,
)

/** A confirmed object (cluster) as the summary speaks it and history stores it. */
data class ObjectSummary(
    val label: String,
    val count: Int,
    val color: ColorName?,
    val angle: Float,
    val isName: Boolean = false,
    val wasPerson: Boolean = false,
)

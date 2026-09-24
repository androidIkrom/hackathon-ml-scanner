package com.nungil.core.people

import kotlin.math.abs

/** The five head poses of face enrolment, in the order they are collected. */
enum class Pose { STRAIGHT, LEFT, RIGHT, UP, DOWN }

/**
 * Collects [samplesPerPose] samples for each of the five poses (20 by default), gating each pose on ML Kit's
 * head angles: straight |yaw| and |pitch| < 10; left/right |yaw| > 20; up pitch > 12; down pitch < -12.
 *
 * ML Kit's yaw sign depends on the camera and on whether the image is mirrored, so LEFT accepts either side and
 * remembers its sign; RIGHT then requires the opposite sign. The user hears "one side", then "the other side".
 */
class EnrollmentGuide(val samplesPerPose: Int = SAMPLES_PER_POSE) {
    private val counts = IntArray(Pose.entries.size)
    private var firstSideSign = 0

    val total: Int = samplesPerPose * Pose.entries.size
    val taken: Int get() = counts.sum()

    /** The pose being collected; null when enrolment is complete. */
    val pose: Pose? get() = Pose.entries.firstOrNull { counts[it.ordinal] < samplesPerPose }
    val isDone: Boolean get() = pose == null

    fun accepts(yawDeg: Float, pitchDeg: Float): Boolean = when (pose) {
        Pose.STRAIGHT -> abs(yawDeg) < STRAIGHT_MAX_DEG && abs(pitchDeg) < STRAIGHT_MAX_DEG
        Pose.LEFT -> abs(yawDeg) > SIDE_MIN_YAW_DEG && (firstSideSign == 0 || sign(yawDeg) == firstSideSign)
        Pose.RIGHT -> abs(yawDeg) > SIDE_MIN_YAW_DEG && sign(yawDeg) == -firstSideSign
        Pose.UP -> pitchDeg > TILT_MIN_PITCH_DEG
        Pose.DOWN -> pitchDeg < -TILT_MIN_PITCH_DEG
        null -> false
    }

    /**
     * Records one sample for the current pose. Call only after [accepts] returned true for the same angles.
     * Returns true when this sample finished a pose (or the whole enrolment).
     */
    fun add(yawDeg: Float): Boolean {
        val current = pose ?: return false
        if (current == Pose.LEFT && firstSideSign == 0) firstSideSign = sign(yawDeg)
        counts[current.ordinal]++
        return pose != current
    }

    /** 0..100, for the progress bar and the spoken percent. */
    fun percent(): Int = taken * 100 / total

    private fun sign(v: Float): Int = if (v >= 0f) 1 else -1

    companion object {
        const val SAMPLES_PER_POSE = 4
        const val STRAIGHT_MAX_DEG = 10f
        const val SIDE_MIN_YAW_DEG = 20f
        const val TILT_MIN_PITCH_DEG = 12f
    }
}

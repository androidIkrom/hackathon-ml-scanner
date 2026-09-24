package com.nungil.core.people

import kotlin.math.abs

/** The five head poses of face enrolment, in the order they are prompted. */
enum class Pose { STRAIGHT, LEFT, RIGHT, UP, DOWN }

/** Why a seen face was not taken, so the user can be told what to change. */
enum class PoseHint { CLOSER, MORE, LESS, OTHER_SIDE, REPEAT }

/**
 * One sweep of the head: [straightSamples] samples looking straight (face matching averages the best 3) and
 * [sideSamples] for each of one side, the other side, up and down (7 by default). A pose is taken as soon as the
 * head reaches it, in any order, so turning straight → right → left → up → down in one go finishes enrolment.
 * Gates on ML Kit's head angles: straight |yaw| and |pitch| < 10; sides |yaw| > 20; up pitch > 12; down pitch < -12.
 *
 * ML Kit's yaw sign depends on the camera and on whether the image is mirrored, so the first side seen becomes
 * LEFT and remembers its sign; RIGHT is the opposite sign.
 */
class EnrollmentGuide(
    val straightSamples: Int = STRAIGHT_SAMPLES,
    val sideSamples: Int = SIDE_SAMPLES,
) {
    private val counts = IntArray(Pose.entries.size)
    private var firstSideSign = 0

    val total: Int = straightSamples + sideSamples * (Pose.entries.size - 1)
    val taken: Int get() = counts.sum()

    /** The first pose still missing, in prompt order; null when enrolment is complete. */
    val pose: Pose? get() = Pose.entries.firstOrNull { !isFull(it) }
    val isDone: Boolean get() = pose == null

    private fun needed(p: Pose): Int = if (p == Pose.STRAIGHT) straightSamples else sideSamples
    private fun isFull(p: Pose): Boolean = counts[p.ordinal] >= needed(p)

    /** The missing pose these angles fit, or null. */
    fun match(yawDeg: Float, pitchDeg: Float): Pose? {
        val candidate = when {
            abs(yawDeg) < STRAIGHT_MAX_DEG && abs(pitchDeg) < STRAIGHT_MAX_DEG -> Pose.STRAIGHT
            abs(yawDeg) > SIDE_MIN_YAW_DEG ->
                if (firstSideSign == 0 || sign(yawDeg) == firstSideSign) Pose.LEFT else Pose.RIGHT
            pitchDeg > TILT_MIN_PITCH_DEG -> Pose.UP
            pitchDeg < -TILT_MIN_PITCH_DEG -> Pose.DOWN
            else -> null
        } ?: return null
        return candidate.takeUnless { isFull(it) }
    }

    fun accepts(yawDeg: Float, pitchDeg: Float): Boolean = match(yawDeg, pitchDeg) != null

    /** Records one sample; returns the pose it counted for, or null when the angles fit no missing pose. */
    fun add(yawDeg: Float, pitchDeg: Float): Pose? {
        val p = match(yawDeg, pitchDeg) ?: return null
        if (p == Pose.LEFT && firstSideSign == 0) firstSideSign = sign(yawDeg)
        counts[p.ordinal]++
        return p
    }

    /**
     * Why a face of [sizePx] at these angles is not taken, aimed at the next missing [pose], or null when it is
     * taken (it passes [FaceQuality.usable] and [accepts]). Turned past FaceQuality's limit gives LESS.
     */
    fun hint(sizePx: Int, yawDeg: Float, pitchDeg: Float): PoseHint? {
        val current = pose ?: return null
        if (FaceQuality.usable(sizePx, yawDeg, pitchDeg) && accepts(yawDeg, pitchDeg)) return null
        if (sizePx < FaceQuality.MIN_SIZE_PX) return PoseHint.CLOSER
        val yaw = abs(yawDeg)
        return when (current) {
            Pose.STRAIGHT -> PoseHint.REPEAT
            Pose.LEFT, Pose.RIGHT -> when {
                yaw > FaceQuality.MAX_YAW_DEG -> PoseHint.LESS
                current == Pose.RIGHT && yaw > SIDE_MIN_YAW_DEG && sign(yawDeg) != -firstSideSign -> PoseHint.OTHER_SIDE
                yaw <= SIDE_MIN_YAW_DEG -> PoseHint.MORE
                else -> PoseHint.REPEAT
            }
            Pose.UP, Pose.DOWN -> {
                val toward = if (current == Pose.UP) pitchDeg else -pitchDeg
                when {
                    abs(pitchDeg) > FaceQuality.MAX_PITCH_DEG && toward > 0f -> PoseHint.LESS
                    toward <= TILT_MIN_PITCH_DEG -> PoseHint.MORE
                    else -> PoseHint.REPEAT
                }
            }
        }
    }

    /** 0..100, for the progress bar. */
    fun percent(): Int = taken * 100 / total

    private fun sign(v: Float): Int = if (v >= 0f) 1 else -1

    companion object {
        const val STRAIGHT_SAMPLES = 3
        const val SIDE_SAMPLES = 1
        const val STRAIGHT_MAX_DEG = 10f
        const val SIDE_MIN_YAW_DEG = 20f
        const val TILT_MIN_PITCH_DEG = 12f
    }
}

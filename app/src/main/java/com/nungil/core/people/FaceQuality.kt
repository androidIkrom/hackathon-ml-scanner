package com.nungil.core.people

import kotlin.math.abs

/** Faces too small or turned too far give embeddings that match the wrong person; skip them. */
object FaceQuality {
    const val MIN_SIZE_PX = 64
    const val MAX_YAW_DEG = 35f
    const val MAX_PITCH_DEG = 25f

    /** [sizePx] is the smaller side of the face box in frame pixels; angles are ML Kit's Euler Y and X. */
    fun usable(sizePx: Int, yawDeg: Float, pitchDeg: Float): Boolean =
        sizePx >= MIN_SIZE_PX && abs(yawDeg) <= MAX_YAW_DEG && abs(pitchDeg) <= MAX_PITCH_DEG
}

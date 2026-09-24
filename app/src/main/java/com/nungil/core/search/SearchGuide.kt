package com.nungil.core.search

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import kotlin.math.abs
import kotlin.math.roundToLong

/** Where the target is across the picture, from the user's point of view. */
enum class Zone { FAR_LEFT, LEFT, AHEAD, RIGHT, FAR_RIGHT }

/** Five zones by box centre and a beep that speeds up as the target nears the centre. */
object SearchGuide {
    const val FARTHEST_MS = 1_000L
    const val NEAREST_MS = 150L

    /**
     * Box centre as the user experiences it. The front camera's boxes are not mirrored (contract), but the user
     * faces the screen, so the camera's left is the user's right.
     */
    fun userX(centerX: Float, facing: Facing): Float = if (facing == Facing.FRONT) 1f - centerX else centerX

    /** far left < 0.2, left < 0.4, ahead 0.4–0.6, right <= 0.8, far right above. */
    fun zone(x: Float): Zone = when {
        x < 0.2f -> Zone.FAR_LEFT
        x < 0.4f -> Zone.LEFT
        x <= 0.6f -> Zone.AHEAD
        x <= 0.8f -> Zone.RIGHT
        else -> Zone.FAR_RIGHT
    }

    fun isCentered(x: Float): Boolean = zone(x) == Zone.AHEAD

    /** 1000 ms at either edge, shrinking linearly to 150 ms at the centre. */
    fun beepIntervalMs(x: Float): Long {
        val offCentre = (abs(x - 0.5f) / 0.5f).coerceIn(0f, 1f)
        return (NEAREST_MS + (FARTHEST_MS - NEAREST_MS) * offCentre).roundToLong()
    }

    fun zoneWord(zone: Zone, lang: Lang): String = when (lang) {
        Lang.EN -> when (zone) {
            Zone.FAR_LEFT -> "far left"
            Zone.LEFT -> "left"
            Zone.AHEAD -> "ahead"
            Zone.RIGHT -> "right"
            Zone.FAR_RIGHT -> "far right"
        }
        Lang.KO -> when (zone) {
            Zone.FAR_LEFT -> "왼쪽 끝"
            Zone.LEFT -> "왼쪽"
            Zone.AHEAD -> "정면"
            Zone.RIGHT -> "오른쪽"
            Zone.FAR_RIGHT -> "오른쪽 끝"
        }
    }
}

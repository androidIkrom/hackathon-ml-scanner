package com.nungil.core.scan

import kotlin.math.atan

/** Horizontal field of view of the upright (portrait) image from the camera's physical data. */
object FovMath {
    const val FALLBACK_DEG = 65f

    /**
     * 2 * atan(side / (2 * focal)) in degrees. Sensors are mounted sideways (orientation 90 or 270), so the
     * portrait image's width is the sensor's physical height; for 0 or 180 it is the sensor's width.
     * Anything unusable gives [FALLBACK_DEG].
     */
    fun horizontalFovDeg(sensorWidthMm: Float, sensorHeightMm: Float, focalMm: Float, sensorOrientation: Int): Float {
        val side = if (sensorOrientation % 180 == 0) sensorWidthMm else sensorHeightMm
        if (!(side > 0f) || !(focalMm > 0f)) return FALLBACK_DEG
        val deg = Math.toDegrees(2.0 * atan(side / (2.0 * focalMm))).toFloat()
        return if (deg in 10f..170f) deg else FALLBACK_DEG
    }
}

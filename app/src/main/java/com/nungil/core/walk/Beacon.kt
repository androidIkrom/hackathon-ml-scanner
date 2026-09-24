package com.nungil.core.walk

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

data class Place(val name: String, val point: LatLon)

/** Straight-line guidance to a place: distance, compass bearing and a clock direction. */
object Beacon {
    private const val EARTH_M = 6_371_000.0

    fun distanceMetres(a: LatLon, b: LatLon): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Compass bearing from [a] to [b], 0..360, 0 = north. */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val y = sin(rad(b.lon - a.lon)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lon - a.lon))
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** "12" = straight ahead, "3" = right, "6" = behind, "9" = left, for a bearing relative to the heading. */
    fun clock(relativeDeg: Double): Int {
        val norm = ((relativeDeg % 360) + 360) % 360
        val hour = (norm / 30).roundToInt() % 12
        return if (hour == 0) 12 else hour
    }

    private fun rad(deg: Double) = deg * PI / 180
}

/** Step length from body height (0.415 × height) and distances spoken in steps. */
object StepLength {
    const val HEIGHT_TO_STEP = 0.415f
    const val DEFAULT_BODY_HEIGHT_M = 1.70f

    fun metres(bodyHeightM: Float = DEFAULT_BODY_HEIGHT_M): Float = bodyHeightM * HEIGHT_TO_STEP

    /** At least one step. */
    fun steps(distanceM: Float, stepM: Float): Int = maxOf(1, (distanceM / stepM).roundToInt())
}

/** Beeps for closeness: 1000 ms at 4 m down to 150 ms at 0.5 m, plus a short vibration under 1 m. */
object WalkBeep {
    const val FARTHEST_M = 4f
    const val NEAREST_M = 0.5f
    const val FARTHEST_MS = 1_000L
    const val NEAREST_MS = 150L
    const val VIBRATE_UNDER_M = 1f

    /** 0 = no beep (nothing ahead, or farther than [FARTHEST_M]). */
    fun intervalMs(distanceM: Float?): Long {
        if (distanceM == null || distanceM > FARTHEST_M) return 0
        val t = ((distanceM - NEAREST_M) / (FARTHEST_M - NEAREST_M)).coerceIn(0f, 1f)
        return (NEAREST_MS + t * (FARTHEST_MS - NEAREST_MS)).toLong()
    }

    fun vibrate(distanceM: Float?): Boolean = distanceM != null && distanceM < VIBRATE_UNDER_M
}

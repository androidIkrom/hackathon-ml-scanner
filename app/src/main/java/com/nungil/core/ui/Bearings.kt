package com.nungil.core.ui

import com.nungil.contract.Lang
import kotlin.math.abs
import kotlin.math.roundToInt

/** How directions are said: words ("on your left") or clock hours ("at 9 o'clock"), a setting. */
enum class DirectionStyle { WORDS, CLOCK }

/**
 * The one way every screen says a direction (spec 2026-10-05-shared-announcer). A screen gives a bearing in degrees,
 * 0 straight ahead, negative to the left, -180..180: from the compass in a scan, from the place in the frame in Find
 * and Walk. The same direction used to be "in front", "ahead" or "far left" depending on the screen.
 *
 * Find and Walk see only the frame (about ±32°), so the words split it finely: ahead up to [AHEAD_DEG], slightly to
 * one side up to [SLIGHT_DEG], then to the side up to [SIDE_DEG], and behind beyond.
 */
object Bearings {
    const val AHEAD_DEG = 7f
    const val SLIGHT_DEG = 20f
    const val SIDE_DEG = 135f

    fun say(deg: Float, style: DirectionStyle, lang: Lang): String = when (style) {
        DirectionStyle.CLOCK -> if (lang == Lang.KO) "${clockHour(deg)}시 방향에" else "at ${clockHour(deg)} o'clock"
        DirectionStyle.WORDS -> words(deg, lang)
    }

    /** The clock hour of [deg]: 12 straight ahead, 3 to the right, 6 behind, 9 to the left. */
    fun clockHour(deg: Float): Int {
        val hour = Math.floorMod((deg / 30f).roundToInt(), 12)
        return if (hour == 0) 12 else hour
    }

    private fun words(deg: Float, lang: Lang): String {
        val a = abs(deg)
        val left = deg < 0f
        val ko = lang == Lang.KO
        return when {
            a <= AHEAD_DEG -> if (ko) "앞에" else "ahead"
            a <= SLIGHT_DEG -> if (ko) (if (left) "조금 왼쪽에" else "조금 오른쪽에") else (if (left) "slightly left" else "slightly right")
            a <= SIDE_DEG -> if (ko) (if (left) "왼쪽에" else "오른쪽에") else (if (left) "on your left" else "on your right")
            else -> if (ko) "뒤에" else "behind you"
        }
    }
}

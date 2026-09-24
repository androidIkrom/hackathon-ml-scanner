package com.nungil.core.ui

import com.nungil.contract.Lang
import com.nungil.contract.ScanSettings
import kotlin.math.roundToInt

/**
 * The confidence slider works in whole percent, 30..70 in steps of 5. Material's Slider crashes on a
 * value that is not on a step, so every saved score is snapped before it is shown.
 */
object SettingsMath {
    const val SLIDER_FROM = ScanSettings.MIN_SCORE_LOW * 100f
    const val SLIDER_TO = ScanSettings.MIN_SCORE_HIGH * 100f
    const val SLIDER_STEP = 5f

    fun scoreToSlider(score: Float): Float {
        val percent = (score * 100f).coerceIn(SLIDER_FROM, SLIDER_TO)
        return (percent / SLIDER_STEP).roundToInt() * SLIDER_STEP
    }

    fun sliderToScore(value: Float): Float = value / 100f
}

/** One line under each history entry: the mode, and the coverage of a full scan. */
object HistoryText {
    fun line(mode: String, coveragePercent: Int, lang: Lang): String {
        val ko = lang == Lang.KO
        return when (mode) {
            "FULL" -> (if (ko) "주변 둘러보기" else "Look around") + " · $coveragePercent%"
            "LIVE" -> if (ko) "실시간 안내" else "Live scan"
            else -> mode
        }
    }

    fun pickFirst(lang: Lang): String =
        if (lang == Lang.KO) "먼저 기록을 하나 눌러 주세요." else "Tap a scan first."

    fun deleted(lang: Lang): String = if (lang == Lang.KO) "기록을 지웠어요." else "Scan deleted."

    fun empty(lang: Lang): String = if (lang == Lang.KO) "아직 기록이 없어요." else "No scans yet."
}

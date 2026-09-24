package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMathTest {
    @Test fun sliderShowsTheSavedScoreOnItsFivePercentSteps() {
        assertEquals(70f, SettingsMath.scoreToSlider(0.7f))
        assertEquals(60f, SettingsMath.scoreToSlider(0.62f))
        assertEquals(65f, SettingsMath.scoreToSlider(0.63f))
    }

    @Test fun sliderStaysInsideThirtyToSeventy() {
        assertEquals(30f, SettingsMath.scoreToSlider(0.1f))
        assertEquals(70f, SettingsMath.scoreToSlider(0.95f))
    }

    @Test fun sliderValueBecomesAScore() = assertEquals(0.55f, SettingsMath.sliderToScore(55f), 1e-6f)

    @Test fun historyLines() {
        assertEquals("Look around · 80%", HistoryText.line("FULL", 80, Lang.EN))
        assertEquals("주변 둘러보기 · 80%", HistoryText.line("FULL", 80, Lang.KO))
        assertEquals("Live scan", HistoryText.line("LIVE", 0, Lang.EN))
        assertEquals("실시간 안내", HistoryText.line("LIVE", 0, Lang.KO))
    }

    @Test fun historySentences() {
        assertEquals("Tap a scan first.", HistoryText.pickFirst(Lang.EN))
        assertEquals("기록을 지웠어요.", HistoryText.deleted(Lang.KO))
        assertEquals("아직 기록이 없어요.", HistoryText.empty(Lang.KO))
    }
}

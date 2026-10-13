package com.nungil.core.ui

import com.nungil.contract.Compute
import com.nungil.contract.Lang
import com.nungil.contract.ModelChoice
import com.nungil.contract.SettingChange
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingPhrasesTest {
    @Test fun saidBack() {
        assertEquals("Model: accurate.", SettingPhrases.done(SettingChange.Model(ModelChoice.ACCURATE), Lang.EN))
        assertEquals("Colours off.", SettingPhrases.done(SettingChange.Colors(false), Lang.EN))
        assertEquals("Processor: CPU.", SettingPhrases.done(SettingChange.Compute(Compute.CPU), Lang.EN))
        assertEquals("모델: 정확.", SettingPhrases.done(SettingChange.Model(ModelChoice.ACCURATE), Lang.KO))
    }

    @Test fun aCameraScreenOpenNowUsesItNextTime() =
        assertEquals("Model: fast. From the next scan.", SettingPhrases.done(SettingChange.Model(ModelChoice.FAST), Lang.EN, nextTime = true))
}

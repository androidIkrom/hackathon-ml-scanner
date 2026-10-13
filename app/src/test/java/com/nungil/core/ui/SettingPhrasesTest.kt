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

    @Test fun surenessSaidBack() {
        assertEquals("Sureness 60 percent: more things, some may be wrong.", SettingPhrases.sureness(70, 60, Lang.EN))
        assertEquals("Sureness 70 percent: fewer things, more sure.", SettingPhrases.sureness(60, 70, Lang.EN))
        assertEquals("Already the most sure: 70 percent.", SettingPhrases.sureness(70, 70, Lang.EN))
        assertEquals("Already the least sure: 30 percent.", SettingPhrases.sureness(30, 30, Lang.EN))
        assertEquals("Sureness is 50 percent.", SettingPhrases.sureness(50, 50, Lang.EN))
        assertEquals("확신 60%: 더 많이 말해요. 틀릴 수도 있어요.", SettingPhrases.sureness(70, 60, Lang.KO))
        assertEquals("이미 가장 확실해요: 70%.", SettingPhrases.sureness(70, 70, Lang.KO))
    }

    @Test fun aCameraScreenOpenNowUsesItNextTime() =
        assertEquals("Model: fast. From the next scan.", SettingPhrases.done(SettingChange.Model(ModelChoice.FAST), Lang.EN, nextTime = true))
}

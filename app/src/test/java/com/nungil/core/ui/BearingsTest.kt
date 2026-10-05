package com.nungil.core.ui

import com.nungil.contract.DirectionStyle
import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class BearingsTest {
    private fun words(deg: Float, lang: Lang = Lang.EN) = Bearings.say(deg, DirectionStyle.WORDS, lang)

    @Test fun wordsEnglish() {
        assertEquals("ahead", words(0f))
        assertEquals("ahead", words(-7f))
        assertEquals("slightly left", words(-8f))
        assertEquals("slightly right", words(20f))
        assertEquals("on your right", words(21f))
        assertEquals("on your left", words(-135f))
        assertEquals("behind you", words(136f))
        assertEquals("behind you", words(-180f))
    }

    @Test fun wordsKorean() {
        assertEquals("앞에", words(0f, Lang.KO))
        assertEquals("조금 왼쪽에", words(-8f, Lang.KO))
        assertEquals("조금 오른쪽에", words(20f, Lang.KO))
        assertEquals("오른쪽에", words(21f, Lang.KO))
        assertEquals("왼쪽에", words(-135f, Lang.KO))
        assertEquals("뒤에", words(136f, Lang.KO))
    }

    @Test fun clockHours() {
        assertEquals(12, Bearings.clockHour(0f))
        assertEquals(11, Bearings.clockHour(-30f))
        assertEquals(3, Bearings.clockHour(90f))
        assertEquals(6, Bearings.clockHour(180f))
        assertEquals(6, Bearings.clockHour(-180f))
        assertEquals(12, Bearings.clockHour(14f))
        assertEquals(1, Bearings.clockHour(16f))
    }

    @Test fun clockSentences() {
        assertEquals("at 11 o'clock", Bearings.say(-30f, DirectionStyle.CLOCK, Lang.EN))
        assertEquals("11시 방향에", Bearings.say(-30f, DirectionStyle.CLOCK, Lang.KO))
    }
}

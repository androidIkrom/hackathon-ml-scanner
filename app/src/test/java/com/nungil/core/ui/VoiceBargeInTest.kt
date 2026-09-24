package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceBargeInTest {
    private val saying = "Around you: 3 blue chairs in front; a black laptop on your right."

    @Test fun anyWordWhileTheAppIsQuietStopsAllSound() =
        assertEquals(BargeIn.STOP_ALL_SOUND, VoiceBargeIn.onPartial("find", appSaying = null))

    @Test fun noWordsMeansNoise() {
        assertEquals(BargeIn.KEEP_TALKING, VoiceBargeIn.onPartial("", appSaying = null))
        assertEquals(BargeIn.KEEP_TALKING, VoiceBargeIn.onPartial(" ... ", appSaying = saying))
    }

    @Test fun theAppHearingItselfKeepsTalking() =
        assertEquals(BargeIn.KEEP_TALKING, VoiceBargeIn.onPartial("3 blue chairs in", saying))

    @Test fun aWordTheAppIsNotSayingStopsIt() =
        assertEquals(BargeIn.STOP_ALL_SOUND, VoiceBargeIn.onPartial("stop", saying))

    @Test fun koreanSpacingDifferencesStillCountAsEcho() =
        assertEquals(BargeIn.KEEP_TALKING, VoiceBargeIn.onPartial("파란 의자 세개", "앞에 파란 의자 세 개가 있어요."))

    @Test fun koreanUserWordStopsTheApp() =
        assertEquals(BargeIn.STOP_ALL_SOUND, VoiceBargeIn.onPartial("멈춰", "앞에 파란 의자 세 개가 있어요."))

    @Test fun finalEchoIsIgnored() {
        assertTrue(VoiceBargeIn.isEcho("three blue chairs in front", "Around you: three blue chairs in front."))
        assertTrue(VoiceBargeIn.isEcho("Saved", "Saved."))
    }

    @Test fun shortCommandsAreNeverTakenForEcho() {
        assertFalse(VoiceBargeIn.isEcho("look around", "Say look around, or tap the first card."))
        assertFalse(VoiceBargeIn.isEcho("saved", "Say saved to teach me people and things."))
    }

    @Test fun nothingSaidMeansNoEcho() = assertFalse(VoiceBargeIn.isEcho("full scan", appSaid = null))

    @Test fun mostlyNewWordsAreTheUser() =
        assertFalse(VoiceBargeIn.isEcho("find my red bag please", "Around you: a red chair in front."))

    @Test fun echoShareFromMeasurement() {
        assertEquals(3, VoiceBargeIn.MIN_ECHO_WORDS)
        assertEquals(0.7, VoiceBargeIn.ECHO_SHARE, 0.0)
    }
}

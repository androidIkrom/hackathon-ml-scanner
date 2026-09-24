package com.nungil.core.voice

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTest {
    private val isCommand: (String) -> Boolean = { it.contains("saved") || it.contains("저장") || it.contains("scan") }

    private fun asleep(text: String) = WakeWord.decide(text, awake = false, isCommand = isCommand)
    private fun awake(text: String) = WakeWord.decide(text, awake = true, isCommand = isCommand)

    @Test fun theWordDependsOnTheLanguage() {
        assertEquals("Eye", WakeWord.word(Lang.EN))
        assertEquals("눈길", WakeWord.word(Lang.KO))
    }

    @Test fun eyeOrEyeStartWakes() {
        assertEquals(WakeResult.Wake, asleep("Eye"))
        assertEquals(WakeResult.Wake, asleep("eye start"))
        assertEquals(WakeResult.Wake, asleep("Eye, start."))
    }

    @Test fun theRecognizerOftenWritesEyeAsI() {
        assertEquals(WakeResult.Wake, asleep("I"))
        assertEquals(WakeResult.Wake, asleep("aye"))
        assertEquals(WakeResult.Wake, asleep("I start"))
    }

    @Test fun nungilWakesInKorean() {
        assertEquals(WakeResult.Wake, asleep("눈길"))
        assertEquals(WakeResult.Wake, asleep("눈길아"))
        assertEquals(WakeResult.Wake, asleep("눈 길 시작"))
        assertEquals(WakeResult.Wake, asleep("Nungil"))
    }

    @Test fun eyeStopSleeps() {
        assertEquals(WakeResult.Sleep, awake("eye stop"))
        assertEquals(WakeResult.Sleep, awake("I stop."))
        assertEquals(WakeResult.Sleep, awake("눈길 멈춰"))
        assertEquals(WakeResult.Sleep, awake("눈길아 그만"))
    }

    @Test fun asleepEverythingElseIsIgnored() {
        assertEquals(WakeResult.Ignore, asleep("saved"))
        assertEquals(WakeResult.Ignore, asleep("full scan"))
        assertEquals(WakeResult.Ignore, asleep("저장한 것"))
    }

    @Test fun ordinarySentencesStartingWithIDoNotWake() {
        assertEquals(WakeResult.Ignore, asleep("I think it is raining"))
        assertEquals(WakeResult.Ignore, asleep("I am hungry"))
    }

    @Test fun wakeWordWithACommandWakesAndRunsIt() {
        assertEquals(WakeResult.Command("saved", wake = true), asleep("Eye saved"))
        assertEquals(WakeResult.Command("저장한 것", wake = true), asleep("눈길아 저장한 것"))
    }

    @Test fun awakeCommandsNeedNoWakeWord() {
        assertEquals(WakeResult.Command("saved", wake = false), awake("saved"))
        assertEquals(WakeResult.Command("I think it is raining", wake = false), awake("I think it is raining"))
    }

    @Test fun awakeTheWakeWordIsStrippedFromACommand() =
        assertEquals(WakeResult.Command("full scan", wake = false), awake("eye, full scan"))

    @Test fun awakeWakeWordAloneJustConfirms() = assertEquals(WakeResult.Wake, awake("eye"))

    @Test fun wordsThatMerelyStartLikeTheWakeWordDoNotCount() {
        assertFalse(WakeWord.addressed("iPhone"))
        assertFalse(WakeWord.addressed("눈길이 미끄러워"))
        assertTrue(WakeWord.addressed("eye"))
        assertTrue(WakeWord.addressed("눈길아"))
    }

    @Test fun emptyTextIsIgnored() = assertEquals(WakeResult.Ignore, asleep("  "))
}

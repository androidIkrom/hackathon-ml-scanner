package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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

    @Test fun anAnswerIsTakenFromWordsStillBeingSaid() {
        val yesNo: (String) -> Boolean = { it.lowercase().trim() in setOf("yes", "no", "yes it is") }
        assertEquals("Yes", VoiceBargeIn.answerIn("Yes", null, yesNo))
        assertEquals("yes it is", VoiceBargeIn.answerIn("yes it is", "My green chair. Is that right?", yesNo))
        assertNull(VoiceBargeIn.answerIn("my green", null, yesNo))
        assertNull(VoiceBargeIn.answerIn("", null, yesNo))
    }

    @Test fun anAnswerAfterTheAppsOwnQuestionInTheSameBreath() {
        // The logs: the microphone heard the question and then the "yes", and gave no words at all 10 s later.
        val yesNo: (String) -> Boolean = { it.lowercase().trim() in setOf("yes", "no", "yes it is") }
        val asked = "It is black and oblong, about 80 by 60 centimetres, about 80 centimetres away. Is this it?"
        assertEquals("yes", VoiceBargeIn.answerIn("it is black and oblong about 80 by 60 cm about 80 cm away is this it yes", asked, yesNo))
        assertEquals("yes it is", VoiceBargeIn.answerIn("about 80 cm away is this it yes it is", asked, yesNo))
        assertNull(VoiceBargeIn.answerIn("it is black and oblong about 80 by 60 cm about 80 cm away is this it", asked, yesNo))
    }

    @Test fun theAppsOwnYesIsNotTheUsers() {
        val yesNo: (String) -> Boolean = { it.lowercase().trim() in setOf("yes", "no") }
        assertNull(VoiceBargeIn.answerIn("yes", "Yes", yesNo))
        // The end of the app's own sentence coming back through the microphone.
        assertNull(VoiceBargeIn.answerIn("say yes or no", "Is this it? Say yes or no.", yesNo))
        assertNull(VoiceBargeIn.answerIn("is this it say yes or no", "Is this it? Say yes or no.", yesNo))
    }

    @Test fun aCommandMadeOfTheAppsOwnWordsButOneIsItsEcho() {
        // The logs: "Hold the phone still." came back as "Glue the phone | Close the phone", and "close" went
        // Back in the middle of learning an item.
        assertTrue(VoiceBargeIn.mostlyEcho("Close the phone", "Hold the phone still."))
        assertTrue(VoiceBargeIn.mostlyEcho("Call the phone", "Great! Hold the phone still."))
        // Short commands, and words of the user's own, are not.
        assertFalse(VoiceBargeIn.mostlyEcho("go back", "Hold the phone still."))
        assertFalse(VoiceBargeIn.mostlyEcho("close", "Hold the phone still."))
        assertFalse(VoiceBargeIn.mostlyEcho("find my bag", "Hold the phone still."))
        assertFalse(VoiceBargeIn.mostlyEcho("close the phone", null))
    }

    @Test fun theStartOfTheAppsOwnSentenceIsNotTheUserTalking() {
        // The logs: "Is", from the app's own "Is this it?", kept a session that then heard nothing for 10 s.
        assertEquals(BargeIn.KEEP_TALKING, VoiceBargeIn.onPartial("Is", "It is black and oblong. Is this it?"))
        assertEquals(BargeIn.STOP_ALL_SOUND, VoiceBargeIn.onPartial("Yes it is", "It is black and oblong. Is this it?"))
    }
}

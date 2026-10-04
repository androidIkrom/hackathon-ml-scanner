package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapHelpOfferTest {
    @Test fun onceOnAScreenAgainOnTheNext() {
        val o = TapHelpOffer()
        o.onScreen("home")
        assertTrue(o.ask(canHear = true))
        assertFalse(o.ask(canHear = true)) // "no", or no answer: not again on this screen
        o.onScreen("walking")
        assertTrue(o.ask(canHear = true))
        o.onScreen("home") // back again: a new visit
        assertTrue(o.ask(canHear = true))
    }

    @Test fun notWhenTheAnswerCannotBeHeard() {
        // Asleep ("Eye stop"), or another question waits: a later tap on the same screen may still ask.
        val o = TapHelpOffer()
        o.onScreen("home")
        assertFalse(o.ask(canHear = false))
        assertTrue(o.ask(canHear = true))
    }

    @Test fun withTheVoiceGuideTheQuestionComesFirst() {
        // The voice guide named the screen on a tap on nothing; now it asks first, and names it after that.
        val o = TapHelpOffer()
        o.onScreen("home")
        assertEquals(TapHelpOffer.Tap.ASK, o.onTap(empty = true, voiceGuide = true, canHear = true))
        assertEquals(TapHelpOffer.Tap.DESCRIBE, o.onTap(empty = true, voiceGuide = true, canHear = true))
        assertEquals(TapHelpOffer.Tap.DESCRIBE, o.onTap(empty = false, voiceGuide = true, canHear = true))
    }

    @Test fun withoutTheVoiceGuideOnlyTheQuestion() {
        val o = TapHelpOffer()
        o.onScreen("home")
        assertEquals(TapHelpOffer.Tap.NOTHING, o.onTap(empty = false, voiceGuide = false, canHear = true))
        assertEquals(TapHelpOffer.Tap.ASK, o.onTap(empty = true, voiceGuide = false, canHear = true))
        assertEquals(TapHelpOffer.Tap.NOTHING, o.onTap(empty = true, voiceGuide = false, canHear = true))
    }

    @Test fun anAnswerThatCannotBeHeardLeavesTheVoiceGuideAsItWas() {
        val o = TapHelpOffer()
        o.onScreen("home")
        assertEquals(TapHelpOffer.Tap.DESCRIBE, o.onTap(empty = true, voiceGuide = true, canHear = false))
        assertEquals(TapHelpOffer.Tap.NOTHING, o.onTap(empty = true, voiceGuide = false, canHear = false))
    }

    @Test fun theQuestion() {
        assertEquals("Do you want instructions for this screen?", TapHelpOffer.question(Lang.EN))
        assertEquals("이 화면 사용법을 알려 드릴까요?", TapHelpOffer.question(Lang.KO))
    }
}

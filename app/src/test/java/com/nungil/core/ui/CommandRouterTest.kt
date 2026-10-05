package com.nungil.core.ui

import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {
    @Test fun globalCommandsNeverReachTheScreen() {
        assertTrue(CommandRouter.isGlobal(VoiceCommand.Repeat))
        assertTrue(CommandRouter.isGlobal(VoiceCommand.StopListening))
        assertTrue(CommandRouter.isGlobal(VoiceCommand.SetLanguage(Lang.KO)))
        assertTrue(CommandRouter.isGlobal(VoiceCommand.Learner(true)))
    }

    @Test fun screenCommandsGoToTheScreenFirst() {
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Start))
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Stop))
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Delete))
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Go(Dest.Home)))
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Unknown("Ali")))
    }

    @Test fun goOpensTheDestination() =
        assertEquals(Route.Open(Dest.Search("bag")), CommandRouter.route(VoiceCommand.Go(Dest.Search("bag"))))

    @Test fun readTextOpensTheReader() = assertEquals(Route.Open(Dest.Reader), CommandRouter.route(VoiceCommand.ReadText))

    @Test fun whatAndWhoOpenLiveScanAndSaySo() {
        val expected = Route.OpenAndSay(Dest.Scan(ScanMode.LIVE), Phrase.OPENING_LIVE_SCAN)
        assertEquals(expected, CommandRouter.route(VoiceCommand.WhatIsThis))
        assertEquals(expected, CommandRouter.route(VoiceCommand.WhoIsThis))
    }

    @Test fun unhandledScreenActionsSayNotHere() {
        for (c in listOf(VoiceCommand.Start, VoiceCommand.SwitchCamera(), VoiceCommand.Delete)) {
            assertEquals(Route.Say(Phrase.NOT_HERE), CommandRouter.route(c))
        }
    }

    @Test fun stopWithNoScreenStopsSpeech() = assertEquals(Route.StopSpeaking, CommandRouter.route(VoiceCommand.Stop))

    @Test fun unknownSaysNotUnderstood() =
        assertEquals(Route.Say(Phrase.NOT_UNDERSTOOD), CommandRouter.route(VoiceCommand.Unknown("Corazon")))

    @Test fun globalRoutes() {
        assertEquals(Route.GoBack, CommandRouter.route(VoiceCommand.Back))
        assertEquals(Route.RepeatLast, CommandRouter.route(VoiceCommand.Repeat))
        assertEquals(Route.StopListening, CommandRouter.route(VoiceCommand.StopListening))
        assertEquals(Route.SwitchLanguage(Lang.KO), CommandRouter.route(VoiceCommand.SetLanguage(Lang.KO)))
        assertEquals(Route.SetLearner(false), CommandRouter.route(VoiceCommand.Learner(false)))
        assertEquals(Route.SpeakHelp("search"), CommandRouter.route(VoiceCommand.Help("search")))
    }

    @Test fun languageChoiceTags() {
        assertEquals("", AppLanguage.tag(LanguageChoice.SYSTEM))
        assertEquals("en", AppLanguage.tag(LanguageChoice.ENGLISH))
        assertEquals("ko", AppLanguage.tag(LanguageChoice.KOREAN))
        assertEquals(LanguageChoice.SYSTEM, AppLanguage.choiceOf(null))
        assertEquals(LanguageChoice.SYSTEM, AppLanguage.choiceOf(""))
        assertEquals(LanguageChoice.KOREAN, AppLanguage.choiceOf("ko-KR"))
        assertEquals(LanguageChoice.ENGLISH, AppLanguage.choiceOf("en"))
        assertEquals(LanguageChoice.KOREAN, AppLanguage.forLang(Lang.KO))
    }
}

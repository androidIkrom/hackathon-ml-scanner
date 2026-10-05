package com.nungil.core.ui

import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand

/** What MainActivity does with a command that no screen handled. */
sealed interface Route {
    data class Open(val dest: Dest) : Route
    data object GoBack : Route
    data object RepeatLast : Route
    data class SpeakHelp(val topic: String?) : Route
    data class SetLearner(val on: Boolean) : Route
    data class SetClockDirections(val on: Boolean) : Route
    data object StopListening : Route
    data class SwitchLanguage(val lang: Lang) : Route
    data class OpenAndSay(val dest: Dest, val phrase: Phrase) : Route
    data class Say(val phrase: Phrase) : Route
    data object StopSpeaking : Route
}

/**
 * Voice-command routing. Global commands are handled by the shell before any screen sees them; all
 * others go to the current screen's VoiceHandler first and come here only if it returns false.
 */
object CommandRouter {
    fun isGlobal(command: VoiceCommand): Boolean =
        command == VoiceCommand.Repeat ||
            command == VoiceCommand.StopListening ||
            command is VoiceCommand.SetLanguage ||
            command is VoiceCommand.Learner ||
            command is VoiceCommand.ClockDirections

    fun route(command: VoiceCommand): Route = when (command) {
        is VoiceCommand.Go -> Route.Open(command.dest)
        VoiceCommand.Back -> Route.GoBack
        VoiceCommand.Repeat -> Route.RepeatLast
        is VoiceCommand.Help -> Route.SpeakHelp(command.topic)
        is VoiceCommand.Learner -> Route.SetLearner(command.on)
        is VoiceCommand.ClockDirections -> Route.SetClockDirections(command.on)
        VoiceCommand.StopListening -> Route.StopListening
        is VoiceCommand.SetLanguage -> Route.SwitchLanguage(command.lang)
        VoiceCommand.ReadText -> Route.Open(Dest.Reader)
        VoiceCommand.WhatIsThis, VoiceCommand.WhoIsThis ->
            Route.OpenAndSay(Dest.Scan(ScanMode.LIVE), Phrase.OPENING_LIVE_SCAN)
        VoiceCommand.Stop -> Route.StopSpeaking
        VoiceCommand.Start, is VoiceCommand.SwitchCamera, VoiceCommand.Delete -> Route.Say(Phrase.NOT_HERE)
        is VoiceCommand.Unknown -> Route.Say(Phrase.NOT_UNDERSTOOD)
    }
}

enum class LanguageChoice { SYSTEM, ENGLISH, KOREAN }

/** Maps the Settings language choice to AppCompat per-app locale tags and back. */
object AppLanguage {
    fun tag(choice: LanguageChoice): String = when (choice) {
        LanguageChoice.SYSTEM -> ""
        LanguageChoice.ENGLISH -> "en"
        LanguageChoice.KOREAN -> "ko"
    }

    /** [appLocaleTag] is the first per-app locale, or null/empty when the app follows the phone. */
    fun choiceOf(appLocaleTag: String?): LanguageChoice = when {
        appLocaleTag.isNullOrEmpty() -> LanguageChoice.SYSTEM
        Lang.fromTag(appLocaleTag) == Lang.KO -> LanguageChoice.KOREAN
        else -> LanguageChoice.ENGLISH
    }

    fun forLang(lang: Lang): LanguageChoice = if (lang == Lang.KO) LanguageChoice.KOREAN else LanguageChoice.ENGLISH
}

package com.nungil.contract

/** Result of parsing one spoken phrase (VoiceCommandParser, owned by I). */
sealed interface VoiceCommand {
    /** Open a screen: "full scan", "saved", "find my bag", "add person Ali", "걷기 모드". */
    data class Go(val dest: Dest) : VoiceCommand
    data object Back : VoiceCommand
    /** Press the current screen's main button. */
    data object Start : VoiceCommand
    data object Stop : VoiceCommand
    /** [to] is the camera asked for ("front camera"); null: the other one ("switch camera"). */
    data class SwitchCamera(val to: Facing? = null) : VoiceCommand
    /** Say the last spoken sentence again. */
    data object Repeat : VoiceCommand
    data object ReadText : VoiceCommand
    data object Delete : VoiceCommand
    data object WhoIsThis : VoiceCommand
    data object WhatIsThis : VoiceCommand
    /** [topic] null = help for the current screen. */
    data class Help(val topic: String?) : VoiceCommand
    data class Learner(val on: Boolean) : VoiceCommand
    data object StopListening : VoiceCommand
    data class SetLanguage(val lang: Lang) : VoiceCommand
    /** Not a command. While a screen waits for words (askForWords), this text is delivered to it. */
    data class Unknown(val text: String) : VoiceCommand
}

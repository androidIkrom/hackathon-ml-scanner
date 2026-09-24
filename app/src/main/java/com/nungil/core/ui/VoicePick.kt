package com.nungil.core.ui

import com.nungil.contract.Lang

enum class LangSupport { AVAILABLE, MISSING_DATA, NOT_SUPPORTED }

/** The voice actually used, and a sentence to say when it is not the one the user chose. */
data class VoiceChoice(val speak: Lang, val notice: String?)

/** Picks the TTS voice. A missing Korean voice falls back to English and says so, never to silence. */
object VoicePick {
    // TextToSpeech.isLanguageAvailable() results, copied so this stays pure Kotlin.
    private const val LANG_AVAILABLE = 0
    private const val LANG_MISSING_DATA = -1

    const val KOREAN_VOICE_MISSING =
        "The Korean voice is not installed, so I will speak English. " +
            "To hear Korean, install the Korean voice in the phone's text-to-speech settings."

    fun support(result: Int): LangSupport = when {
        result >= LANG_AVAILABLE -> LangSupport.AVAILABLE
        result == LANG_MISSING_DATA -> LangSupport.MISSING_DATA
        else -> LangSupport.NOT_SUPPORTED
    }

    fun choose(wanted: Lang, support: LangSupport): VoiceChoice =
        if (support == LangSupport.AVAILABLE || wanted == Lang.EN) {
            VoiceChoice(wanted, null)
        } else {
            VoiceChoice(Lang.EN, KOREAN_VOICE_MISSING)
        }
}

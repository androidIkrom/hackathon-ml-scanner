package com.nungil.core.voice

import com.nungil.contract.Lang

sealed interface WakeResult {
    /** Asleep and not addressed: do nothing. */
    data object Ignore : WakeResult

    /** "Eye", "Eye start", "눈길", "눈길아 시작": start taking commands. */
    data object Wake : WakeResult

    /** "Eye stop", "눈길 멈춰": stop taking commands until the next wake word. */
    data object Sleep : WakeResult

    /** Run [text] as a command. [wake] is true when this phrase also woke the app ("Eye, saved"). */
    data class Command(val text: String, val wake: Boolean) : WakeResult
}

/**
 * The wake word, like a smart speaker: the microphone always listens, but commands run only between
 * "Eye" (Korean: "눈길") and "Eye stop". Both words are accepted in both languages, so the wake word
 * still works when Korean recognition falls back to English. The recognizer often writes "Eye" as
 * "I", so a sentence that merely starts with "I" wakes the app only when the rest is a command.
 */
object WakeWord {
    private val ENGLISH = Regex("""^\s*(eye|eyes|aye|ai|i|nungil)\b[\s,.!?]*(.*)$""", RegexOption.IGNORE_CASE)

    /** 눈길, 눈 길, and the vocative 눈길아 / 눈길야, but not 눈길이 ("the snowy path"). */
    private val KOREAN = Regex("""^\s*눈\s*길(아|야)?(?![가-힣])[\s,.!?]*(.*)$""")

    private val START = setOf("start", "wake up", "시작", "시작해", "시작해 줘", "일어나")
    private val STOP = setOf("stop", "still", "sleep", "go to sleep", "멈춰", "정지", "그만", "그만해", "스톱", "중지", "멈춤")

    /** The word to tell the user about. */
    fun word(lang: Lang): String = if (lang == Lang.KO) "눈길" else "Eye"

    /** True when [text] starts with the wake word. */
    fun addressed(text: String): Boolean = rest(text) != null

    fun decide(text: String, awake: Boolean, isCommand: (String) -> Boolean): WakeResult {
        if (text.isBlank()) return WakeResult.Ignore
        val rest = rest(text)
        if (rest != null) {
            val key = rest.lowercase().trim().trimEnd('.', '!', '?', ',').trim()
            if (key.isEmpty() || key in START) return WakeResult.Wake
            if (key in STOP) return WakeResult.Sleep
            if (isCommand(rest)) return WakeResult.Command(rest, wake = !awake)
        }
        return if (awake) WakeResult.Command(text.trim(), wake = false) else WakeResult.Ignore
    }

    private fun rest(text: String): String? =
        (ENGLISH.find(text) ?: KOREAN.find(text))?.groupValues?.get(2)?.trim()
}

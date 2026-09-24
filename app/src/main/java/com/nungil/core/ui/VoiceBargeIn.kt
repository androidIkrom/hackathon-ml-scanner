package com.nungil.core.ui

/** What to do with the app's own sound when the microphone picks up words. */
enum class BargeIn { KEEP_TALKING, STOP_ALL_SOUND }

/**
 * Tells the user's voice apart from the app's own voice coming back through the microphone: the
 * phone's speaker sits next to its microphone, so everything the app says is also "heard".
 * Words are compared against the app's sentence with spaces removed, because the recognizer spaces
 * Korean differently ("세개" for "세 개").
 */
object VoiceBargeIn {
    const val MIN_ECHO_WORDS = 3
    const val ECHO_SHARE = 0.7

    /** A partial result: any word the app is not saying right now means a person is talking. */
    fun onPartial(heard: String, appSaying: String?): BargeIn {
        val words = words(heard)
        if (words.isEmpty()) return BargeIn.KEEP_TALKING
        if (appSaying == null) return BargeIn.STOP_ALL_SOUND
        val said = joined(appSaying)
        return if (words.any { it !in said }) BargeIn.STOP_ALL_SOUND else BargeIn.KEEP_TALKING
    }

    /**
     * A final result that is only the app's own sentence coming back. Short phrases (under
     * [MIN_ECHO_WORDS] words) count as echo only when they are the whole sentence, so a one-word
     * command is never thrown away.
     */
    fun isEcho(heard: String, appSaid: String?): Boolean {
        if (appSaid == null) return false
        val words = words(heard)
        if (words.isEmpty()) return false
        if (words.joinToString("") == joined(appSaid)) return true
        if (words.size < MIN_ECHO_WORDS) return false
        val said = joined(appSaid)
        return words.count { it in said } >= words.size * ECHO_SHARE
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun joined(text: String): String = words(text).joinToString("")
}

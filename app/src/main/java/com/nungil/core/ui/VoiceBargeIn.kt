package com.nungil.core.ui

/**
 * What to do with the app's own sound when the microphone picks up words. UNSURE: one word that the app is not
 * saying, alone, is the user's or the start of the app's own sentence misheard; what comes next decides.
 */
enum class BargeIn { KEEP_TALKING, STOP_ALL_SOUND, UNSURE }

/**
 * Tells the user's voice apart from the app's own voice coming back through the microphone: the
 * phone's speaker sits next to its microphone, so everything the app says is also "heard".
 * Words are compared against the app's sentence with spaces removed, because the recognizer spaces
 * Korean differently ("세개" for "세 개").
 */
object VoiceBargeIn {
    const val MIN_ECHO_WORDS = 3
    const val ECHO_SHARE = 0.7

    /** The most words of an answer taken from the end of what was heard ("yes it is"). */
    const val MAX_ANSWER_WORDS = 3

    /** A partial result: words the app is not saying right now mean a person is talking. */
    fun onPartial(heard: String, appSaying: String?): BargeIn {
        val words = words(heard)
        if (words.isEmpty()) return BargeIn.KEEP_TALKING
        if (appSaying == null) return BargeIn.STOP_ALL_SOUND
        val said = joined(appSaying)
        val others = words.count { it !in said }
        // The recognizer mishears the app too: "Hold the phone still." came back as "Call the phone", "Glue
        // the", and "Move the phone a little" as "Close the phone a little". Each silenced the app for the 8 to
        // 10 s that session lasted (the logs). All words but one its own is its own sentence; one word alone
        // may be either, so it waits for the next.
        return when {
            others == 0 -> BargeIn.KEEP_TALKING
            words.size == 1 -> BargeIn.UNSURE
            others == 1 -> BargeIn.KEEP_TALKING
            else -> BargeIn.STOP_ALL_SOUND
        }
    }

    /** Whether [heard] has a word the app did not say: the user may be talking (any word when the app is quiet). */
    fun hasOtherWords(heard: String, appSaid: String?): Boolean {
        val words = words(heard)
        if (appSaid == null) return words.isNotEmpty()
        val said = joined(appSaid)
        return words.any { it !in said }
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

    /**
     * The answer a screen waits for ("yes", "no"), found in words that are still being said: all of [heard],
     * or its last words when the app's own question came first in the same breath. Null when there is none,
     * or when it is only the app's own voice: [heard] is its sentence, or the words are the end of it.
     * In a room with other sound a phrase ends 6 to 10 s after the word, or with no words at all (the logs).
     */
    fun answerIn(heard: String, appSaid: String?, accepts: (String) -> Boolean): String? {
        val text = heard.trim()
        if (text.isEmpty()) return null
        if (accepts(text)) return text.takeUnless { isEcho(it, appSaid) || endsWhatWasSaid(it, appSaid) }
        // Words before the answer can only be the app's own, so only when it has just spoken.
        if (appSaid == null) return null
        val spoken = text.split(' ').filter { it.isNotEmpty() }
        for (n in minOf(MAX_ANSWER_WORDS, spoken.size - 1) downTo 1) {
            val tail = spoken.takeLast(n).joinToString(" ")
            if (accepts(tail) && !endsWhatWasSaid(tail, appSaid)) return tail
        }
        return null
    }

    /**
     * A phrase of [MIN_ECHO_WORDS] words or more of which all but one are the app's own: its sentence with one
     * word misheard. For commands only: "Hold the phone still." came back as "Close the phone" and went Back
     * (the logs). An answer may share words with the question ("Yes it is" to "Is this it?").
     */
    fun mostlyEcho(heard: String, appSaid: String?): Boolean {
        if (appSaid == null) return false
        val words = words(heard)
        if (words.size < MIN_ECHO_WORDS) return false
        val said = joined(appSaid)
        return words.count { it in said } >= words.size - 1
    }

    private fun endsWhatWasSaid(text: String, appSaid: String?): Boolean =
        appSaid != null && joined(text).isNotEmpty() && joined(appSaid).endsWith(joined(text))

    /**
     * Number words as digits: the app says "3 blue bottles" and the microphone writes "Three", so both sides are
     * compared as "3". "Three" taken for the user silenced the app, and the next thing found was lost (the logs).
     */
    private val NUMBERS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
    ).withIndex().associate { (n, word) -> word to n.toString() }

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map { NUMBERS[it] ?: it }

    private fun joined(text: String): String = words(text).joinToString("")
}

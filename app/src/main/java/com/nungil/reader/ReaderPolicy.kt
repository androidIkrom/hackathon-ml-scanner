package com.nungil.reader

import com.nungil.contract.Lang

/** When to read and what to say in the reader (pure Kotlin, unit-tested). Use from one thread. */
class ReaderPolicy(private val repeatMs: Long = REPEAT_MS) {
    private val spokenAt = HashMap<String, Long>()

    /** The same text is never spoken twice within [repeatMs]. */
    fun shouldSpeak(text: String, nowMs: Long): Boolean {
        val last = spokenAt[text]
        if (last != null && nowMs - last < repeatMs) return false
        spokenAt[text] = nowMs
        return true
    }

    companion object {
        const val MAX_CHARS = 80
        const val MIN_LINE_CHARS = 3
        const val READ_EVERY_MS = 1_500L
        const val REPEAT_MS = 10_000L

        fun truncateCode(text: String): String = if (text.length > MAX_CHARS) text.take(MAX_CHARS) else text

        /** The longest line of at least [MIN_LINE_CHARS] characters, or null. */
        fun longestLine(lines: List<String>): String? =
            lines.map { it.trim() }.filter { it.length >= MIN_LINE_CHARS }.maxByOrNull { it.length }

        fun codePhrase(text: String, lang: Lang): String = when (lang) {
            Lang.EN -> "Code: ${truncateCode(text)}"
            Lang.KO -> "코드: ${truncateCode(text)}"
        }
    }
}

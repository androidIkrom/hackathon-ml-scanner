package com.nungil.reader

import com.nungil.contract.Lang
import kotlin.math.abs

/**
 * One recognised line of text. [height], [centerX] and [centerY] are fractions of the frame (0..1); [confidence]
 * is ML Kit's line confidence (0..1).
 */
data class SeenLine(val text: String, val confidence: Float, val height: Float, val centerX: Float, val centerY: Float)

/** When to read and what to say in the reader (pure Kotlin, unit-tested). Use from one thread. */
class ReaderPolicy(private val repeatMs: Long = REPEAT_MS) {
    private val spokenAt = HashMap<String, Long>()
    private var lastPick: String? = null

    /**
     * True when [text] is also what the previous read picked, so a line is spoken only once it has been seen in two
     * reads in a row; a one-frame misreading is never spoken. Call once per read, with null when nothing was picked.
     */
    fun steady(text: String?): Boolean {
        val same = text != null && text == lastPick
        lastPick = text
        return same
    }

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

        /** Lines ML Kit is less sure of are background noise or misreadings. */
        const val MIN_CONFIDENCE = 0.6f

        /** Letters and digits must be at least this share of the non-space characters ("|||", "~~" are not text). */
        const val MIN_LETTER_SHARE = 0.6f

        /** Text lower than this share of the frame height is small print in the background, not a sign. */
        const val MIN_HEIGHT = 0.03f

        /** Whether one line is worth reading at all. */
        fun readable(line: SeenLine): Boolean {
            val text = line.text.trim()
            if (text.length < MIN_LINE_CHARS || line.confidence < MIN_CONFIDENCE || line.height < MIN_HEIGHT) return false
            val chars = text.filterNot { it.isWhitespace() }
            return chars.count { it.isLetterOrDigit() } >= chars.length * MIN_LETTER_SHARE
        }

        /** The line to read: the biggest readable text (signs are big), the one nearest the middle on a tie. */
        fun pickLine(lines: List<SeenLine>): String? {
            val biggestThenMiddle = compareBy<SeenLine> { it.height }
                .thenByDescending { abs(it.centerX - 0.5f) + abs(it.centerY - 0.5f) }
            return lines.filter(::readable).maxWithOrNull(biggestThenMiddle)?.text?.trim()
        }

        fun codePhrase(text: String, lang: Lang): String = when (lang) {
            Lang.EN -> "Code: ${truncateCode(text)}"
            Lang.KO -> "코드: ${truncateCode(text)}"
        }
    }
}

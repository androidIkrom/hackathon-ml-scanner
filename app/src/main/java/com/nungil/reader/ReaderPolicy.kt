package com.nungil.reader

import com.nungil.contract.Lang
import com.nungil.core.search.SearchResolver
import com.nungil.core.walk.RoutePhrases
import kotlin.math.abs

/**
 * One recognised line of text. [height], [centerX] and [centerY] are fractions of the frame (0..1); [confidence]
 * is ML Kit's line confidence (0..1).
 */
data class SeenLine(val text: String, val confidence: Float, val height: Float, val centerX: Float, val centerY: Float)

/**
 * When to read and what to say in the reader (pure Kotlin, unit-tested). Text in view is first offered ([found]:
 * "Text found, titled …" / "beginning with …"), and read in full only when the user says so ([accept]). Codes are
 * read at once ([shouldSpeak]). Use from one thread.
 */
class ReaderPolicy(private val repeatMs: Long = REPEAT_MS, private val offerAgainMs: Long = OFFER_AGAIN_MS) {
    /** Text found in view, its lines top to bottom. */
    class Offer(val lines: List<SeenLine>) {
        val text: String get() = sentence(lines.map { it.text.trim() })

        /** What to say when it is found: its title (lines much bigger than the rest), else its first words. */
        fun phrase(lang: Lang): String {
            val title = title(lines)
            return when {
                title != null && lang == Lang.KO -> "글자를 찾았어요. 제목: '$title'. 들으려면 '읽어'라고 말해 주세요."
                title != null -> "Text found, titled '$title'. Say read to hear it."
                lang == Lang.KO -> "글자를 찾았어요. 시작: '${beginning(lines)}'. 들으려면 '읽어'라고 말해 주세요."
                else -> "Text found, beginning with '${beginning(lines)}'. Say read to hear it."
            }
        }
    }

    private val spokenAt = HashMap<String, Long>()
    private var lastRead: List<String> = emptyList()
    private var waiting: Offer? = null
    private val offeredAt = ArrayList<Pair<List<String>, Long>>()

    /** An offer waits for the user's answer. */
    val pending: Boolean get() = waiting != null

    /**
     * Text to offer from one read's [lines], or null: only lines also seen in the read before (a one-frame
     * misreading is never offered), and not the text offered last within [offerAgainMs], nor anything while an
     * offer waits (the waiting one takes the fuller reading of the same text). Call once per read.
     */
    fun found(lines: List<SeenLine>, nowMs: Long): Offer? {
        val now = inOrder(lines)
        val steady = now.filter { line -> lastRead.any { sameLine(it, line.text) } }
        lastRead = now.map { it.text }
        if (steady.isEmpty()) return null
        val texts = steady.map { it.text }
        waiting?.let { w ->
            if (sameText(w.lines.map { it.text }, texts) && steady.size >= w.lines.size) waiting = Offer(steady)
            return null
        }
        offeredAt.removeAll { nowMs - it.second >= offerAgainMs }
        if (offeredAt.any { sameText(it.first, texts) }) return null
        offeredAt += texts to nowMs
        return Offer(steady).also { waiting = it }
    }

    /** "Read": the waiting offer's whole text, or null when nothing waits. */
    fun accept(): String? = waiting?.text.also { waiting = null }

    /** "No", or no answer: the offer is dropped; the same text is not offered again for a while. */
    fun decline() {
        waiting = null
    }

    /** The same code is never spoken twice within [repeatMs]. */
    fun shouldSpeak(text: String, nowMs: Long): Boolean {
        spokenAt.values.removeAll { nowMs - it >= repeatMs }
        if (spokenAt.keys.any { sameLine(it, text) }) return false
        spokenAt[text] = nowMs
        return true
    }

    companion object {
        const val MAX_CHARS = 80
        const val MIN_LINE_CHARS = 3
        /** Reads as soon as one is done, at most this often: a line needs two reads, so it is said in about 1 s. */
        const val READ_EVERY_MS = 600L
        const val REPEAT_MS = 10_000L

        /** Two reads of one line agree on at least this share of their letters (OCR changes a few each time). */
        const val SAME_LINE_SHARE = 0.8f

        /**
         * Whether [a] and [b] are one line read twice: OCR never gave a line twice letter for letter
         * ("SeenLine(Line.text" / "SeenLine (lLine.text"), so nothing was ever spoken (the logs, 2026-10-11).
         * Case and spaces do not count. Lines with other numbers are other lines ("Step 1" / "Step 2" of a list).
         */
        fun sameLine(a: String, b: String): Boolean {
            val x = a.lowercase().filterNot { it.isWhitespace() }
            val y = b.lowercase().filterNot { it.isWhitespace() }
            val longest = maxOf(x.length, y.length)
            if (longest == 0 || x.filter { it.isDigit() } != y.filter { it.isDigit() }) return false
            return SearchResolver.distance(x, y) <= longest * (1 - SAME_LINE_SHARE)
        }

        fun truncateCode(text: String): String = if (text.length > MAX_CHARS) text.take(MAX_CHARS) else text

        /** Lines ML Kit is less sure of are background noise or misreadings. */
        const val MIN_CONFIDENCE = 0.6f

        /** Letters and digits must be at least this share of the non-space characters ("|||", "~~" are not text). */
        const val MIN_LETTER_SHARE = 0.6f

        /**
         * Text lower than this share of the frame height is too small to read well. It was 0.03: lines of a page
         * at reading distance are 0.028-0.033 high, so they were read one moment and dropped the next (replay
         * test, 2026-10-12). Small print next to big text is left out by [RELATIVE_MIN_HEIGHT] instead.
         */
        const val MIN_HEIGHT = 0.015f

        /** Whether one line is worth reading at all. */
        fun readable(line: SeenLine): Boolean {
            val text = line.text.trim()
            if (text.length < MIN_LINE_CHARS || line.confidence < MIN_CONFIDENCE || line.height < MIN_HEIGHT) return false
            val chars = text.filterNot { it.isWhitespace() }
            return chars.count { it.isLetterOrDigit() } >= chars.length * MIN_LETTER_SHARE
        }

        /** How long a text offered is not offered again (read, refused or not answered). */
        const val OFFER_AGAIN_MS = 30_000L

        /** A line this much bigger than the middle-sized one is a title ("EMERGENCY EXIT" over small print). */
        const val TITLE_SCALE = 1.4f
        const val MAX_TITLE_LINES = 3

        /** The words of the first line said when there is no title. */
        const val BEGINNING_WORDS = 6

        /** Readable lines top to bottom, and left to right on one row. */
        fun inOrder(lines: List<SeenLine>): List<SeenLine> {
            val rows = mutableListOf<MutableList<SeenLine>>()
            for (line in lines.filter(::readable).sortedBy { it.centerY }) {
                val row = rows.lastOrNull()
                if (row != null && abs(line.centerY - row.first().centerY) < row.first().height / 2) row += line else rows += mutableListOf(line)
            }
            return rows.flatMap { row -> row.sortedBy { it.centerX } }
        }

        /** Two readings of one text: at least half the lines of the shorter one are in the other ([sameLine]). */
        fun sameText(a: List<String>, b: List<String>): Boolean {
            val (few, many) = if (a.size <= b.size) a to b else b to a
            if (few.isEmpty()) return false
            return few.count { line -> many.any { sameLine(it, line) } } * 2 >= few.size
        }

        /** The lines much bigger than the middle-sized one, when there are a few of them; else null. */
        fun title(lines: List<SeenLine>): String? {
            if (lines.size < 2) return null
            // The lower middle: with two big lines over two small ones the small ones are the body text.
            val middle = lines.map { it.height }.sorted()[(lines.size - 1) / 2]
            val big = lines.filter { it.height >= middle * TITLE_SCALE }
            if (big.isEmpty() || big.size > MAX_TITLE_LINES) return null
            return big.joinToString(" ") { it.text.trim() }
        }

        /** The first [BEGINNING_WORDS] words of the first line, with "…" when it goes on. */
        fun beginning(lines: List<SeenLine>): String {
            val words = lines.firstOrNull()?.text?.trim()?.split(Regex("\\s+")).orEmpty()
            val head = words.take(BEGINNING_WORDS).joinToString(" ")
            return if (words.size > BEGINNING_WORDS) "$head…" else head
        }

        /** The user's answer to an offer: true to read it, false not to, null when it is no answer. */
        fun answer(text: String): Boolean? {
            val t = text.trim().lowercase()
            return when {
                Regex("\\bread\\b").containsMatchIn(t) || t.contains("읽") -> true
                t == "skip" || t.startsWith("skip ") -> false
                RoutePhrases.isYes(t) -> true
                RoutePhrases.isNo(t) -> false
                else -> null
            }
        }

        /**
         * Lines as one thing to say: a line ending in a word gets a full stop, so the voice pauses between lines;
         * one ending in a comma or other mark keeps it ("seen twice," went on as "seen twice,.").
         */
        fun sentence(lines: List<String>): String =
            lines.joinToString(" ") { if (it.last().isLetterOrDigit()) "$it." else it }

        /** "No" to an offer. */
        fun skipped(lang: Lang): String = if (lang == Lang.KO) "알겠어요." else "Okay."

        /** "Read" with nothing offered. */
        fun nothingFound(lang: Lang): String =
            if (lang == Lang.KO) "아직 글자를 찾지 못했어요. 글자에 카메라를 향해 주세요." else "No text found yet. Point the camera at some text."

        fun codePhrase(text: String, lang: Lang): String = when (lang) {
            Lang.EN -> "Code: ${truncateCode(text)}"
            Lang.KO -> "코드: ${truncateCode(text)}"
        }
    }
}

package com.nungil.reader

import com.nungil.contract.Lang
import com.nungil.core.search.SearchResolver
import com.nungil.core.walk.RoutePhrases
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One recognised line of text. [height], [centerX] and [centerY] are fractions of the frame (0..1); [confidence]
 * is ML Kit's line confidence (0..1).
 */
data class SeenLine(
    val text: String, val confidence: Float, val height: Float, val centerX: Float, val centerY: Float,
    /** Fraction of the frame width; 0 when not known. */
    val width: Float = 0f,
    /** ML Kit's block (a paragraph, a piece of a column) the line is in; -1 when not known. */
    val block: Int = -1,
    /** The frame's height in pixels, for the line's height in pixels. */
    val frameHeight: Int = 640,
)

/**
 * When to read and what to say in the reader (pure Kotlin, unit-tested). Text in view is first offered ([found]:
 * "Text found, titled …" / "beginning with …"), and read in full only when the user says so ([accept]). Codes are
 * read at once ([shouldSpeak]). Use from one thread.
 */
class ReaderPolicy(private val repeatMs: Long = REPEAT_MS, private val offerAgainMs: Long = OFFER_AGAIN_MS) {
    /** One line as ML Kit gives it: its corners in pixels, clockwise from the top-left ([xs], [ys], four each). */
    class RawLine(val text: String, val confidence: Float, val xs: FloatArray, val ys: FloatArray, val block: Int = -1)
    /** Text found in view, its lines top to bottom. */
    class Offer(val lines: List<SeenLine>) {
        /**
         * Each line as one thing to say, so the reading can stop and go on line by line. Pieces of one line (ML Kit
         * cut "Prices start at" from "dollars for a cup of tea" round a big word) are one sentence, with no full stop
         * and no pause between them.
         */
        val sentences: List<String> get() = rows(lines).map { row -> sentence(listOf(joinPieces(row.map { it.text.trim() }))) }
        val text: String get() = sentences.joinToString(" ")

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
    private var waitingSince = 0L

    /** Until then the text accepted is being read: nothing new is offered on top of it. */
    private var quietUntil = 0L
    private val offeredAt = ArrayList<Pair<List<String>, Long>>()

    /** An offer waits for the user's answer. */
    val pending: Boolean get() = waiting != null

    /**
     * Text to offer from one read's [lines], or null: only lines also seen in the read before (a one-frame
     * misreading is never offered), and not the text offered last within [offerAgainMs], nor anything while an
     * offer waits for its answer (up to [ANSWER_MS]; the waiting one gathers the lines of each new reading of the
     * same text, [merge]) or while accepted text is being read. Five offers in a minute queued up behind each other (the logs).
     * Call once per read.
     */
    fun found(lines: List<SeenLine>, nowMs: Long): Offer? {
        val now = inOrder(lines)
        val steady = now.filter { line -> lastRead.any { sameLine(it, line.text) } }
        lastRead = now.map { it.text }
        if (waiting != null && nowMs - waitingSince >= ANSWER_MS) waiting = null
        if (steady.isEmpty() || nowMs < quietUntil) return null
        val texts = steady.map { it.text }
        waiting?.let { w ->
            if (sameText(w.lines.map { it.text }, texts)) waiting = Offer(merge(w.lines, steady))
            return null
        }
        offeredAt.removeAll { nowMs - it.second >= offerAgainMs }
        if (offeredAt.any { sameText(it.first, texts) }) return null
        offeredAt += texts to nowMs
        waitingSince = nowMs
        return Offer(steady).also { waiting = it }
    }

    /** "Read" at [nowMs]: the waiting offer's lines to say, or null when nothing waits. */
    fun accept(nowMs: Long): List<String>? {
        val lines = waiting?.sentences ?: return null
        waiting = null
        reading(nowMs, lines.sumOf { it.length })
        return lines
    }

    /** [chars] of text are being read from [nowMs]: nothing new is offered on top of them. */
    fun reading(nowMs: Long, chars: Int) {
        quietUntil = nowMs + chars * MS_PER_CHAR
    }

    /** "Another text": the offer and the reading are dropped, and other text in view is offered at once. */
    fun another() {
        waiting = null
        quietUntil = 0L
    }

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
         * Case and spaces do not count. Lines with other numbers are other lines ("Step 1: …" / "Step 2: …" of a list).
         * A lone character at either end is left out, and an O among digits is a 0: the ornaments beside "FIELD
         * MUSEUM" on a plaque came as "O", "6" or "9" in turn, and "100000" as "10000O", so the line was never
         * read in two frames alike and never read (real photo, 2026-10-13).
         */
        fun sameLine(a: String, b: String): Boolean {
            val x = core(a)
            val y = core(b)
            val longest = maxOf(x.length, y.length)
            if (longest == 0 || x.filter { it.isDigit() } != y.filter { it.isDigit() }) return false
            return SearchResolver.distance(x, y) <= longest * (1 - SAME_LINE_SHARE)
        }

        /** [text] as sameLine compares it: lower case, no spaces, no lone character at either end, O among digits 0. */
        private fun core(text: String): String {
            var words = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size > 1 && words.first().length == 1) words = words.drop(1)
            if (words.size > 1 && words.last().length == 1) words = words.dropLast(1)
            return words.joinToString("") { w -> if (w.any { it.isDigit() }) w.replace('o', '0') else w }
        }

        fun truncateCode(text: String): String = if (text.length > MAX_CHARS) text.take(MAX_CHARS) else text

        /**
         * Lines ML Kit is less sure of are background noise or misreadings. It was 0.6: small print in a camera-like
         * frame came at 0.57 and was left out (replay, 2026-10-13).
         */
        const val MIN_CONFIDENCE = 0.5f

        /** Letters and digits must be at least this share of the non-space characters ("|||", "~~" are not text). */
        const val MIN_LETTER_SHARE = 0.6f

        /**
         * Text lower than this many pixels is too small to read well. It was a share of the frame (0.03, then
         * 0.015 of 640 px): lines of a page at reading distance are 18-21 px, and in the bigger frame the reader
         * now asks for, the text of a laptop screen is 8-12 px (replay, 2026-10-12 and 2026-10-13).
         */
        const val MIN_HEIGHT_PX = 8f

        /** Whether one line is worth reading at all. */
        fun readable(line: SeenLine): Boolean {
            val text = line.text.trim()
            if (text.length < MIN_LINE_CHARS || line.confidence < MIN_CONFIDENCE || line.height * line.frameHeight < MIN_HEIGHT_PX) return false
            val chars = text.filterNot { it.isWhitespace() }
            return chars.count { it.isLetterOrDigit() } >= chars.length * MIN_LETTER_SHARE
        }

        /** How long a text offered is not offered again (read, refused or not answered). */
        const val OFFER_AGAIN_MS = 30_000L

        /** A line this much bigger than the middle-sized one is a title ("EMERGENCY EXIT" over small print). */
        const val TITLE_SCALE = 1.4f
        const val MAX_TITLE_LINES = 3

        /** A title is short: a long big line was a chat message, not a heading (the logs). */
        const val MAX_TITLE_LINE_WORDS = 5
        const val MAX_TITLE_WORDS = 8

        /** How long an offer waits for "read". */
        const val ANSWER_MS = 20_000L

        /** About how long the voice takes per character, so nothing is offered while a text is being read. */
        const val MS_PER_CHAR = 70L

        /** "Yes read it please", "read another text please". */
        const val MAX_ANSWER_WORDS = 4

        private fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotEmpty() }

        /** The words of the first line said when there is no title. */
        const val BEGINNING_WORDS = 6

        /**
         * The lines of a [width] x [height] frame as on a page held straight: each line's height measured along its
         * own slant, and its centre turned back by the page's slant (the middle one of the lines'). A phone held 7°
         * crooked made the lines' upright boxes taller than the gaps between them, and lines were read out of order
         * ("Tuesday" before "Monday", replay 2026-10-13).
         */
        fun straighten(lines: List<RawLine>, width: Float, height: Float): List<SeenLine> {
            if (lines.isEmpty()) return emptyList()
            val slant = lines.map { atan2(it.ys[1] - it.ys[0], it.xs[1] - it.xs[0]) }.sorted()[lines.size / 2]
            val c = cos(-slant)
            val s = sin(-slant)
            return lines.map { l ->
                val tall = (hypot(l.xs[3] - l.xs[0], l.ys[3] - l.ys[0]) + hypot(l.xs[2] - l.xs[1], l.ys[2] - l.ys[1])) / 2
                val wide = (hypot(l.xs[1] - l.xs[0], l.ys[1] - l.ys[0]) + hypot(l.xs[2] - l.xs[3], l.ys[2] - l.ys[3])) / 2
                val dx = l.xs.average().toFloat() - width / 2
                val dy = l.ys.average().toFloat() - height / 2
                SeenLine(
                    l.text, l.confidence, tall / height, (width / 2 + dx * c - dy * s) / width, (height / 2 + dx * s + dy * c) / height,
                    wide / width, l.block, height.toInt(),
                )
            }
        }

        /** The readable lines of one block (a line with no block is one alone), and where they are. */
        private class Box(val lines: List<SeenLine>) {
            val top = lines.minOf { it.centerY - it.height / 2 }
            val bottom = lines.maxOf { it.centerY + it.height / 2 }
            val left = lines.minOf { it.centerX - it.width / 2 }
            val right = lines.maxOf { it.centerX + it.width / 2 }
            val firstMiddle = lines.first().centerY
        }

        /**
         * Readable lines in reading order: blocks side by side (a band) are read as columns, left to right, each top
         * to bottom; bands top to bottom. Two columns were read across, a line of one and a line of the other (replay,
         * 2026-10-13). Lines with no block: top to bottom, left to right on one row.
         */
        fun inOrder(lines: List<SeenLine>): List<SeenLine> {
            val boxes = lines.filter(::readable).withIndex()
                .groupBy({ (i, l) -> if (l.block >= 0) l.block else -1 - i }, { it.value })
                .values.map { Box(it.sortedWith(compareBy({ l -> l.centerY }, { l -> l.centerX }))) }
                .sortedBy { it.top }
            // A band: boxes beside each other, each one's first line starting above the band's bottom.
            val bands = mutableListOf<MutableList<Box>>()
            var bottom = 0f
            for (box in boxes) {
                if (bands.isEmpty() || box.firstMiddle >= bottom) {
                    bands += mutableListOf(box)
                    bottom = box.bottom
                } else {
                    bands.last() += box
                    bottom = maxOf(bottom, box.bottom)
                }
            }
            return bands.flatMap { band ->
                // Columns: boxes that share some width, left to right; in a column, top to bottom.
                val columns = mutableListOf<MutableList<Box>>()
                var right = 0f
                for (box in band.sortedBy { it.left }) {
                    if (columns.isEmpty() || box.left >= right) {
                        columns += mutableListOf(box)
                        right = box.right
                    } else {
                        columns.last() += box
                        right = maxOf(right, box.right)
                    }
                }
                columns.flatMap { column -> column.sortedBy { it.top }.flatMap { it.lines } }
            }
        }

        /**
         * One text from two readings of it, [older] then [newer], in [newer]'s order: a line only one of them has
         * goes after the line it followed. Small print in a noisy frame was read in one frame and not the next, and
         * was left out (replay, 2026-10-13). Of one line read twice, the reading with more of it is kept (a big word
         * among small ones came in some frames only: "FIVE dollars for a cup of tea" / "dollars for a cup of tea"),
         * else the surer one; a piece of a line that another reading has whole goes.
         */
        fun merge(older: List<SeenLine>, newer: List<SeenLine>): List<SeenLine> {
            val out = newer.toMutableList()
            var after = -1
            for (line in older) {
                val i = out.indexOfFirst { sameLine(it.text, line.text) || covers(it, line) || covers(line, it) }
                if (i >= 0) {
                    out[i] = fuller(out[i], line)
                    after = i
                } else {
                    out.add(++after, line)
                }
            }
            return out.filterNot { piece -> out.any { it !== piece && covers(it, piece) } }
        }

        private fun letters(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

        /** [whole] has all of [piece] and more, on the same row: one line read whole and in part. */
        private fun covers(whole: SeenLine, piece: SeenLine): Boolean {
            val w = letters(whole.text)
            val p = letters(piece.text)
            return p.length >= MIN_LINE_CHARS && w.length > p.length && w.contains(p) &&
                abs(whole.centerY - piece.centerY) < maxOf(whole.height, piece.height) / 2
        }

        private fun fuller(a: SeenLine, b: SeenLine): SeenLine = when {
            covers(a, b) -> a
            covers(b, a) -> b
            b.confidence > a.confidence -> b
            else -> a
        }

        /** Lines in reading order, the pieces of one line together: each next piece to the right, at the same height. */
        fun rows(lines: List<SeenLine>): List<List<SeenLine>> {
            val rows = mutableListOf<MutableList<SeenLine>>()
            for (line in lines) {
                val last = rows.lastOrNull()?.last()
                // Not two readings of one line on top of each other ("100000 DOLLARS" / "10000O DOLLARS"): pieces
                // overlap by less than half the narrower one.
                val apart = last == null || line.width == 0f || last.width == 0f ||
                    (last.centerX + last.width / 2) - (line.centerX - line.width / 2) < minOf(line.width, last.width) / 2
                val sameRow = last != null && line.centerX > last.centerX && apart &&
                    abs(line.centerY - last.centerY) < minOf(line.height, last.height) / 2
                if (sameRow) rows.last() += line else rows += mutableListOf(line)
            }
            return rows
        }

        /**
         * The pieces of one line as one: a word at the end of a piece that begins the next one again (one word read
         * twice, "Prices start at FVE" + "FIVE dollars", replay 2026-10-13) is said once, as the next piece has it.
         */
        fun joinPieces(pieces: List<String>): String = pieces.reduce { text, next ->
            val last = text.substringAfterLast(' ')
            val first = next.substringBefore(' ')
            val a = letters(last)
            val b = letters(first)
            val twice = a.length >= MIN_LINE_CHARS && b.length >= MIN_LINE_CHARS && SearchResolver.distance(a, b) <= 1
            if (twice) text.dropLast(last.length).trimEnd().let { if (it.isEmpty()) next else "$it $next" } else "$text $next"
        }

        /** Two readings of one text: at least half the lines of the shorter one are in the other ([sameLine]). */
        fun sameText(a: List<String>, b: List<String>): Boolean {
            val (few, many) = if (a.size <= b.size) a to b else b to a
            if (few.isEmpty()) return false
            return few.count { line -> many.any { sameLine(it, line) } } * 2 >= few.size
        }

        /**
         * The first lines much bigger than the middle-sized one, one after the other, when there are a few of them;
         * else null. A big line further down is not part of it ("Weekly Notes" … "SALE all shoes", replay 2026-10-13).
         * [lines] in reading order.
         */
        fun title(lines: List<SeenLine>): String? {
            if (lines.size < 2) return null
            // The lower middle: with two big lines over two small ones the small ones are the body text.
            val middle = lines.map { it.height }.sorted()[(lines.size - 1) / 2]
            val isBig = { l: SeenLine -> l.height >= middle * TITLE_SCALE }
            val big = lines.dropWhile { !isBig(it) }.takeWhile(isBig)
            if (big.isEmpty() || big.size > MAX_TITLE_LINES) return null
            val words = big.map { it.text.trim().split(Regex("\\s+")).size }
            if (words.any { it > MAX_TITLE_LINE_WORDS } || words.sum() > MAX_TITLE_WORDS) return null
            return big.joinToString(" ") { it.text.trim() }
        }

        /** The first [BEGINNING_WORDS] words of the first line, with "…" when it goes on. */
        fun beginning(lines: List<SeenLine>): String {
            val words = lines.firstOrNull()?.text?.trim()?.split(Regex("\\s+")).orEmpty()
            val head = words.take(BEGINNING_WORDS).joinToString(" ")
            return if (words.size > BEGINNING_WORDS) "$head…" else head
        }

        /**
         * The user's answer to an offer: true to read it, false not to, null when it is no answer. A long sentence
         * is someone talking nearby: "read" in a lecture is not the user's answer (2026-10-13).
         */
        fun answer(text: String): Boolean? {
            val t = text.trim().lowercase()
            if (wordCount(t) > MAX_ANSWER_WORDS) return null
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

        /**
         * [line] as it is said, each mark said as a word: the voice only paused at them, and the user wanted brackets,
         * quotes, commas, dots and dashes read too (2026-10-13). A mark the voice says right stays: the dot in "3.5",
         * the comma in "1,000", the colon in "10:30", the apostrophe in "don't". After a sentence's end the word gets a
         * full stop and after a comma, colon or semicolon a comma, so the voice still pauses there.
         */
        fun spoken(line: String, lang: Lang): String {
            val names = if (lang == Lang.KO) MARKS_KO else MARKS_EN
            val out = StringBuilder()
            val text = line.replace("...", "…")
            for ((i, c) in text.withIndex()) {
                val name = names[c]
                val before = text.getOrNull(i - 1)
                val after = text.getOrNull(i + 1)
                val inNumber = c in ".,:" && before?.isDigit() == true && after?.isDigit() == true
                val inWord = c in "'’" && before?.isLetter() == true && after?.isLetter() == true
                if (name == null || inNumber || inWord) {
                    out.append(c)
                    continue
                }
                // No pause inside a word: "example dot com".
                val pause = when {
                    after?.isLetterOrDigit() == true -> ""
                    c in ".!?…" -> "."
                    c in ",;:" -> ","
                    else -> ""
                }
                out.append(' ').append(name).append(pause).append(' ')
            }
            return out.toString().trim().replace(Regex("\\s+"), " ")
        }

        private val MARKS_EN: Map<Char, String> = marks(
            "open bracket", "close bracket", "quote", "comma", "dot", "dot dot dot", "dash", "colon", "semicolon",
            "exclamation mark", "question mark", "slash",
        )

        private val MARKS_KO: Map<Char, String> = marks(
            "괄호 열고", "괄호 닫고", "따옴표", "쉼표", "마침표", "말줄임표", "대시", "콜론", "세미콜론", "느낌표", "물음표", "빗금",
        )

        private fun marks(
            open: String, close: String, quote: String, comma: String, dot: String, dots: String, dash: String,
            colon: String, semicolon: String, exclamation: String, question: String, slash: String,
        ): Map<Char, String> =
            "([{".associateWith { open } + ")]}".associateWith { close } +
                "\"'“”‘’«»".associateWith { quote } +
                mapOf(',' to comma, '.' to dot, '…' to dots, ':' to colon, ';' to semicolon, '!' to exclamation, '?' to question, '/' to slash) +
                "-–—".associateWith { dash }

        /** "Another text", "next": not this text, other text. */
        fun wantsAnother(text: String): Boolean {
            val t = text.trim().lowercase()
            if (wordCount(t) > MAX_ANSWER_WORDS) return false
            return t == "next" || t == "다음" || t.contains("another") || t.contains("new text") || t.contains("other text") ||
                t.contains("something else") || t.contains("different") || t.contains("다른")
        }

        /** Where to go on after a stop: the line that was being said, or null when none of them was. */
        fun resumeAt(lines: List<String>, speaking: String?): Int? =
            speaking?.let { lines.indexOf(it) }?.takeIf { it >= 0 }

        fun lookingForAnother(lang: Lang): String = if (lang == Lang.KO) "다른 글자를 찾을게요." else "Looking for other text."

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

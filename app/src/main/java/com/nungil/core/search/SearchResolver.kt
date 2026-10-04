package com.nungil.core.search

import com.nungil.contract.Lang
import com.nungil.core.lang.LabelNames

/**
 * Resolves a query to a saved person or item first, then to a COCO label.
 *
 * Saved names: exact, then containment (the name's words in a row, or in their order with a word slipped in
 * between for names of two words or more), then at most [MAX_TYPOS] edits for names of [MIN_FUZZY_LENGTH]+
 * characters (a Hangul syllable counts as one character). Names are also matched against the raw words, so a
 * name that is a filler word ("Me", "나") is never lost. Labels: the whole phrase, then two-word pairs, then
 * single words, each with Korean particles and English plurals stripped, through the synonym tables.
 */
object SearchResolver {
    const val MAX_TYPOS = 2
    const val MIN_FUZZY_LENGTH = 4

    /** How many other words may stand between the words of a saved name. */
    const val MAX_SLIPPED = 2

    private val EN_SYNONYMS = mapOf(
        "phone" to "cell phone", "cellphone" to "cell phone", "mobile" to "cell phone",
        "desk" to "dining table", "table" to "dining table",
        "sofa" to "couch",
        "monitor" to "tv", "screen" to "tv", "television" to "tv",
        "bag" to "backpack",
        "bike" to "bicycle",
        "computer" to "laptop", "notebook" to "laptop",
    )

    private val KO_SYNONYMS = mapOf(
        "가방" to "backpack",
        "휴대폰" to "cell phone", "핸드폰" to "cell phone", "폰" to "cell phone", "전화기" to "cell phone",
        "책상" to "dining table", "테이블" to "dining table", "탁자" to "dining table", "식탁" to "dining table",
        "소파" to "couch",
        "모니터" to "tv", "화면" to "tv", "티비" to "tv", "tv" to "tv", "텔레비전" to "tv",
        "자전거" to "bicycle",
        "컴퓨터" to "laptop", "노트북" to "laptop",
        "컵" to "cup", "잔" to "cup",
        "물병" to "bottle", "병" to "bottle",
    )

    /** Particles allowed after a Hangul name inside one word: "민준이를", "Ali가". */
    private val NAME_SUFFIXES: Set<Char> = QueryCleaner.PARTICLES + setOf('의', '에')

    fun resolve(text: String, saved: List<SavedName>, lang: Lang): SearchTarget? {
        val cleaned = QueryCleaner.clean(text)
        if (cleaned.raw.isEmpty()) return null
        matchSaved(cleaned, saved)?.let { return SearchTarget(it.type, it.id, it.label, it.name) }
        val label = matchLabel(cleaned) ?: return null
        return SearchTarget(TargetType.LABEL, -1L, label, LabelNames.name(label, lang))
    }

    /**
     * [resolve], and when [text] means nothing, the first of the recognizer's other [guesses] for the same
     * phrase that names something saved: "Find Dopey" was heard as "Find Dorothy", with "Find Dopey" as the
     * second guess (the logs). Guesses that do not have [text] in them are for some other phrase and are not used.
     */
    fun resolve(text: String, guesses: List<String>, saved: List<SavedName>, lang: Lang): SearchTarget? {
        resolve(text, saved, lang)?.let { return it }
        val heard = QueryCleaner.normalize(text)
        if (heard.isEmpty() || guesses.none { QueryCleaner.normalize(it).contains(heard) }) return null
        return guesses.firstNotNullOfOrNull { guess -> resolve(guess, saved, lang)?.takeIf { it.type != TargetType.LABEL } }
    }

    /** COCO label for one word or phrase, or null. Also used for suggestion chips. */
    fun labelFor(term: String): String? {
        val t = QueryCleaner.normalize(term)
        if (t.isEmpty()) return null
        val candidates = (listOf(t) + QueryCleaner.withoutParticles(t))
            .flatMap { if (it.length > 1 && it.endsWith("들")) listOf(it, it.dropLast(1)) else listOf(it) }
        for (candidate in candidates) {
            for (form in pluralForms(candidate)) {
                if (LabelNames.isKnown(form)) return form
                EN_SYNONYMS[form]?.let { return it }
                KO_SYNONYMS[form]?.let { return it }
                LabelNames.labelForKorean(form)?.let { return it }
            }
        }
        return null
    }

    private fun matchSaved(cleaned: QueryCleaner.Cleaned, saved: List<SavedName>): SavedName? {
        if (saved.isEmpty()) return null
        val wordLists = listOf(cleaned.words, cleaned.raw).filter { it.isNotEmpty() }
        val names = saved.map { it to QueryCleaner.normalize(it.name) }.filter { it.second.isNotEmpty() }

        // 1. Exact: the cleaned phrase (or the raw phrase) is the name, allowing particles on the last word.
        names.firstOrNull { (_, name) -> wordLists.any { sameAsName(it, name) } }?.let { return it.first }

        // 2. Containment: the name's words appear in a row inside the query, or the phrase is part of the name.
        names.firstOrNull { (_, name) -> wordLists.any { containsName(it, name) } }?.let { return it.first }
        val phrase = cleaned.phrase
        if (phrase.isNotEmpty()) names.firstOrNull { (_, name) -> nameContains(name, phrase) }?.let { return it.first }
        // 2b. A word slipped in: "my new black box" for "My black box" (the logs). Two-word names and longer only.
        names.firstOrNull { (_, name) -> wordLists.any { containsNameWords(it, name) } }?.let { return it.first }

        // 3. Fuzzy: small typos in longer names.
        if (phrase.isEmpty()) return null
        return names
            .filter { (_, name) -> name.length >= MIN_FUZZY_LENGTH }
            .map { (entry, name) -> entry to distance(phrase, name) }
            .filter { it.second <= MAX_TYPOS }
            .minByOrNull { it.second }
            ?.first
    }

    private fun sameAsName(words: List<String>, name: String): Boolean {
        val nameWords = name.split(' ')
        return words.size == nameWords.size && words.indices.all { wordIs(words[it], nameWords[it], it == words.lastIndex) }
    }

    private fun containsName(words: List<String>, name: String): Boolean {
        val nameWords = name.split(' ')
        if (nameWords.size > words.size) return false
        for (start in 0..words.size - nameWords.size) {
            if (nameWords.indices.all { wordIs(words[start + it], nameWords[it], it == nameWords.lastIndex) }) return true
        }
        return false
    }

    /**
     * All the words of a name of two words or more appear in [words] in their order, with at most [MAX_SLIPPED]
     * other words between the first and the last: "my new black box" is "My black box", "my phone in the bag"
     * is not "My bag".
     */
    private fun containsNameWords(words: List<String>, name: String): Boolean {
        val nameWords = name.split(' ')
        if (nameWords.size < 2 || nameWords.size > words.size) return false
        for (start in words.indices) {
            if (!wordIs(words[start], nameWords[0], false)) continue
            var next = 1
            var slipped = 0
            var i = start + 1
            while (i < words.size && next < nameWords.size && slipped <= MAX_SLIPPED) {
                if (wordIs(words[i], nameWords[next], next == nameWords.lastIndex)) next++ else slipped++
                i++
            }
            if (next == nameWords.size && slipped <= MAX_SLIPPED) return true
        }
        return false
    }

    /**
     * The phrase is part of the name: whole words ("chair" in "office chair"), or for Korean at least two
     * syllables inside a word ("민준" in "김민준"). "cup" never matches "cupboard".
     */
    private fun nameContains(name: String, phrase: String): Boolean {
        if (" $name ".contains(" $phrase ")) return true
        return phrase.count { isHangul(it) } >= 2 && name.contains(phrase)
    }

    private fun isHangul(c: Char): Boolean = c.code in 0xAC00..0xD7A3

    /** [word] equals [nameWord], or (for the last word) is [nameWord] followed by up to two particles. */
    private fun wordIs(word: String, nameWord: String, last: Boolean): Boolean {
        if (word == nameWord) return true
        if (!last || !word.startsWith(nameWord)) return false
        val suffix = word.substring(nameWord.length)
        return suffix.length in 1..2 && suffix.all { it in NAME_SUFFIXES }
    }

    private fun matchLabel(cleaned: QueryCleaner.Cleaned): String? {
        val words = cleaned.words.ifEmpty { cleaned.raw }
        labelFor(words.joinToString(" "))?.let { return it }
        for (i in 0 until words.size - 1) labelFor(words[i] + " " + words[i + 1])?.let { return it }
        for (w in words) labelFor(w)?.let { return it }
        return null
    }

    /** The word as said, then English singulars: "chairs", "glasses", "knives", "batteries". */
    private fun pluralForms(word: String): List<String> {
        val forms = mutableListOf(word)
        if (word.endsWith("ies") && word.length > 4) forms += word.dropLast(3) + "y"
        if (word.endsWith("ves") && word.length > 4) forms += word.dropLast(3) + "fe"
        if (word.endsWith("es") && word.length > 3) forms += word.dropLast(2)
        if (word.endsWith("s") && word.length > 2) forms += word.dropLast(1)
        return forms
    }

    /** Levenshtein distance; one Hangul syllable is one character. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}

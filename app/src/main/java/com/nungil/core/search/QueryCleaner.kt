package com.nungil.core.search

/**
 * Turns a spoken or typed query into words.
 *
 * [Cleaned.raw] keeps every word (lowercased, punctuation removed), so a saved name that is also a filler
 * word ("Me", "나") can still be matched. [Cleaned.words] drops command and filler words in both languages.
 */
object QueryCleaner {
    data class Cleaned(val raw: List<String>, val words: List<String>) {
        val phrase: String get() = words.joinToString(" ")
        val rawPhrase: String get() = raw.joinToString(" ")
    }

    /** Multi-word fillers, removed before single words. Longest first. */
    private val FILLER_PHRASES = listOf(
        "can you find", "search for", "look for", "where is", "where are",
        "어디에 있어", "어디에있어", "어디 있어", "어디있어", "찾아 줘", "찾아 주세요",
    )

    /** "me" and "나" are fillers on purpose: a saved person called "Me" or "나" must still be found (raw words). */
    private val FILLER_WORDS = setOf(
        "find", "search", "show", "help", "wheres", "me", "my", "the", "a", "an", "please",
        "찾아줘", "찾아주세요", "찾아", "찾기", "어디야", "어디", "있어", "나", "내", "나의", "제", "좀", "줘",
    )

    /** Korean particles that may trail a noun: 을/를 (object), 이/가 (subject), 은/는 (topic), 도 (also). */
    val PARTICLES: Set<Char> = setOf('을', '를', '이', '가', '은', '는', '도')

    fun clean(text: String): Cleaned {
        val normal = normalize(text)
        val raw = split(normal)
        var joined = " $normal "
        for (phrase in FILLER_PHRASES) joined = joined.replace(" $phrase ", " ")
        val words = split(joined).filter { it !in FILLER_WORDS }
        return Cleaned(raw, words)
    }

    /** Lowercase, apostrophes removed ("where's" becomes "wheres"), other punctuation becomes a space. */
    fun normalize(text: String): String =
        text.lowercase()
            .replace("'", "")
            .replace("’", "")
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")

    /**
     * [word] without up to two trailing Korean particles, shortest last: "배낭을" gives ["배낭"],
     * "민준이를" gives ["민준이", "민준"]. Empty when nothing can be stripped.
     */
    fun withoutParticles(word: String): List<String> {
        val out = mutableListOf<String>()
        var w = word
        repeat(2) {
            if (w.length > 1 && w.last() in PARTICLES) {
                w = w.dropLast(1)
                out += w
            }
        }
        return out
    }

    private fun split(text: String): List<String> = text.split(' ').filter { it.isNotBlank() }
}

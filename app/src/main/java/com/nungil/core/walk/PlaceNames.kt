package com.nungil.core.walk

/**
 * Place names the recognizer gets wrong. Listening in English it wrote "Soul", "Cell" and "Sable" for
 * Seoul, "Assam" and "Asansa" for Asan, "Put some" for Busan (the logs). A name sounds like what was
 * heard when the consonants agree: vowels, h, w and y are dropped and consonants that are easily
 * confused count as one (b/p/f/v, s/z/x, g/k/q, d/t/j/ch, m/n). An "r" that no vowel follows is not a
 * sound of its own: Cheonan came back as "Turn on".
 */
object PlaceNames {
    /** Cities and well-known parts of Seoul, as the geocoder knows them. */
    val KNOWN = listOf(
        "Seoul", "Busan", "Incheon", "Daegu", "Daejeon", "Gwangju", "Ulsan", "Sejong", "Suwon", "Seongnam",
        "Yongin", "Goyang", "Changwon", "Cheongju", "Cheonan", "Jeonju", "Ansan", "Anyang", "Asan", "Pohang",
        "Gimhae", "Jeju", "Pyeongtaek", "Gangneung", "Chuncheon", "Gyeongju", "Yeosu",
        "Gangnam", "Hongdae", "Itaewon", "Myeongdong", "Jamsil", "Yeouido", "Jongno", "Sinchon", "Dongdaemun",
    )

    private val HOME = setOf("home", "my home", "house", "my house", "집", "우리 집", "내 집", "우리집")

    /** "home" is the user's own saved place, never a search for something called Home. */
    fun isHome(text: String): Boolean = text.trim().trimEnd('.', '!', '?').lowercase() in HOME

    /** What the recognizer wrote for a name when the sounds alone do not give it away (the logs). */
    private val ALIASES = mapOf(
        "Seoul" to setOf("soul", "sole", "sol", "so", "sale", "sail", "sailed", "save", "saved", "sable", "cell", "sell", "seal", "saul", "see you"),
        "Cheonan" to setOf("jonah", "turn on"),
    )

    /** The known name [heard] is a written-down mishearing of, or null. */
    fun alias(heard: String): String? {
        val h = heard.trim().trimEnd('.', '!', '?').lowercase()
        return ALIASES.entries.firstOrNull { h in it.value }?.key
    }

    /** The consonant sounds of [name]; "a" in front when it starts with a vowel or an h. Empty for non-Latin names. */
    fun key(name: String): String {
        val letters = name.lowercase().filter { it in 'a'..'z' }
        val out = StringBuilder()
        if (letters.isNotEmpty() && letters[0] in "aeiouh") out.append('a')
        var last = ' '
        for ((i, c) in letters.withIndex()) {
            val next = letters.getOrNull(i + 1)
            val code = when (c) {
                'b', 'p', 'f', 'v' -> '1'
                // "ch" is close to "t" (but "sch" is a k), "ce" and "ci" are an s, any other c is a k.
                'c' -> when {
                    next == 'h' -> if (letters.getOrNull(i - 1) == 's') '7' else '3'
                    next != null && next in "eiy" -> '2'
                    else -> '7'
                }
                's', 'x', 'z' -> '2'
                'd', 't', 'j' -> '3'
                'g', 'k', 'q' -> '7'
                'l' -> '4'
                'm', 'n' -> '5'
                'r' -> if (next != null && next in "aeiouy") '6' else ' '
                else -> ' '
            }
            // "ss" is one sound; the same sound after a vowel is said again ("Cheonan").
            if (code != ' ' && code != last) out.append(code)
            last = code
        }
        return out.toString()
    }

    /**
     * The [known] names that sound like something in [heard], each with 0 (the same sounds, or a
     * written-down mishearing) or 1 (one sound more or one changed in what was heard, at least three
     * sounds), best first. Fewer sounds than the name has do not count: "New York" would be half of the map.
     */
    fun ranked(heard: List<String>, known: Collection<String>): List<Pair<String, Int>> {
        val aliased = heard.mapNotNull { alias(it) }.toSet()
        val keys = heard.map { key(it) }.filter { it.length >= MIN_KEY }
        return known.distinct().mapNotNull { name ->
            if (name in aliased) return@mapNotNull name to 0
            val k = key(name)
            if (k.length < MIN_KEY) return@mapNotNull null
            // A city is also called by its name with "-si" ("Asan-si" was heard as "Asansa").
            if (key(name + "si") in keys) return@mapNotNull name to 0
            val best = keys.minOfOrNull { distance(it, k).let { d -> if (d == 1 && (it.length < MIN_NEAR_KEY || it.length < k.length)) 2 else d } }
            if (best != null && best <= 1) name to best else null
        }.sortedWith(
            // Of two names one sound off, the one the heard words begin with: the recognizer adds to the
            // end ("Assange" is Asan, not Anyang).
            compareBy<Pair<String, Int>>(
                { it.second },
                // a written-down mishearing is surer than sounds that happen to agree ("Jonah" is Cheonan)
                { (name, _) -> if (name in aliased) 0 else 1 },
                { (name, _) -> if (keys.any { it.startsWith(key(name)) }) 0 else 1 },
            ),
        )
    }

    fun soundsLike(heard: List<String>, known: Collection<String>): List<String> = ranked(heard, known).map { it.first }

    /**
     * Has the search result [name] anything to do with the [query]? The geocoder answers nearly anything:
     * "Turn on" gave Onyang-dong and "Assange" gave Sejong-ro. A word of the query has to start a word of
     * the name or the other way round (three letters at least), or sound the same. A name without Latin
     * letters cannot be compared and is kept.
     */
    fun related(query: String, name: String): Boolean {
        val nameWords = words(name)
        if (nameWords.isEmpty()) return true
        return words(query).any { q ->
            nameWords.any { n ->
                (q.length >= MIN_WORD && n.length >= MIN_WORD && (n.startsWith(q) || q.startsWith(n))) ||
                    (key(q).length >= MIN_KEY && key(q) == key(n))
            }
        }
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }

    private const val MIN_WORD = 3
    private const val MIN_KEY = 2
    private const val MIN_NEAR_KEY = 3

    private fun distance(a: String, b: String): Int {
        var row = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val next = IntArray(b.length + 1)
            next[0] = i
            for (j in 1..b.length) {
                next[j] = minOf(row[j] + 1, next[j - 1] + 1, row[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            row = next
        }
        return row[b.length]
    }
}

package com.nungil.core.lang

/**
 * Korean particles that depend on whether the previous word ends in a consonant (batchim).
 * Hangul is exact; digits use their Sino-Korean reading; Latin letters use a small heuristic
 * (ends in l, m or n means batchim), which is right for most names.
 */
object Josa {
    private const val HANGUL_FIRST = 0xAC00
    private const val HANGUL_LAST = 0xD7A3
    private const val DIGITS_WITH_BATCHIM = "0136780"

    fun hasBatchim(word: String): Boolean {
        val c = word.trimEnd().lastOrNull { it.isLetterOrDigit() } ?: return false
        return when {
            c.code in HANGUL_FIRST..HANGUL_LAST -> (c.code - HANGUL_FIRST) % 28 != 0
            c.isDigit() -> c in DIGITS_WITH_BATCHIM
            else -> c.lowercaseChar() in "lmn"
        }
    }

    /** 이/가 (subject). */
    fun iGa(word: String): String = word + if (hasBatchim(word)) "이" else "가"

    /** 은/는 (topic). */
    fun eunNeun(word: String): String = word + if (hasBatchim(word)) "은" else "는"

    /** 을/를 (object). */
    fun eulReul(word: String): String = word + if (hasBatchim(word)) "을" else "를"

    /** 과/와 (and). */
    fun waGwa(word: String): String = word + if (hasBatchim(word)) "과" else "와"
}

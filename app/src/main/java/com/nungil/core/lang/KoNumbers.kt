package com.nungil.core.lang

/** Native Korean numbers as used before a counter: 한 개, 두 명, 세 대 … 스무 개; 21 and above as digits. */
object KoNumbers {
    private val native = arrayOf(
        "", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열",
        "열한", "열두", "열세", "열네", "열다섯", "열여섯", "열일곱", "열여덟", "열아홉", "스무",
    )

    /** "세 개" for (3, "개"); "21개" for (21, "개"). [n] must be at least 1. */
    fun count(n: Int, counter: String): String {
        require(n >= 1) { "count must be at least 1, was $n" }
        return if (n < native.size) "${native[n]} $counter" else "$n$counter"
    }
}

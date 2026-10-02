package com.nungil.core.voice

/**
 * Questions answered at once on any screen: the time, the date, the weather. The logs had "Tell me
 * weather", "What time" and "I tell me time" eight times with no answer (on the Go screen they were
 * searched as places). Only whole questions count: "next time" and "Time Square" are not one.
 */
enum class QuickAsk {
    TIME, DATE, WEATHER;

    companion object {
        private val ASKING = setOf("what", "whats", "tell", "say", "check", "current", "now", "the", "is", "it", "me", "please", "show")
        private val NOT_TIME = setOf("next", "last", "every", "more", "first", "second", "long", "some", "any", "times")

        fun of(text: String): QuickAsk? {
            val words = text.lowercase().replace("'", "").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            val joined = words.joinToString("")
            return when {
                words.any { it in setOf("weather", "forecast", "temperature") } || listOf("날씨", "기온").any { joined.contains(it) } -> WEATHER
                listOf("몇시", "시간알려", "지금시간").any { joined.contains(it) } -> TIME
                listOf("며칠", "무슨요일", "날짜").any { joined.contains(it) } -> DATE
                "date" in words && words.all { it == "date" || it == "today" || it == "todays" || it in ASKING } -> DATE
                "day" in words && "what" in words && words.all { it == "day" || it == "today" || it in ASKING } -> DATE
                isTime(words) -> TIME
                else -> null
            }
        }

        /** "time", "what time", "tell me the time", "I tell me time" (the wake word "Eye" heard as "I"). */
        private fun isTime(words: List<String>): Boolean {
            if ("time" !in words && "clock" !in words) return false
            if (words.any { it in NOT_TIME }) return false
            val rest = words.filter { it != "time" && it != "clock" && it != "i" && it != "hi" && it != "eye" }
            return rest.all { it in ASKING }
        }
    }
}

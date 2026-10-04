package com.nungil.core.walk

/**
 * What a walker asks in Go mode (spec §3). Only whole questions count, as in QuickAsk: "next time", "repeat
 * after me" and a place name on "Where to?" ("Next Door Cafe") are never one. English is matched word for
 * word after "please", "ok" and "hey"; Korean without its spaces, because the recognizer spaces it its own way.
 */
enum class GoQuestion {
    HOW_FAR, NEXT, REPEAT, WHICH_WAY, WHERE_AM_I, QUIET, UPDATES_ON;

    companion object {
        private val FILLERS = setOf("please", "ok", "okay", "hey")

        private val EN = mapOf(
            HOW_FAR to setOf(
                "how far", "how far is it", "how far left", "how far to go", "how long", "how long is it",
                "how much further", "how much farther", "how much longer", "how much more", "how many minutes",
                "how many minutes left", "how many metres", "how many meters", "when will i arrive",
                "when do i arrive", "when will i get there", "distance", "distance left", "time left",
            ),
            NEXT to setOf("whats next", "what is next", "what next", "next turn", "next instruction", "next step"),
            REPEAT to setOf("repeat", "repeat that", "say again", "say that again", "what did you say", "pardon"),
            WHICH_WAY to setOf("which way", "which way now", "which direction", "where do i go", "where should i go", "where now", "direction"),
            WHERE_AM_I to setOf("where am i", "where are we", "what street is this", "what street am i on", "which street is this", "my location", "current location"),
            QUIET to setOf("quiet updates", "fewer updates", "less updates", "updates off", "stop updates", "no updates", "turn off updates"),
            UPDATES_ON to setOf("updates on", "more updates", "turn on updates", "start updates"),
        )

        private val KO = mapOf(
            HOW_FAR to setOf(
                "얼마나남았어", "얼마나남았어요", "얼마나남았지", "몇분남았어", "몇분남았어요", "몇미터남았어",
                "몇미터남았어요", "언제도착", "언제도착해", "언제도착해요", "얼마나걸려", "얼마나걸려요", "거리",
                "남은거리", "남은시간",
            ),
            NEXT to setOf("다음은", "다음안내", "다음은뭐야", "다음은어디야"),
            REPEAT to setOf("다시", "반복", "뭐라고", "뭐라고요", "다시말해", "다시말해줘", "다시한번"),
            WHICH_WAY to setOf("어느쪽", "어느쪽이야", "어느쪽으로", "어디로가", "어디로가요", "어디로가야해", "방향"),
            WHERE_AM_I to setOf("여기어디", "여기어디야", "여기가어디야", "지금어디", "지금어디야", "현재위치", "내위치"),
            QUIET to setOf("안내조용히", "업데이트꺼", "업데이트꺼줘"),
            UPDATES_ON to setOf("업데이트켜", "업데이트켜줘", "안내켜", "안내다시켜"),
        )

        fun of(text: String): GoQuestion? {
            val words = text.lowercase().replace("'", "").replace("’", "")
                .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
                .dropWhile { it in FILLERS }.dropLastWhile { it == "please" }
            if (words.isEmpty()) return null
            val spaced = words.joinToString(" ")
            val joined = words.joinToString("")
            return entries.firstOrNull { q -> spaced in EN.getValue(q) || joined in KO.getValue(q) }
        }
    }
}

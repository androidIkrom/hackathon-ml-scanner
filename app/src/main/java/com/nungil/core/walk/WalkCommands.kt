package com.nungil.core.walk

/** Walk-mode voice commands that carry a place name. Parsed before the general command parser. */
sealed interface WalkCommand {
    data class GoTo(val place: String) : WalkCommand
    /** [name] null: the user did not say one, so ask for it. */
    data class SavePlace(val name: String?) : WalkCommand

    /** Open Go mode with no destination yet: "go mode", "길 안내". */
    data object GoMode : WalkCommand
}

object WalkCommands {
    private val EN_SAVE = Regex("""^(?:please\s+)?(?:save|remember|mark)\s+(?:this\s+place|this\s+spot|here|this\s+location|the\s+place)\s+(?:as|called)\s+(.+)$""", RegexOption.IGNORE_CASE)
    /** "go to …" is left to the screen parser ("go to settings"); these verbs only mean walking there. */
    private val EN_GO = Regex("""^(?:please\s+)?(?:take\s+me|guide\s+me|walk\s+me|navigate|directions|lead\s+me|get\s+me)\s+(?:back\s+)?(?:to|towards|toward)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val EN_HOME = Regex("""^(?:please\s+)?(?:take|bring|walk|get|guide)\s+me\s+(?:back\s+)?home$""", RegexOption.IGNORE_CASE)

    private val EN_SAVE_BARE = Regex("""^(?:please\s+)?(?:save|remember|mark)\s+(?:this\s+place|this\s+spot|here|this\s+location|my\s+location|the\s+place|this)$""", RegexOption.IGNORE_CASE)
    private val KO_SAVE_BARE = Regex("""^(?:여기|이\s*곳|이\s*장소|지금\s*위치|현재\s*위치)(?:를|을)?\s*저장.*$""")

    /** 여기를 집으로 저장해 줘 / 이 장소를 학교로 저장 / 이곳을 집이라고 저장 */
    private val KO_SAVE = Regex("""^(?:여기|이\s*곳|이\s*장소|지금\s*위치|현재\s*위치)(?:를|을)?\s*(.+?)(?:으로|로|이라고|라고)\s*저장.*$""")

    /** 학교까지 안내해 줘 / 역에 데려다 줘 / 집으로 가는 길 알려 줘: always a place. */
    private val KO_GO = Regex("""^(.+?)\s*(?:으로|로|까지|에)\s*(?:안내해\s*줘|안내해\s*주세요|안내|데려다\s*줘|데려다\s*주세요|가는\s*길.*|길\s*안내.*)$""")

    /** 집으로 가자: a place unless the word is a screen ("설정으로 가자"). */
    private val KO_LETS_GO = Regex("""^(.+?)\s*(?:으로|로|까지|에)\s*(?:가자|가 줘|가줘|가고 싶어)$""")

    /** [isScreenWord] tells a screen name ("설정", "saved") from a place for the ambiguous "…로 가자". */
    private val GO_MODE = setOf(
        "go mode", "go", "navigation", "navigation mode", "navigate", "directions", "go somewhere", "where to",
        "길 안내", "길안내", "길 안내 모드", "길안내 모드", "길 찾기", "길찾기", "길 찾기 모드",
    )

    fun parse(text: String, isScreenWord: (String) -> Boolean = { false }): WalkCommand? {
        val t = text.trim().trimEnd('.', '!', '?', ',').trim()
        if (t.isEmpty()) return null
        if (t.lowercase() in GO_MODE) return WalkCommand.GoMode
        EN_SAVE.find(t)?.let { m -> name(m.groupValues[1])?.let { return WalkCommand.SavePlace(it) } }
        KO_SAVE.find(t)?.let { m -> name(m.groupValues[1])?.let { return WalkCommand.SavePlace(it) } }
        if (EN_SAVE_BARE.matches(t) || KO_SAVE_BARE.matches(t)) return WalkCommand.SavePlace(null)
        EN_GO.find(t)?.let { m -> name(m.groupValues[1])?.let { return WalkCommand.GoTo(it) } }
        if (EN_HOME.matches(t)) return WalkCommand.GoTo("home")
        KO_GO.find(t)?.let { m -> name(m.groupValues[1])?.let { return WalkCommand.GoTo(it) } }
        KO_LETS_GO.find(t)?.let { m ->
            name(m.groupValues[1])?.let { if (!isScreenWord(it)) return WalkCommand.GoTo(it) }
        }
        return null
    }

    private fun name(raw: String): String? {
        val n = raw.trim()
            .removePrefix("the ").removePrefix("my ").removePrefix("내 ").removePrefix("우리 ")
            .trim()
        return n.ifEmpty { null }
    }
}

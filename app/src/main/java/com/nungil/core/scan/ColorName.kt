package com.nungil.core.scan

/**
 * The 11 basic colour names, with the words each language needs:
 * English, the Korean form before a noun ("파란 의자") and the Korean noun used in lists ("파란색, 빨간색").
 */
enum class ColorName(val en: String, val koAdjective: String, val koNoun: String) {
    RED("red", "빨간", "빨간색"),
    ORANGE("orange", "주황색", "주황색"),
    YELLOW("yellow", "노란", "노란색"),
    GREEN("green", "초록색", "초록색"),
    BLUE("blue", "파란", "파란색"),
    PURPLE("purple", "보라색", "보라색"),
    PINK("pink", "분홍색", "분홍색"),
    BROWN("brown", "갈색", "갈색"),
    BLACK("black", "검은", "검은색"),
    WHITE("white", "흰", "흰색"),
    GRAY("gray", "회색", "회색");

    /** Black, white and gray are not "colourful"; a colourful name wins with only a 30% share. */
    val isChromatic: Boolean get() = this != BLACK && this != WHITE && this != GRAY
}

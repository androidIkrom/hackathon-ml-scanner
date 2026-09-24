package com.nungil.core.scan

import com.nungil.contract.Lang

/** Direction words (team plan §5 glossary). Speech uses 4 sectors; history stores 8. */
object Directions {
    private val sector4En = arrayOf("in front", "on your right", "behind you", "on your left")
    private val sector4Ko = arrayOf("앞", "오른쪽", "뒤", "왼쪽")
    private val sector8En = arrayOf("front", "front right", "right", "back right", "back", "back left", "left", "front left")
    private val sector8Ko = arrayOf("앞", "오른쪽 앞", "오른쪽", "오른쪽 뒤", "뒤", "왼쪽 뒤", "왼쪽", "왼쪽 앞")

    /** English phrase placed after the objects ("3 chairs in front"); Korean word placed before them ("앞에 …"). */
    fun sector4Phrase(sector: Int, lang: Lang): String =
        if (lang == Lang.KO) sector4Ko[sector.mod(4)] else sector4En[sector.mod(4)]

    /** Name of one of the 8 sectors, for lists such as history. */
    fun sector8Name(sector: Int, lang: Lang): String =
        if (lang == Lang.KO) sector8Ko[sector.mod(8)] else sector8En[sector.mod(8)]

    /** Direction phrase for an angle relative to where the user faces. */
    fun of(relAngle: Float, lang: Lang): String = sector4Phrase(AngleMath.sector4(relAngle), lang)
}

package com.nungil.core.scan

import com.nungil.contract.DirectionStyle
import com.nungil.contract.Lang
import com.nungil.core.ui.Bearings
import com.nungil.core.lang.Josa
import com.nungil.core.lang.KoNumbers
import com.nungil.core.lang.LabelNames

/**
 * What the app says about a scan, in English and Korean (team plan §5 canonical sentences).
 * Angles in [ObjectSummary] are relative to where the user faces now.
 */
object SummaryBuilder {
    private val irregular = mapOf(
        "person" to "people",
        "mouse" to "mice",
        "knife" to "knives",
        "skis" to "skis",
        "scissors" to "scissors",
        "sheep" to "sheep",
    )

    /** English plural of a COCO label: "chair" -> "chairs", "bus" -> "buses", "person" -> "people". */
    fun plural(label: String): String {
        irregular[label]?.let { return it }
        return if (label.endsWith("s") || label.endsWith("x") || label.endsWith("ch") || label.endsWith("sh")) {
            label + "es"
        } else {
            label + "s"
        }
    }

    /** "a" or "an" before [word]. */
    fun article(word: String): String {
        val first = word.firstOrNull()?.lowercaseChar() ?: return "a"
        return if (first in VOWELS) "an" else "a"
    }

    /** Same label in one direction: counts add up, colours are listed per cluster. */
    private class Group(val label: String, val isName: Boolean, var count: Int, val colors: MutableList<ColorName?>)

    /** "3 blue chairs", "a person", "4 chairs in blue, red and gray" / "파란 의자 세 개", "사람 한 명". */
    fun describe(o: ObjectSummary, lang: Lang, colorsOn: Boolean = true): String =
        groupText(Group(o.label, o.isName, o.count, mutableListOf(o.color)), lang, colorsOn, liveKo = false)

    /**
     * One live announcement, its direction said as on every screen (Bearings): "a blue chair ahead" / "앞에 파란 의자";
     * "3 chairs on your left" / "왼쪽에 의자 세 개"; "3 chairs at 9 o'clock". [o]'s angle is relative to where the
     * user faces.
     */
    fun livePhrase(o: ObjectSummary, lang: Lang, style: DirectionStyle, colorsOn: Boolean = true): String {
        val group = Group(o.label, o.isName, o.count, mutableListOf(o.color))
        val w = Bearings.say(AngleMath.diff(o.angle, 0f), style, lang)
        return if (lang == Lang.KO) {
            "$w ${groupText(group, lang, colorsOn, liveKo = true)}"
        } else {
            "${groupText(group, lang, colorsOn, liveKo = false)} $w"
        }
    }

    /**
     * The full-scan sentence. EN: "Around you: 3 blue chairs ahead; a black laptop on your right."
     * KO: "앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요." Below 100% coverage it admits how much was seen.
     * Objects are grouped by the four sectors; each is said at its middle (Bearings: ahead, right, behind, left).
     */
    fun fullSummary(objects: List<ObjectSummary>, coveragePercent: Int, lang: Lang, style: DirectionStyle, colorsOn: Boolean = true): String {
        val prefix = if (coveragePercent in 0..99) coveragePrefix(coveragePercent, lang) else ""
        if (objects.isEmpty()) return prefix + nothingFound(lang)
        val bySector = (0 until 4).map { sector -> groupsIn(objects.filter { AngleMath.sector4(it.angle) == sector }) }
        val clauses = bySector.withIndex().filter { it.value.isNotEmpty() }.map { (sector, groups) ->
            sector to groups.map { groupText(it, lang, colorsOn, liveKo = false) }
        }
        return prefix + if (lang == Lang.KO) koreanSummary(clauses, style) else englishSummary(clauses, style)
    }

    fun coveragePrefix(percent: Int, lang: Lang): String =
        if (lang == Lang.KO) "방의 ${percent}퍼센트를 살펴봤어요. " else "I scanned $percent percent of the room. "

    fun nothingFound(lang: Lang): String =
        if (lang == Lang.KO) "찾은 물건이 없어요. 밝은 곳에서 천천히 돌아 주세요."
        else "No objects found. Try better lighting and turn slowly."

    /** The middle of sector [sector] of four (0 ahead, 1 right, 2 behind, 3 left), as Bearings says it. */
    private fun sectorWords(sector: Int, style: DirectionStyle, lang: Lang): String =
        Bearings.say(listOf(0f, 90f, 180f, -90f)[sector.mod(4)], style, lang)

    private fun englishSummary(clauses: List<Pair<Int, List<String>>>, style: DirectionStyle): String =
        "Around you: " + clauses.joinToString("; ") { (sector, groups) ->
            "${joinAnd(groups)} ${sectorWords(sector, style, Lang.EN)}"
        } + "."

    private fun koreanSummary(clauses: List<Pair<Int, List<String>>>, style: DirectionStyle): String {
        val parts = clauses.mapIndexed { i, (sector, groups) ->
            val last = i == clauses.lastIndex
            val items = if (last) groups.dropLast(1) + Josa.iGa(groups.last()) else groups
            "${sectorWords(sector, style, Lang.KO)} ${joinKorean(items)}"
        }
        return parts.joinToString(", ") + " 있어요."
    }

    private fun groupsIn(objects: List<ObjectSummary>): List<Group> {
        val groups = mutableListOf<Group>()
        for (o in objects) {
            val existing = if (o.isName) null else groups.firstOrNull { !it.isName && it.label == o.label }
            if (existing == null) {
                groups += Group(o.label, o.isName, o.count, mutableListOf(o.color))
            } else {
                existing.count += o.count
                existing.colors += o.color
            }
        }
        return groups
    }

    private fun groupText(g: Group, lang: Lang, colorsOn: Boolean, liveKo: Boolean): String {
        if (g.isName) return g.label
        val useColor = colorsOn && ColorPolicy.hasColor(g.label)
        val distinct = if (useColor) g.colors.filterNotNull().distinct() else emptyList()
        val single = distinct.singleOrNull()?.takeIf { g.colors.all { c -> c == it } }
        val mixed = if (distinct.size >= 2) distinct else null
        return if (lang == Lang.KO) {
            val name = LabelNames.name(g.label, Lang.KO)
            val amount = if (liveKo && g.count == 1) "" else " " + KoNumbers.count(g.count, LabelNames.counterKo(g.label))
            when {
                single != null -> "${single.koAdjective} $name$amount"
                mixed != null -> "${mixed.joinToString(", ") { it.koNoun }} $name$amount"
                else -> "$name$amount"
            }
        } else {
            when {
                single != null && g.count == 1 -> "${article(single.en)} ${single.en} ${g.label}"
                single != null -> "${g.count} ${single.en} ${plural(g.label)}"
                mixed != null -> "${g.count} ${plural(g.label)} in ${joinAnd(mixed.map { it.en })}"
                g.count == 1 -> "${article(g.label)} ${g.label}"
                else -> "${g.count} ${plural(g.label)}"
            }
        }
    }

    /** "a", "a and b", "a, b and c". */
    fun joinAnd(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    /** "a", "a와 b", "a, b와 c" with 와/과 chosen by the word before it. */
    fun joinKorean(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> {
            val head = items.dropLast(2)
            val pair = Josa.waGwa(items[items.size - 2]) + " " + items.last()
            (head + pair).joinToString(", ")
        }
    }

    private const val VOWELS = "aeiou"
}

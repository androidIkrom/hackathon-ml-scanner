package com.nungil.core.ui

import com.nungil.contract.Lang

enum class ControlKind { BUTTON, SWITCH, SLIDER, TEXT_FIELD, TEXT }

/** One control on screen; [checked] is null for controls without an on/off state. */
data class GuideItem(
    val label: String,
    val kind: ControlKind,
    val checked: Boolean?,
    val enabled: Boolean,
    val centerX: Float,
    val centerY: Float,
)

/** Words for the voice guide: "tap a control to hear it, tap empty space to hear the whole screen". */
object ScreenGuide {
    const val MAX_ITEMS = 12

    private val positionsEn = arrayOf("top left", "top", "top right", "left", "middle", "right", "bottom left", "bottom", "bottom right")
    private val positionsKo = arrayOf("왼쪽 위", "위", "오른쪽 위", "왼쪽", "가운데", "오른쪽", "왼쪽 아래", "아래", "오른쪽 아래")

    fun position(x: Float, y: Float, width: Float, height: Float, lang: Lang): String {
        fun third(v: Float, size: Float) = when {
            v < size / 3f -> 0
            v < size * 2f / 3f -> 1
            else -> 2
        }
        val i = third(y, height) * 3 + third(x, width)
        return if (lang == Lang.KO) positionsKo[i] else positionsEn[i]
    }

    fun describeControl(label: String, kind: ControlKind, checked: Boolean?, enabled: Boolean, lang: Lang): String {
        val ko = lang == Lang.KO
        val parts = mutableListOf(label)
        when (kind) {
            ControlKind.BUTTON -> parts += if (ko) "버튼" else "button"
            ControlKind.SWITCH -> parts += if (ko) "스위치" else "switch"
            ControlKind.SLIDER -> parts += if (ko) "슬라이더" else "slider"
            ControlKind.TEXT_FIELD -> parts += if (ko) "입력창" else "text box"
            ControlKind.TEXT -> Unit
        }
        if (checked != null) {
            parts += if (kind == ControlKind.SWITCH) {
                if (checked) (if (ko) "켜짐" else "on") else (if (ko) "꺼짐" else "off")
            } else {
                if (checked) (if (ko) "선택됨" else "selected") else (if (ko) "선택 안 됨" else "not selected")
            }
        }
        if (!enabled) parts += if (ko) "사용할 수 없음" else "unavailable"
        return parts.joinToString(", ")
    }

    fun summary(items: List<GuideItem>, width: Float, height: Float, lang: Lang): String {
        val ko = lang == Lang.KO
        if (items.isEmpty()) return if (ko) "이 화면에는 누를 것이 없어요." else "Nothing to press on this screen."
        val head = when {
            ko -> "이 화면에는 항목이 ${items.size}개 있어요."
            items.size == 1 -> "This screen has 1 control."
            else -> "This screen has ${items.size} controls."
        }
        val shown = items.take(MAX_ITEMS).map {
            describeControl(it.label, it.kind, it.checked, it.enabled, lang) + ", " +
                position(it.centerX, it.centerY, width, height, lang) + "."
        }
        val rest = items.size - MAX_ITEMS
        val tail = if (rest > 0) listOf(if (ko) "외 ${rest}개." else "And $rest more.") else emptyList()
        return (listOf(head) + shown + tail).joinToString(" ")
    }
}

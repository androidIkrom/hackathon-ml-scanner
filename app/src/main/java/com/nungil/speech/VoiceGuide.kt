package com.nungil.speech

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.nungil.contract.Lang
import com.nungil.core.ui.ControlKind
import com.nungil.core.ui.GuideItem
import com.nungil.core.ui.ScreenGuide

/**
 * The voice guide for users without TalkBack: a tap on a control speaks its name and state, a tap on
 * empty space speaks a summary of the screen. It never consumes the tap, so the control still works.
 */
class VoiceGuide {
    private val rect = Rect()

    /** What to say for a tap at screen coordinates ([rawX], [rawY]); null when the tap is outside [root]. */
    fun describeAt(root: View, rawX: Int, rawY: Int, lang: Lang): String? {
        if (!root.getGlobalVisibleRect(rect) || !rect.contains(rawX, rawY)) return null
        val hit = deepestControlAt(root, rawX, rawY)
        if (hit != null) {
            val (kind, checked) = kindOf(hit)
            return ScreenGuide.describeControl(labelOf(hit), kind, checked, hit.isEnabled, lang)
        }
        val items = mutableListOf<GuideItem>()
        collect(root, items)
        root.getGlobalVisibleRect(rect)
        return ScreenGuide.summary(items, rect.width().toFloat(), rect.height().toFloat(), lang)
    }

    private fun isControl(view: View): Boolean =
        view.isShown && (view.isClickable || view is CompoundButton || view is Slider || view is EditText)

    private fun deepestControlAt(view: View, x: Int, y: Int): View? {
        if (!view.isShown || !view.getGlobalVisibleRect(rect) || !rect.contains(x, y)) return null
        if (view is ViewGroup && view !is Slider) {
            for (i in view.childCount - 1 downTo 0) {
                deepestControlAt(view.getChildAt(i), x, y)?.let { return it }
            }
        }
        return if (isControl(view)) view else null
    }

    private fun collect(view: View, out: MutableList<GuideItem>) {
        if (!view.isShown) return
        if (isControl(view)) {
            view.getGlobalVisibleRect(rect)
            val (kind, checked) = kindOf(view)
            out += GuideItem(labelOf(view), kind, checked, view.isEnabled, rect.exactCenterX(), rect.exactCenterY())
            return
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i), out)
    }

    private fun kindOf(view: View): Pair<ControlKind, Boolean?> = when {
        view is CompoundButton -> ControlKind.SWITCH to view.isChecked
        view is Slider -> ControlKind.SLIDER to null
        view is EditText -> ControlKind.TEXT_FIELD to null
        view is MaterialButton && view.isCheckable -> ControlKind.BUTTON to view.isChecked
        else -> ControlKind.BUTTON to null
    }

    private fun labelOf(view: View): String {
        view.contentDescription?.takeIf { it.isNotBlank() }?.let { described ->
            return if (view is Slider) "$described ${view.value.toInt()}" else described.toString()
        }
        if (view is TextView && view.text.isNotBlank()) return view.text.toString()
        val texts = mutableListOf<String>()
        fun gather(v: View) {
            if (!v.isShown) return
            if (v is TextView && v.text.isNotBlank()) texts += v.text.toString()
            if (v is ViewGroup) for (i in 0 until v.childCount) gather(v.getChildAt(i))
        }
        gather(view)
        return texts.joinToString(", ")
    }
}

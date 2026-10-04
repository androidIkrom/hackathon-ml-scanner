package com.nungil.core.ui

import com.nungil.contract.Lang

/**
 * A tap where there is nothing to press (no button, no field) is someone looking for the way: the app offers
 * the screen's instructions (ScreenHelp), and gives them only on "yes". Asked once a screen visit; a "no", or
 * no answer, is not asked again until another screen has been opened.
 */
class TapHelpOffer {
    private var screen: String? = null
    private var asked = false

    /** A screen was opened (or opened again). */
    fun onScreen(id: String) {
        screen = id
        asked = false
    }

    /**
     * Whether to ask now, after a tap on nothing. [canHear]: the answer would reach the app (not asleep, no
     * other question waiting); without it nothing is asked, and a later tap may.
     */
    fun ask(canHear: Boolean): Boolean {
        if (!canHear || asked) return false
        asked = true
        return true
    }

    /** What a short tap leads to: the question, the voice guide naming what is there, or nothing. */
    enum class Tap { ASK, DESCRIBE, NOTHING }

    /**
     * A short tap, [empty] when on nothing that can be pressed. With the [voiceGuide] on, a tap on nothing asks
     * first, as without it, and names the screen once the question has been asked (or cannot be heard); any
     * other tap is named as before.
     */
    fun onTap(empty: Boolean, voiceGuide: Boolean, canHear: Boolean): Tap = when {
        empty && ask(canHear) -> Tap.ASK
        voiceGuide -> Tap.DESCRIBE
        else -> Tap.NOTHING
    }

    companion object {
        fun question(lang: Lang): String =
            if (lang == Lang.KO) "이 화면 사용법을 알려 드릴까요?" else "Do you want instructions for this screen?"
    }
}

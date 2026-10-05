package com.nungil.core.search

import com.nungil.contract.DirectionStyle
import com.nungil.contract.Lang
import com.nungil.core.ui.Bearings
import com.nungil.core.lang.Josa

/** Everything the search screens say, in both languages (team plan §5). */
object SearchPhrases {
    fun looking(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "Looking for $name."
        Lang.KO -> "${Josa.eulReul(name)} 찾고 있어요."
    }

    fun lost(lang: Lang): String = when (lang) {
        Lang.EN -> "Lost it."
        Lang.KO -> "놓쳤어요."
    }

    /** Where the target is, as every screen says directions (Bearings): "Cup slightly left.", "Cup at 1 o'clock." */
    fun where(name: String, zone: Zone, style: DirectionStyle, lang: Lang): String {
        val w = Bearings.say(bearing(zone), style, lang)
        return when (lang) {
            Lang.EN -> "$name $w."
            Lang.KO -> "${Josa.iGa(name)} $w 있어요."
        }
    }

    /**
     * A zone of the frame (SearchGuide.zone, fifths) as a bearing: the middle of each fifth of a 65° view. The two
     * outer fifths are "on your left / right", the inner ones "slightly"; they were "far left" and "on your left".
     */
    fun bearing(zone: Zone): Float = when (zone) {
        Zone.FAR_LEFT -> -FAR_DEG
        Zone.LEFT -> -NEAR_DEG
        Zone.AHEAD -> 0f
        Zone.RIGHT -> NEAR_DEG
        Zone.FAR_RIGHT -> FAR_DEG
    }

    private const val NEAR_DEG = 13f
    private const val FAR_DEG = 26f

    fun unknown(lang: Lang): String = when (lang) {
        Lang.EN -> "I don't know that. Say it another way."
        Lang.KO -> "잘 모르겠어요. 다르게 말해 주세요."
    }

    fun askWhat(lang: Lang): String = when (lang) {
        Lang.EN -> "What should I find?"
        Lang.KO -> "무엇을 찾을까요?"
    }
}

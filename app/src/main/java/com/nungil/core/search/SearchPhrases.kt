package com.nungil.core.search

import com.nungil.contract.Lang
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

    fun where(name: String, zone: Zone, lang: Lang): String = when (lang) {
        Lang.EN -> when (zone) {
            Zone.AHEAD -> "$name ahead."
            Zone.LEFT -> "$name on your left."
            Zone.RIGHT -> "$name on your right."
            Zone.FAR_LEFT -> "$name far left."
            Zone.FAR_RIGHT -> "$name far right."
        }
        Lang.KO -> "${Josa.iGa(name)} ${SearchGuide.zoneWord(zone, lang)}에 있어요."
    }

    fun unknown(lang: Lang): String = when (lang) {
        Lang.EN -> "I don't know that. Say it another way."
        Lang.KO -> "잘 모르겠어요. 다르게 말해 주세요."
    }

    fun askWhat(lang: Lang): String = when (lang) {
        Lang.EN -> "What should I find?"
        Lang.KO -> "무엇을 찾을까요?"
    }
}

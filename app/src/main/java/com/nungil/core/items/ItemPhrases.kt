package com.nungil.core.items

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa

/** What item enrolment says, in both languages. */
object ItemPhrases {
    fun start(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "Learning $name. Point the camera at it."
        Lang.KO -> "${Josa.eulReul(name)} 등록할게요. 카메라로 비춰 주세요."
    }

    fun prompt(step: ItemStep, lang: Lang): String = when (lang) {
        Lang.EN -> when (step) {
            ItemStep.STILL -> "Hold the phone still, pointing at it."
            ItemStep.LEFT -> "Move the phone a little to the left."
            ItemStep.RIGHT -> "Now a little to the right."
        }
        Lang.KO -> when (step) {
            ItemStep.STILL -> "물건을 향해 휴대폰을 가만히 들어 주세요."
            ItemStep.LEFT -> "휴대폰을 왼쪽으로 조금 옮겨 주세요."
            ItemStep.RIGHT -> "이제 오른쪽으로 조금 옮겨 주세요."
        }
    }

    fun noItem(lang: Lang): String = when (lang) {
        Lang.EN -> "I can't see it. Point the camera at it from about an arm's length."
        Lang.KO -> "물건이 보이지 않아요. 팔 길이 정도 떨어져서 비춰 주세요."
    }

    fun done(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "All done. I will remember $name."
        Lang.KO -> "다 됐어요. ${Josa.eulReul(name)} 기억할게요."
    }
}

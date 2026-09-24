package com.nungil.core.people

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa

/** What face enrolment says, in both languages. Spoken to the person being enrolled. */
object EnrollPhrases {
    fun start(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "Learning $name's face."
        Lang.KO -> "$name 얼굴을 등록할게요."
    }

    fun prompt(pose: Pose, lang: Lang): String = when (lang) {
        Lang.EN -> when (pose) {
            Pose.STRAIGHT -> "Look straight at the phone."
            Pose.LEFT -> "Slowly turn your head to one side."
            Pose.RIGHT -> "Now turn to the other side."
            Pose.UP -> "Tilt your head up a little."
            Pose.DOWN -> "Tilt your head down a little."
        }
        Lang.KO -> when (pose) {
            Pose.STRAIGHT -> "휴대폰을 똑바로 바라봐 주세요."
            Pose.LEFT -> "고개를 한쪽으로 천천히 돌려 주세요."
            Pose.RIGHT -> "이제 반대쪽으로 돌려 주세요."
            Pose.UP -> "고개를 살짝 들어 주세요."
            Pose.DOWN -> "고개를 살짝 숙여 주세요."
        }
    }

    fun percent(percent: Int, lang: Lang): String = when (lang) {
        Lang.EN -> "$percent percent"
        Lang.KO -> "${percent}퍼센트"
    }

    fun done(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "All done. I will remember $name."
        Lang.KO -> "다 됐어요. ${Josa.eulReul(name)} 기억할게요."
    }

    fun noFace(lang: Lang): String = when (lang) {
        Lang.EN -> "I can't see a face. Hold the phone at face height."
        Lang.KO -> "얼굴이 보이지 않아요. 휴대폰을 얼굴 높이로 들어 주세요."
    }

    fun paused(lang: Lang): String = when (lang) {
        Lang.EN -> "Paused. Nothing is saved until the end."
        Lang.KO -> "잠시 멈췄어요. 끝까지 해야 저장돼요."
    }
}

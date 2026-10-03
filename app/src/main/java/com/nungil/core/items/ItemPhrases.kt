package com.nungil.core.items

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa

/** What item enrolment says, in both languages. */
object ItemPhrases {
    fun start(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "Learning $name. Point the camera at it and hold still."
        Lang.KO -> "${Josa.eulReul(name)} 등록할게요. 물건을 향해 비추고 가만히 들어 주세요."
    }

    fun prompt(step: ItemStep, lang: Lang): String = when (lang) {
        Lang.EN -> when (step) {
            ItemStep.STILL -> "Hold the phone still."
            ItemStep.LEFT -> "Move the phone a little to the left."
            ItemStep.RIGHT -> "Now to the right, past where you started."
            ItemStep.UP -> "Now lift the phone a little."
        }
        Lang.KO -> when (step) {
            ItemStep.STILL -> "휴대폰을 가만히 들어 주세요."
            ItemStep.LEFT -> "휴대폰을 왼쪽으로 조금 옮겨 주세요."
            ItemStep.RIGHT -> "이제 처음 자리를 지나 오른쪽으로 옮겨 주세요."
            ItemStep.UP -> "이제 휴대폰을 조금 위로 올려 주세요."
        }
    }

    /**
     * The question before learning: what the thing in view looks like, so that someone who cannot see the
     * screen knows which thing it is, and whether it is the right one. Null when nothing about it is known.
     */
    fun ask(look: ItemLook, lang: Lang): String? {
        val it = look(look, lang) ?: return null
        return when (lang) {
            Lang.EN -> "I see something. $it Is this it? Say yes or no."
            Lang.KO -> "물건이 보여요. $it 이것인가요? 네 또는 아니요라고 말해 주세요."
        }
    }

    /** "Yes": learning begins. */
    fun confirmed(lang: Lang): String = when (lang) {
        Lang.EN -> "Great! Hold the phone still."
        Lang.KO -> "좋아요! 휴대폰을 가만히 들어 주세요."
    }

    /** "No": the camera is on something else. */
    fun notThat(lang: Lang): String = when (lang) {
        Lang.EN -> "Point the camera at the right thing and hold still."
        Lang.KO -> "맞는 물건을 향해 비추고 가만히 들어 주세요."
    }

    /** The thing left the view, or something else took its place, while it was being learned. */
    fun lost(lang: Lang): String = when (lang) {
        Lang.EN -> "I lost it. Point the camera at it again."
        Lang.KO -> "놓쳤어요. 다시 비춰 주세요."
    }

    /**
     * What the thing looks like: "It is black and round, about 20 centimetres across, about 40 centimetres
     * away." Null when nothing about it is known.
     */
    fun look(look: ItemLook, lang: Lang): String? = when (lang) {
        Lang.EN -> lookEn(look)
        Lang.KO -> lookKo(look)
    }

    private fun lookEn(look: ItemLook): String? {
        val shape = when (look.shape) {
            ItemShape.ROUND -> "round"
            ItemShape.SQUARE -> "square"
            ItemShape.OBLONG -> "oblong"
            ItemShape.RECTANGULAR -> "rectangular"
            ItemShape.LONG -> "long and narrow"
            null -> null
        }
        val color = look.color?.en
        val what = when {
            color != null && look.shape == ItemShape.LONG -> "$color, $shape"
            color != null && shape != null -> "$color and $shape"
            else -> color ?: shape
        }
        val size = when {
            look.lengthCm == null -> null
            look.widthCm == null || look.widthCm == look.lengthCm -> "about ${look.lengthCm} centimetres across"
            else -> "about ${look.lengthCm} by ${look.widthCm} centimetres"
        }
        val away = look.distanceCm?.let { cm ->
            when {
                cm < 100 -> "about $cm centimetres away"
                cm == 100 -> "about 1 metre away"
                else -> "about ${metres(cm)} metres away"
            }
        }
        val parts = listOfNotNull(what, size, away)
        return if (parts.isEmpty()) null else "It is ${parts.joinToString(", ")}."
    }

    private fun lookKo(look: ItemLook): String? {
        val shape = when (look.shape) {
            ItemShape.ROUND -> "둥근 모양"
            ItemShape.SQUARE -> "네모난 모양"
            ItemShape.OBLONG -> "약간 길쭉한 모양"
            ItemShape.RECTANGULAR -> "직사각형 모양"
            ItemShape.LONG -> "길쭉한 모양"
            null -> null
        }
        val color = look.color?.koNoun
        val what = when {
            color != null && shape != null -> "${color}이고 ${shape}이에요."
            color != null -> "${color}이에요."
            shape != null -> "${shape}이에요."
            else -> null
        }
        val size = when {
            look.lengthCm == null -> null
            look.widthCm == null || look.widthCm == look.lengthCm -> "크기는 약 ${look.lengthCm}센티미터"
            else -> "길이 약 ${look.lengthCm}센티미터, 폭 약 ${look.widthCm}센티미터"
        }
        val away = look.distanceCm?.let { cm -> if (cm < 100) "거리는 약 ${cm}센티미터" else "거리는 약 ${metres(cm)}미터" }
        val measures = listOfNotNull(size, away).takeIf { it.isNotEmpty() }?.let { it.joinToString(", ") + "예요." }
        val parts = listOfNotNull(what, measures)
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    /** 100 -> "1", 150 -> "1.5". */
    private fun metres(cm: Int): String = if (cm % 100 == 0) "${cm / 100}" else "${cm / 100}.${cm % 100 / 10}"

    fun noItem(lang: Lang): String = when (lang) {
        Lang.EN -> "I can't see anything there. Point the camera at it from about an arm's length."
        Lang.KO -> "물건이 보이지 않아요. 팔 길이 정도 떨어져서 비춰 주세요."
    }

    fun done(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "All done. I will remember $name."
        Lang.KO -> "다 됐어요. ${Josa.eulReul(name)} 기억할게요."
    }
}

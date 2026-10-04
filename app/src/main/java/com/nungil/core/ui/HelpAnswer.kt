package com.nungil.core.ui

/**
 * The answer to "Do you want instructions for this screen?" (TapHelpOffer): true for yes in any of its usual
 * words, false for no, null for anything else. Whole answers only, as GoQuestion: "go to the park" is not a
 * yes. English word for word, Korean without its spaces.
 */
object HelpAnswer {
    private val YES_EN = setOf(
        "yes", "yeah", "yep", "yup", "ya", "sure", "ok", "okay", "alright", "all right", "of course", "correct",
        "right", "go ahead", "go on", "tell me", "please", "please do", "yes please", "please tell me", "yes tell me",
        "ok tell me", "i do", "i want", "i want to", "explain", "read it", "help", "help me", "do it", "sure thing",
        "why not",
    )
    private val YES_KO = setOf(
        "네", "예", "응", "어", "그래", "그래요", "좋아", "좋아요", "알려줘", "알려줘요", "알려주세요", "부탁해", "부탁해요",
        "설명해줘", "설명해주세요", "네알려주세요", "응알려줘", "도와줘", "도와주세요", "들려줘", "듣고싶어",
    )
    private val NO_EN = setOf(
        "no", "nope", "nah", "no thanks", "no thank you", "not now", "later", "maybe later", "never mind",
        "nevermind", "dont", "do not", "im fine", "i am fine", "no need", "not needed", "skip", "cancel",
    )
    private val NO_KO = setOf(
        "아니", "아니요", "아니야", "아니오", "괜찮아", "괜찮아요", "됐어", "됐어요", "나중에", "필요없어", "필요없어요",
        "안돼", "싫어",
    )

    fun of(text: String): Boolean? {
        val words = text.lowercase().replace("'", "").replace("’", "")
            .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val spaced = words.joinToString(" ")
        val joined = words.joinToString("")
        return when {
            spaced in YES_EN || joined in YES_KO -> true
            spaced in NO_EN || joined in NO_KO -> false
            else -> null
        }
    }
}

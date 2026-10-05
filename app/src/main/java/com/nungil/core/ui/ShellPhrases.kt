package com.nungil.core.ui

import com.nungil.contract.Lang

/** Sentences the app shell speaks. */
enum class Phrase {
    NOT_UNDERSTOOD,
    NOT_HERE,
    OPENING_LIVE_SCAN,
    LANGUAGE_SET,
    VOICE_ON,
    VOICE_OFF,
    LEARNER_ON,
    LEARNER_OFF,
    CLOCK_DIRECTIONS_ON,
    CLOCK_DIRECTIONS_OFF,
    NOTHING_TO_REPEAT,
    HELP_GENERAL,
    MIC_NEEDED,
    MIC_BLOCKED,
    VOICE_UNAVAILABLE,
    KOREAN_RECOGNITION_MISSING,
    LISTENING,
}

/** English and Korean (해요체) text of every shell sentence. */
object ShellPhrases {
    private val en = mapOf(
        Phrase.NOT_UNDERSTOOD to "I did not understand.",
        Phrase.NOT_HERE to "That does not work on this screen.",
        Phrase.OPENING_LIVE_SCAN to "Opening live scan. Point the phone at it.",
        Phrase.LANGUAGE_SET to "Language: English.",
        Phrase.VOICE_ON to "Voice commands on. Say help to hear what you can say.",
        Phrase.VOICE_OFF to "Voice commands off.",
        Phrase.LEARNER_ON to "Learner mode on. I will explain each screen.",
        Phrase.LEARNER_OFF to "Learner mode off.",
        Phrase.CLOCK_DIRECTIONS_ON to "Directions as clock hours.",
        Phrase.CLOCK_DIRECTIONS_OFF to "Directions in words.",
        Phrase.NOTHING_TO_REPEAT to "Nothing to repeat yet.",
        Phrase.HELP_GENERAL to "You can say: look around, find and the name of a thing, saved, settings, or help.",
        Phrase.MIC_NEEDED to "Voice commands need the microphone permission.",
        Phrase.MIC_BLOCKED to "The microphone is blocked. Allow it in the app settings.",
        Phrase.VOICE_UNAVAILABLE to "Voice input is not available on this phone.",
        Phrase.KOREAN_RECOGNITION_MISSING to "Korean speech recognition is not installed, so I will listen in English.",
        Phrase.LISTENING to "Listening.",
    )

    private val ko = mapOf(
        Phrase.NOT_UNDERSTOOD to "잘 못 알아들었어요.",
        Phrase.NOT_HERE to "이 화면에서는 할 수 없어요.",
        Phrase.OPENING_LIVE_SCAN to "실시간 안내를 열게요. 휴대폰을 그쪽으로 향해 주세요.",
        Phrase.LANGUAGE_SET to "언어를 한국어로 바꿨어요.",
        Phrase.VOICE_ON to "음성 명령을 켰어요. 무엇을 말할 수 있는지 들으려면 도움말이라고 말해 주세요.",
        Phrase.VOICE_OFF to "음성 명령을 껐어요.",
        Phrase.LEARNER_ON to "학습 모드를 켰어요. 화면마다 설명해 드릴게요.",
        Phrase.LEARNER_OFF to "학습 모드를 껐어요.",
        Phrase.CLOCK_DIRECTIONS_ON to "방향을 시계 방향으로 말할게요.",
        Phrase.CLOCK_DIRECTIONS_OFF to "방향을 말로 알려 드릴게요.",
        Phrase.NOTHING_TO_REPEAT to "아직 다시 들려드릴 말이 없어요.",
        Phrase.HELP_GENERAL to "주변 둘러보기, 무엇 찾아줘, 저장한 것, 설정, 도움말이라고 말해 보세요.",
        Phrase.MIC_NEEDED to "음성 명령을 쓰려면 마이크 권한이 필요해요.",
        Phrase.MIC_BLOCKED to "마이크가 막혀 있어요. 앱 설정에서 허용해 주세요.",
        Phrase.VOICE_UNAVAILABLE to "이 휴대폰에서는 음성 입력을 쓸 수 없어요.",
        Phrase.KOREAN_RECOGNITION_MISSING to "한국어 음성 인식이 설치되어 있지 않아서 영어로 들을게요.",
        Phrase.LISTENING to "듣고 있어요.",
    )

    fun text(phrase: Phrase, lang: Lang): String =
        (if (lang == Lang.KO) ko else en).getValue(phrase)
}

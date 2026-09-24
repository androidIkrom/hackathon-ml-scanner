package com.nungil.core.ui

import com.nungil.contract.Lang
import java.util.Locale

/**
 * Spoken help. One entry per navigation destination (keyed by its id name, e.g. "search_camera") plus
 * general topics, in English and Korean. "help" speaks the current screen; "what is walk mode" a topic.
 */
object ScreenHelp {
    private class Text(val en: String, val ko: String)

    private val screens: Map<String, Text> = mapOf(
        "home" to Text(
            "Home. Look around, find something, or open what you saved. The button at the bottom turns voice commands on.",
            "홈 화면이에요. 주변 둘러보기, 물건 찾기, 저장한 것을 열 수 있어요. 맨 아래 버튼으로 음성 명령을 켤 수 있어요.",
        ),
        "onboarding" to Text(
            "Welcome. Listen to the introduction, then press start at the bottom.",
            "시작 화면이에요. 소개를 듣고 아래의 시작하기를 눌러 주세요.",
        ),
        "scan_hub" to Text(
            "Scan menu. Choose look around, live scan, walk mode or history.",
            "둘러보기 메뉴예요. 주변 둘러보기, 실시간 안내, 걷기 모드, 기록 중에서 골라 주세요.",
        ),
        "settings" to Text(
            "Settings. Choose the camera, the model, how sure I must be, the language and high contrast.",
            "설정 화면이에요. 카메라, 모델, 확신도, 언어, 고대비 화면을 바꿀 수 있어요.",
        ),
        "history" to Text(
            "History. Tap a scan to hear it again. Touch and hold to delete it.",
            "기록 화면이에요. 누르면 다시 들려드리고, 길게 누르면 지울 수 있어요.",
        ),
        "scan" to Text(
            "Look around. Hold the phone upright and turn slowly in a full circle. I will tell you what is around you.",
            "주변 둘러보기 화면이에요. 휴대폰을 세우고 천천히 한 바퀴 돌면 주변을 알려드려요.",
        ),
        "walk" to Text(
            "Walk mode. Hold the phone in front of you while walking. I warn about obstacles, steps and walls.",
            "걷기 모드예요. 걸을 때 휴대폰을 앞으로 들면 장애물, 계단, 벽을 알려드려요.",
        ),
        "search" to Text(
            "Find. Say or type what to look for, for example my bag.",
            "찾기 화면이에요. 찾을 것을 말하거나 입력해 주세요. 예를 들어 가방이라고 말해 보세요.",
        ),
        "search_camera" to Text(
            "Finding. Move the phone slowly. The beeps get faster as you point at it, and the phone vibrates when it is straight ahead.",
            "찾는 중이에요. 휴대폰을 천천히 움직여 주세요. 가까워질수록 소리가 빨라지고, 정면에 오면 진동이 울려요.",
        ),
        "saved" to Text(
            "Saved. People, cars and objects you taught me. Add new ones with the button at the bottom.",
            "저장한 것 화면이에요. 알려주신 사람, 자동차, 물건이 있어요. 아래 버튼으로 새로 추가할 수 있어요.",
        ),
        "person" to Text(
            "A saved person. You can rename or delete them.",
            "저장한 사람이에요. 이름을 바꾸거나 지울 수 있어요.",
        ),
        "add_person" to Text(
            "Add a person. Say or type their name, then continue.",
            "사람 추가 화면이에요. 이름을 말하거나 입력한 뒤 다음을 눌러 주세요.",
        ),
        "enroll" to Text(
            "Learn a face. Point the camera at the face and follow the spoken steps: straight, left, right, up and down.",
            "얼굴 등록 화면이에요. 카메라를 얼굴에 맞추고 안내에 따라 정면, 왼쪽, 오른쪽, 위, 아래를 보여 주세요.",
        ),
        "item" to Text(
            "A saved item. You can rename or delete it.",
            "저장한 물건이에요. 이름을 바꾸거나 지울 수 있어요.",
        ),
        "add_item" to Text(
            "Add an item. Say or type its name, then continue.",
            "물건 추가 화면이에요. 이름을 말하거나 입력한 뒤 다음을 눌러 주세요.",
        ),
        "item_enroll" to Text(
            "Learn an item. Hold the item in front of the camera, keep still, then move it left and right.",
            "물건 등록 화면이에요. 물건을 카메라 앞에 두고 가만히 있다가 왼쪽과 오른쪽으로 움직여 주세요.",
        ),
        "reader" to Text(
            "Read text. Point the camera at printed text or a QR code and I will read it.",
            "글자 읽기 화면이에요. 카메라를 글자나 QR 코드에 맞추면 읽어 드려요.",
        ),
    )

    private val topics: Map<String, Text> = mapOf(
        "voice" to Text(
            "Voice commands: press the microphone button on Home, then just speak. Say stop listening to turn it off.",
            "음성 명령: 홈의 마이크 버튼을 누르고 말씀하세요. 끄려면 듣기 중지라고 말해 주세요.",
        ),
        "language" to Text(
            "Say Korean or English to switch the language, or change it in Settings.",
            "한국어나 영어라고 말하면 언어가 바뀌어요. 설정에서도 바꿀 수 있어요.",
        ),
        "learner" to Text(
            "Learner mode explains each screen when it opens.",
            "학습 모드는 화면이 열릴 때마다 설명해 드려요.",
        ),
        "contrast" to Text(
            "High contrast shows white and yellow on black. Turn it on in Settings.",
            "고대비 화면은 검은 바탕에 흰색과 노란색으로 보여 줘요. 설정에서 켤 수 있어요.",
        ),
        "faces" to Text(
            "Save a person's face under Saved, then I say their name when I see them.",
            "저장한 것에서 사람 얼굴을 등록하면, 보일 때 이름을 말해 드려요.",
        ),
        "coverage" to Text(
            "The ring shows how much of the room you have turned through. The scan ends when it is full.",
            "원은 방을 얼마나 돌았는지 보여 줘요. 원이 다 차면 둘러보기가 끝나요.",
        ),
        "live" to Text(
            "Live scan tells you about things as soon as the camera finds them.",
            "실시간 안내는 카메라가 찾는 즉시 알려드려요.",
        ),
    )

    /** Spoken words to topic keys; longer phrases are checked first. Keys may also be screen names. */
    private val words: List<Pair<String, String>> = listOf(
        "live scan" to "live", "live" to "live", "실시간" to "live",
        "walk mode" to "walk", "walk" to "walk", "걷기" to "walk", "보행" to "walk",
        "full scan" to "scan", "look around" to "scan", "scan" to "scan", "둘러보기" to "scan", "스캔" to "scan",
        "search" to "search", "find" to "search", "찾기" to "search", "검색" to "search",
        "saved" to "saved", "저장" to "saved",
        "history" to "history", "기록" to "history",
        "settings" to "settings", "설정" to "settings",
        "voice" to "voice", "microphone" to "voice", "음성" to "voice", "마이크" to "voice",
        "language" to "language", "언어" to "language",
        "learner" to "learner", "학습" to "learner",
        "contrast" to "contrast", "고대비" to "contrast",
        "read" to "reader", "글자" to "reader", "qr" to "reader",
        "face" to "faces", "얼굴" to "faces",
        "ring" to "coverage", "coverage" to "coverage", "percent" to "coverage", "퍼센트" to "coverage",
    ).sortedByDescending { it.first.length }

    fun hasScreen(screen: String): Boolean = screen in screens

    fun forScreen(screen: String, lang: Lang): String =
        screens[screen]?.pick(lang) ?: ShellPhrases.text(Phrase.HELP_GENERAL, lang)

    /** Help for a spoken topic ("walk mode", "검색"), or null when the topic is unknown. */
    fun forTopic(topic: String, lang: Lang): String? {
        val key = topicOf(topic) ?: return null
        return (topics[key] ?: screens[key])?.pick(lang)
    }

    fun topicOf(text: String): String? {
        val t = text.lowercase(Locale.ROOT)
        return words.firstOrNull { t.contains(it.first) }?.second
    }

    private fun Text.pick(lang: Lang) = if (lang == Lang.KO) ko else en
}

/** The twenty-second introduction spoken on first launch. */
object OnboardingText {
    fun intro(lang: Lang): String = if (lang == Lang.KO) {
        "안녕하세요, 눈길이에요. 인터넷 없이 이 휴대폰만으로 주변에 무엇이 있는지 알려드려요. " +
            "주변 둘러보기라고 말하거나 홈의 첫 번째 카드를 누른 뒤 천천히 한 바퀴 돌아 보세요. " +
            "가방 찾아줘처럼 말하면 소리를 따라 찾을 수 있어요. " +
            "저장한 것이라고 말하면 사람과 물건을 알려 줄 수 있어요. " +
            "아래의 시작하기를 눌러 시작해 주세요."
    } else {
        "Hello, I am Nungil. I tell you what is around you, using only this phone, even without internet. " +
            "Say look around, or tap the first card on the home screen, then turn slowly in a circle. " +
            "Say find and a thing, like find my bag, and follow the beeps. " +
            "Say saved to teach me people and things. " +
            "Press start at the bottom to begin."
    }
}

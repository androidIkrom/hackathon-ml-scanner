package com.nungil.core.voice

import com.nungil.contract.Dest
import com.nungil.contract.Facing
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand
import java.util.Locale

/**
 * Turns one recognised phrase (English or Korean) into a [VoiceCommand] by keywords, never by whole
 * sentences (build guide §10.5). Rules run in this order, first match wins:
 *
 * 1. microphone words ("stop listening", "마이크 꺼"), so they can never stop a scan instead;
 * 2. search with a payload ("find my bag", "가방 찾아줘");
 * 3. questions (who / what is this, help, "what is <topic>");
 * 4. adding a person, car or object, with the words that are not command words as the name;
 * 5. clock directions, learner mode, then language;
 * 6. actions: stop, switch camera, read text, delete, back (verbs beat destinations: "stop full scan" stops);
 * 7. destinations, including known mishearings ("safe", "working mode", "fool scan", "light skin", bare "person");
 * 8. start and repeat;
 * 9. anything else is Unknown with the original text (dictation for names and queries).
 */
object VoiceCommandParser {

    fun parse(text: String): VoiceCommand {
        val original = clean(text)
        if (original.isEmpty()) return VoiceCommand.Unknown("")
        val s = Said(original)
        return micOff(s)
            ?: search(s)
            ?: question(s)
            ?: adding(s)
            ?: clockDirections(s)
            ?: learner(s)
            ?: language(s)
            ?: action(s)
            ?: destination(s)
            ?: late(s)
            ?: VoiceCommand.Unknown(original)
    }

    /** Drops apostrophes ("where's" -> "wheres"), turns other punctuation into spaces, collapses spaces. */
    private fun clean(text: String): String =
        text.replace("'", "").replace("’", "")
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .trim()
            .replace(Regex(" +"), " ")

    private class Said(val original: String) {
        val raw: List<String> = original.split(' ')
        val words: List<String> = original.lowercase(Locale.ROOT).split(' ')
        val compact: String = words.joinToString("")

        fun has(vararg w: String): Boolean = w.any { it in words }

        fun seq(vararg w: String): Boolean =
            (0..words.size - w.size).any { i -> w.indices.all { words[i + it] == w[it] } }

        fun ko(vararg k: String): Boolean = k.any { compact.contains(it) }
    }

    // 1 ------------------------------------------------------------------------------------------------
    private fun micOff(s: Said): VoiceCommand? {
        val en = s.has("listening", "microphone") || s.seq("mic", "off") || s.seq("voice", "off") || s.seq("stop", "voice") ||
            s.seq("stop", "hearing") || s.seq("dont", "listen") || (s.has("mic") && s.has("mute", "off")) ||
            (s.has("voice") && s.has("off", "disable"))
        val ko = s.ko(
            "듣기중지", "듣기그만", "듣지마", "듣지말", "그만들어", "마이크꺼", "마이크끄", "마이크중지",
            "음성꺼", "음성끄", "음성명령꺼", "음성명령끄",
        )
        return if (en || ko) VoiceCommand.StopListening else null
    }

    // 2 ------------------------------------------------------------------------------------------------
    private val vagueKo = setOf("물건", "것", "뭐", "무엇", "뭔가")
    private val searchFillersKo = setOf("줘", "좀", "있어", "있니", "있어요", "있나요", "해줘", "봐", "줄래")

    private fun search(s: Said): VoiceCommand? {
        englishSearch(s)?.let { return it }
        return koreanSearch(s)
    }

    private fun englishSearch(s: Said): VoiceCommand? {
        val w = s.words
        // "what is search" is a help question, not a search.
        if (w.first() in setOf("what", "whats", "how", "explain")) return null
        for (i in w.indices) {
            var start = when {
                w[i] == "find" || w[i] == "locate" -> i + 1
                w[i] == "search" -> i + 1
                w[i] == "look" && w.getOrNull(i + 1) == "for" -> i + 2
                w[i] == "where" || w[i] == "wheres" -> i + 1
                else -> continue
            }
            if (w[i] == "where" && w.getOrNull(start) == "am") return null // "where am I" is help
            if (w[i] == "search" && w.getOrNull(start) == "for") start++
            if (w[i] == "where" && w.getOrNull(start) in setOf("is", "are")) start++
            // "where did I put my keys": the thing comes after put / leave.
            if (w[i] == "where" && w.getOrNull(start) == "did") {
                val verb = w.indexOfFirst { it in setOf("put", "leave", "left", "drop", "place", "keep") }
                if (verb > start) start = verb + 1
            }
            return goSearch(s.raw.drop(start).joinToString(" "))
        }
        return null
    }

    private fun koreanSearch(s: Said): VoiceCommand? {
        val j = s.raw.indexOfFirst { it.contains("찾") || it.contains("어디") || it.contains("어딨") }
        if (j < 0) return null
        val trigger = s.raw[j]
        val cut = listOf(trigger.indexOf("찾"), trigger.indexOf("어디"), trigger.indexOf("어딨")).filter { it >= 0 }.min()
        val before = (s.raw.take(j) + trigger.substring(0, cut)).filter { it.isNotBlank() }
        val payload = if (before.isNotEmpty()) {
            before
        } else {
            s.raw.drop(j + 1).filter { it !in searchFillersKo }
        }
        val query = payload.joinToString(" ")
        if (query == "여기" || query == "여기가") return VoiceCommand.Help(null)
        return goSearch(if (query in vagueKo) "" else query)
    }

    private fun goSearch(query: String): VoiceCommand =
        VoiceCommand.Go(Dest.Search(query.trim().ifEmpty { null }))

    // 3 ------------------------------------------------------------------------------------------------
    private val topicParticlesKo = listOf("이란", "란", "이", "가", "은", "는")

    private fun question(s: Said): VoiceCommand? {
        val w = s.words
        val helpScreen = s.seq("what", "is", "this", "screen") || s.seq("whats", "this", "screen") ||
            s.seq("where", "am", "i") || s.seq("what", "can", "i", "say") || s.seq("which", "screen") ||
            s.seq("what", "can", "you", "do") || s.has("commands", "instructions") ||
            s.seq("how", "do", "i", "use") || s.seq("how", "to", "use") ||
            s.ko(
                "이화면", "여기어디", "뭐라고말", "무슨말", "도움말", "도와주", "도와줘", "명령어", "사용법",
                "뭐할수있", "어떻게써", "어떻게사용",
            )
        if (helpScreen) return VoiceCommand.Help(null)

        // "what is around me", "주변에 뭐 있어": the answer is a full scan.
        val around = s.seq("what", "is", "around") || s.seq("whats", "around") || s.seq("what", "is", "near") ||
            s.seq("whats", "near") || (s.has("describe") && s.has("room", "surroundings", "around")) ||
            s.ko("주변에뭐", "주위에뭐", "근처에뭐", "주변뭐")
        if (around) return VoiceCommand.Go(Dest.Scan(ScanMode.FULL))

        val who = s.seq("who", "is", "this") || s.seq("who", "is", "that") || s.seq("who", "is", "it") ||
            s.seq("whos", "this") || s.seq("whos", "that") || s.ko("누구", "누군") ||
            (w.first() in setOf("who", "whos") && s.has("there", "here", "front", "see", "looking"))
        if (who) return VoiceCommand.WhoIsThis

        val what = s.seq("what", "is", "this") || s.seq("what", "is", "that") || s.seq("whats", "this") ||
            s.seq("whats", "that") || s.seq("what", "am", "i", "looking", "at") ||
            s.seq("what", "is", "it") || s.seq("whats", "it") || s.seq("tell", "me", "what") ||
            (s.has("what", "whats") && (s.seq("in", "front") || s.has("see"))) || s.has("identify", "describe") ||
            s.ko("이게뭐", "이거뭐", "이건뭐", "이것뭐", "뭐야이거", "뭐야이게", "앞에뭐", "뭐가보여", "뭐보여")
        if (what) return VoiceCommand.WhatIsThis

        val topicWords = when {
            w.size > 2 && w[0] == "what" && w[1] == "is" -> w.drop(2)
            w.size > 1 && w[0] == "whats" -> w.drop(1)
            w.size > 3 && w[0] == "how" && w[1] == "does" && w.last() == "work" -> w.subList(2, w.size - 1)
            w.size > 1 && w[0] == "explain" -> w.drop(1)
            else -> null
        }
        if (topicWords != null) {
            val topic = topicWords.dropWhile { it == "the" || it == "a" }.joinToString(" ")
            if (topic.isNotEmpty()) return VoiceCommand.Help(topic)
        }

        if (!s.ko("뭐라고")) {
            val k = s.raw.indexOfFirst { it.contains("뭐") || it.contains("설명") }
            if (k == 0) return VoiceCommand.WhatIsThis
            if (k > 0) {
                val parts = s.raw.take(k).toMutableList()
                val last = parts.last()
                val particle = topicParticlesKo.firstOrNull { last.endsWith(it) && last.length > it.length }
                if (particle != null) parts[parts.size - 1] = last.dropLast(particle.length)
                return VoiceCommand.Help(parts.joinToString(" "))
            }
        }

        return if (s.has("help")) VoiceCommand.Help(null) else null
    }

    // 4 ------------------------------------------------------------------------------------------------
    private enum class Kind { PERSON, CAR, OBJECT }

    private val addVerbsEn = setOf(
        "add", "create", "new", "register", "save", "make", "remember", "teach", "learn",
        "enroll", "enrol", "memorize", "memorise", "store",
    )
    private val personEn = setOf(
        "person", "people", "persons", "face", "faces", "friend", "friends", "someone", "man", "woman", "human",
    )
    private val carEn = setOf("car", "cars", "vehicle", "vehicles")
    private val objectEn = setOf(
        "object", "objects", "item", "items", "thing", "things", "stuff", "belonging", "belongings",
    )
    private val nameLeadFillersEn = setOf("called", "named", "as", "is", "a", "an", "the", "name", "whose")
    private val betweenFillersEn = setOf("a", "an", "the", "new", "this", "that", "my")
    private val nameFillersKo = setOf("좀", "줘", "해줘", "해", "해주세요", "주세요", "이름은", "이름", "새", "새로", "이", "그", "저")
    private val nameSuffixesKo = listOf("이라는", "라는", "이라고", "라고", "을", "를")

    private fun kindEn(word: String): Kind? = when (word) {
        in personEn -> Kind.PERSON
        in carEn -> Kind.CAR
        in objectEn -> Kind.OBJECT
        else -> null
    }

    private fun kindKo(word: String): Kind? = when {
        word.contains("사람") || word.contains("얼굴") || word.contains("친구") || word.contains("가족") ||
            word.contains("지인") -> Kind.PERSON
        word.contains("자동차") || word == "차" || word.startsWith("차를") || word.startsWith("차로") -> Kind.CAR
        word.contains("물건") || word.contains("사물") || word.contains("소지품") -> Kind.OBJECT
        else -> null
    }

    private fun isAddVerbKo(word: String): Boolean =
        word.contains("추가") || word.contains("등록") || word.contains("만들") || word.contains("기억해") ||
            word.contains("저장해") || word.contains("저장하") || word == "새" || word == "새로"

    private fun adding(s: Said): VoiceCommand? {
        val w = s.words
        val verbEn = w.indexOfFirst { it in addVerbsEn }
        val kindIdxEn = w.indexOfFirst { kindEn(it) != null }
        if (verbEn >= 0 && kindIdxEn >= 0) {
            val after = s.raw.drop(kindIdxEn + 1).dropWhile { it.lowercase(Locale.ROOT) in nameLeadFillersEn }
            val name = if (after.isNotEmpty()) {
                after
            } else {
                s.raw.subList(minOf(verbEn + 1, kindIdxEn), kindIdxEn)
                    .filter { it.lowercase(Locale.ROOT) !in betweenFillersEn }
            }
            return add(kindEn(w[kindIdxEn])!!, name.joinToString(" "))
        }

        val verbKo = s.raw.indexOfFirst { isAddVerbKo(it) }
        val kindIdxKo = s.raw.indexOfFirst { kindKo(it) != null }
        if (verbKo >= 0 && kindIdxKo >= 0) {
            val name = s.raw.filterIndexed { i, word -> i != verbKo && i != kindIdxKo && word !in nameFillersKo }
                .map { word ->
                    val suffix = nameSuffixesKo.firstOrNull { word.endsWith(it) && word.length > it.length }
                    if (suffix == null) word else word.dropLast(suffix.length)
                }
            return add(kindKo(s.raw[kindIdxKo])!!, name.joinToString(" "))
        }
        return null
    }

    private fun add(kind: Kind, name: String): VoiceCommand {
        val n = name.trim().ifEmpty { null }
        return VoiceCommand.Go(
            when (kind) {
                Kind.PERSON -> Dest.AddPerson(n)
                Kind.CAR -> Dest.AddItem(ItemKind.CAR, n)
                Kind.OBJECT -> Dest.AddItem(ItemKind.OBJECT, n)
            },
        )
    }

    // 5 ------------------------------------------------------------------------------------------------
    /** "Clock directions on / off", "use clock directions", "directions in words"; "시계 방향 켜 / 꺼". */
    private fun clockDirections(s: Said): VoiceCommand? {
        if (s.has("clock") && s.has("direction", "directions", "hours")) {
            return VoiceCommand.ClockDirections(on = !s.has("off", "disable", "stop", "no"))
        }
        if (s.has("direction", "directions") && s.has("words")) return VoiceCommand.ClockDirections(on = false)
        if (s.ko("시계방향")) return VoiceCommand.ClockDirections(on = !s.ko("꺼", "끄", "중지", "그만"))
        return null
    }

    private fun learner(s: Said): VoiceCommand? {
        if (s.has("learner", "learning") || s.seq("tutorial", "mode") || s.seq("practice", "mode") || s.seq("training", "mode")) {
            return VoiceCommand.Learner(on = !s.has("off", "disable", "stop", "end"))
        }
        if (s.ko("학습모드", "배움모드", "연습모드", "튜토리얼")) {
            return VoiceCommand.Learner(on = !s.ko("꺼", "끄", "중지", "그만"))
        }
        return null
    }

    private fun language(s: Said): VoiceCommand? = when {
        s.has("korean") || s.ko("한국어", "한국말") -> VoiceCommand.SetLanguage(Lang.KO)
        s.has("english") || s.ko("영어") -> VoiceCommand.SetLanguage(Lang.EN)
        else -> null
    }

    // 6 ------------------------------------------------------------------------------------------------
    /** "bus stop", "subway stops": the stop is a place, not the command (it stopped walk mode in the logs). */
    private val stopPlacesEn = setOf("bus", "tram", "subway", "metro", "train", "taxi", "shuttle", "truck", "rest")

    private fun stopIsAPlace(s: Said): Boolean =
        s.words.zipWithNext().any { (a, b) -> a in stopPlacesEn && (b == "stop" || b == "stops") } &&
            s.words.count { it == "stop" || it == "stops" } == 1 && s.words.first() !in setOf("stop", "cancel", "pause")

    /**
     * "Front camera" asks for the front one, not the other one: said with the front camera already on, it
     * turned to the back. On the add-person screen it was not taken at all (the logs).
     */
    private fun cameraAskedFor(s: Said): Facing? = when {
        s.has("front", "selfie") || s.ko("전면", "앞카메라", "셀카") -> Facing.FRONT
        s.has("back", "rear") || s.ko("후면", "뒤카메라") -> Facing.BACK
        else -> null
    }

    private fun action(s: Said): VoiceCommand? = when {
        stopIsAPlace(s) -> null
        s.has(
            "stop", "cancel", "pause", "enough", "quiet", "halt", "silence", "shut", "finish",
            "wait", "hold", "mute", "shh", "hush", "end",
        ) ||
            s.ko("멈춰", "멈춤", "멈추", "정지", "그만", "중지", "스톱", "취소", "조용", "끝", "잠깐", "쉿") -> VoiceCommand.Stop
        (s.has("camera") && s.has("switch", "flip", "change", "rotate", "turn", "swap", "other", "reverse", "front", "back", "rear")) ||
            s.has("selfie") || s.words == listOf("camera") ||
            s.ko("카메라전환", "카메라바꿔", "카메라바꾸", "카메라돌려", "카메라변경", "전면카메라", "후면카메라", "셀카") ->
            VoiceCommand.SwitchCamera(cameraAskedFor(s))
        s.has("read", "text", "qr", "barcode", "code", "sign", "signs", "label", "labels", "document", "ocr", "reader") ||
            s.ko("글자", "읽어", "텍스트", "큐알", "바코드", "문자", "표지판", "간판", "리더", "라벨") -> VoiceCommand.ReadText
        s.has("delete", "remove", "erase", "forget", "discard") ||
            s.ko("삭제", "지워", "지우", "없애", "제거") -> VoiceCommand.Delete
        s.has("back", "previous", "exit", "close", "leave", "quit") || (s.has("return") && !s.has("home")) ||
            s.ko("뒤로", "이전", "돌아가", "나가", "닫아") -> VoiceCommand.Back
        else -> null
    }

    // 7 ------------------------------------------------------------------------------------------------
    private val savedEn = setOf("saved", "save", "safe", "saves", "favorites", "favourites")

    /** The logs: "walk" came back as "valk", "scan" as "skin", "live scan" as "light skin", "livescan", "lifespan". */
    private val walkEn = arrayOf("walk", "walking", "walks", "valk", "wok", "wolk", "obstacle", "obstacles", "street")
    private val scanMisheardEn = setOf("skin", "skan", "sken", "scam")
    private val liveMisheardEn = arrayOf("light", "lite", "life", "alive")
    private val liveScanMisheardEn = arrayOf("lifespan", "lifescan", "livespan", "lightscan", "liveskin", "lifeskin", "lightskin")

    private fun scanWord(s: Said): Boolean = s.words.any { it.contains("scan") || it in scanMisheardEn }

    private fun tabOf(s: Said): SavedTab? = when {
        s.words.any { kindEn(it) == Kind.PERSON } || s.ko("사람", "얼굴", "친구") -> SavedTab.PEOPLE
        s.words.any { kindEn(it) == Kind.CAR } || s.ko("자동차") || "차" in s.words -> SavedTab.CARS
        s.words.any { kindEn(it) == Kind.OBJECT } || s.ko("물건", "사물") -> SavedTab.OBJECTS
        else -> null
    }

    private fun destination(s: Said): VoiceCommand? {
        val dest = when {
            s.seq("scan", "menu") || s.seq("scan", "options") || s.seq("scan", "modes") ||
                s.ko("스캔메뉴", "둘러보기메뉴") -> Dest.ScanHub
            s.has(*walkEn) || (s.has("mode") && s.has("work", "working")) ||
                s.ko("걷기", "보행", "걸을", "워킹", "산책", "장애물") -> Dest.Walk
            s.has("history", "recent") || s.seq("past", "scans") || s.seq("last", "scan") ||
                s.ko("기록", "히스토리", "최근") -> Dest.History
            s.has("settings", "setting", "options", "preferences", "configuration", "setup") ||
                s.ko("설정", "세팅", "셋팅", "옵션") -> Dest.Settings
            s.has("home", "menu") || s.seq("main", "screen") || s.seq("start", "screen") ||
                s.ko("홈", "처음", "메인", "첫화면", "시작화면") -> Dest.Home
            s.has("live", "realtime", "continuous", "announce") || s.seq("real", "time") ||
                s.words.any { it.startsWith("live") && it.contains("scan") } || (s.has(*liveMisheardEn) && scanWord(s)) ||
                s.has(*liveScanMisheardEn) ||
                s.ko("실시간", "라이브", "계속알려") -> Dest.Scan(ScanMode.LIVE)
            scanWord(s) || s.seq("look", "around") || s.seq("around", "me") ||
                s.has("surroundings", "panorama", "360") || s.seq("full", "view") ||
                s.ko("전체스캔", "스캔", "스켄", "둘러보", "둘러봐", "주변", "주위", "한바퀴", "방안") ->
                Dest.Scan(ScanMode.FULL)
            s.words.any { it in savedEn } || s.seq("my", "list") || s.has("remembered", "library") ||
                s.ko("저장", "목록") -> Dest.Saved(tabOf(s))
            else -> tabOf(s)?.let { Dest.Saved(it) }
        }
        return dest?.let { VoiceCommand.Go(it) }
    }

    // 8 ------------------------------------------------------------------------------------------------
    private fun late(s: Said): VoiceCommand? = when {
        s.has("start", "begin", "go", "resume", "continue", "run", "play") ||
            s.ko("시작", "스타트", "눌러", "계속", "재개") -> VoiceCommand.Start
        s.has("repeat", "again", "pardon", "sorry") || s.seq("what", "did", "you", "say") || s.seq("one", "more", "time") ||
            (s.has("didnt") && s.has("hear", "catch")) || s.words == listOf("what") ||
            s.ko("다시", "반복", "뭐라고", "한번더", "못들었") -> VoiceCommand.Repeat
        else -> null
    }
}

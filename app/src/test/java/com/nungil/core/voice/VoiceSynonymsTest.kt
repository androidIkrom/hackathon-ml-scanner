package com.nungil.core.voice

import com.nungil.contract.Dest
import com.nungil.contract.ItemKind
import com.nungil.contract.SavedTab
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand
import com.nungil.contract.VoiceCommand.Go
import org.junit.Assert.assertEquals
import org.junit.Test

/** More ways to say each command, in English and Korean. */
class VoiceSynonymsTest {
    private fun all(expected: VoiceCommand, vararg said: String) {
        for (s in said) assertEquals(s, expected, VoiceCommandParser.parse(s))
    }

    @Test fun stopListening() = all(
        VoiceCommand.StopListening,
        "stop hearing", "don't listen", "mute the mic", "turn off voice", "듣지 말아 줘", "마이크 끄기",
    )

    @Test fun search() {
        all(Go(Dest.Search("my keys")), "where did I put my keys", "where did I leave my keys", "can you find my keys")
        all(Go(Dest.Search("가방")), "가방 어딨어")
    }

    @Test fun lookAroundQuestions() = all(
        Go(Dest.Scan(ScanMode.FULL)),
        "what is around me", "what's around", "what is near me", "describe the room", "describe my surroundings",
        "주변에 뭐 있어", "주위에 뭐가 있어", "근처에 뭐 있어",
    )

    @Test fun help() = all(
        VoiceCommand.Help(null),
        "what can you do", "commands", "list commands", "how do I use this", "instructions",
        "명령어", "사용법", "뭐 할 수 있어", "어떻게 써",
    )

    @Test fun who() = all(
        VoiceCommand.WhoIsThis,
        "who is there", "who's here", "who is in front of me", "who do you see", "who am I looking at",
    )

    @Test fun what() = all(
        VoiceCommand.WhatIsThis,
        "what is in front of me", "what do you see", "what can you see", "identify this", "describe this",
        "tell me what this is", "what is it",
        "앞에 뭐 있어", "뭐가 보여", "이거 뭐야",
    )

    @Test fun addPerson() {
        all(Go(Dest.AddPerson("Ali")), "enroll person Ali", "memorize face Ali", "add man Ali", "add woman Ali")
        all(Go(Dest.AddPerson("엄마")), "가족 추가 엄마")
    }

    @Test fun addObject() {
        all(Go(Dest.AddItem(ItemKind.OBJECT, "wallet")), "store belonging wallet", "add stuff wallet")
        all(Go(Dest.AddItem(ItemKind.OBJECT, "지갑")), "소지품 등록 지갑")
    }

    @Test fun learner() {
        all(VoiceCommand.Learner(true), "practice mode", "training mode on", "튜토리얼 켜 줘")
        all(VoiceCommand.Learner(false), "practice mode off", "튜토리얼 꺼")
    }

    @Test fun stop() = all(
        VoiceCommand.Stop,
        "wait", "hold on", "mute", "shh", "hush", "end",
        "잠깐", "잠깐만", "쉿", "멈추세요",
    )

    @Test fun switchCamera() = all(
        VoiceCommand.SwitchCamera,
        "change the camera", "rotate camera", "turn the camera", "other camera", "swap camera",
        "카메라 돌려", "카메라 변경",
    )

    @Test fun read() = all(
        VoiceCommand.ReadText,
        "read the sign", "what does the label say", "scan document", "reader", "ocr",
        "문자 인식", "표지판", "간판", "리더", "라벨",
    )

    @Test fun delete() = all(VoiceCommand.Delete, "forget this", "discard", "없애 줘", "제거")

    @Test fun back() = all(VoiceCommand.Back, "quit", "return")

    @Test fun start() = all(VoiceCommand.Start, "resume", "continue", "run", "play", "계속", "재개")

    @Test fun repeat() = all(
        VoiceCommand.Repeat,
        "pardon", "sorry", "one more time", "I didn't hear", "didn't catch that", "what",
        "한 번 더", "못 들었어",
    )

    @Test fun screens() {
        all(Go(Dest.ScanHub), "scan options", "scan modes")
        all(Go(Dest.History), "recent", "recent scans", "last scan", "최근")
        all(Go(Dest.Settings), "configuration", "setup", "환경 설정")
        all(Go(Dest.Home), "main screen", "home screen", "menu", "홈 화면")
        all(Go(Dest.Scan(ScanMode.LIVE)), "continuous", "announce as I go", "계속 알려 줘")
        all(Go(Dest.Scan(ScanMode.FULL)), "panorama", "360", "full view", "주위", "방 안")
        all(Go(Dest.Walk), "obstacles", "obstacle mode", "street", "장애물")
        all(Go(Dest.Saved(null)), "my list", "remembered", "library", "목록")
        all(Go(Dest.Saved(SavedTab.PEOPLE)), "my people")
        all(Go(Dest.Saved(SavedTab.OBJECTS)), "my things")
    }

    /** New words must not steal older commands. */
    @Test fun olderCommandsStillWin() {
        assertEquals(VoiceCommand.Learner(true), VoiceCommandParser.parse("tutorial mode"))
        assertEquals(VoiceCommand.Help("walk mode"), VoiceCommandParser.parse("what is walk mode"))
        assertEquals(Go(Dest.ScanHub), VoiceCommandParser.parse("scan menu"))
        assertEquals(VoiceCommand.ReadText, VoiceCommandParser.parse("read the menu"))
        assertEquals(VoiceCommand.Repeat, VoiceCommandParser.parse("what did you say"))
        assertEquals(VoiceCommand.Stop, VoiceCommandParser.parse("stop"))
        assertEquals(VoiceCommand.Unknown("Ali"), VoiceCommandParser.parse("Ali"))
    }
}

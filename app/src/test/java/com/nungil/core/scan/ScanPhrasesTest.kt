package com.nungil.core.scan

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanPhrasesTest {
    @Test fun canonicalSlowDown() {
        assertEquals("Slow down.", ScanPhrases.slowDown(Lang.EN))
        assertEquals("천천히 돌아 주세요.", ScanPhrases.slowDown(Lang.KO))
    }

    @Test fun canonicalNoCompass() {
        assertEquals("Compass not available. Switching to live scan.", ScanPhrases.noCompass(Lang.EN))
        assertEquals("나침반을 쓸 수 없어서 실시간 안내로 바꿀게요.", ScanPhrases.noCompass(Lang.KO))
    }

    @Test fun intro() {
        assertEquals("휴대폰을 세우고 천천히 한 바퀴 돌아 주세요.", ScanPhrases.intro(ScanMode.FULL, Lang.KO))
        assertEquals("Point the phone around. I will say what I find.", ScanPhrases.intro(ScanMode.LIVE, Lang.EN))
    }

    @Test fun thisIsUsesTheRightKoreanEnding() {
        assertEquals("This is Ali.", ScanPhrases.thisIs("Ali", Lang.EN))
        assertEquals("Ali예요.", ScanPhrases.thisIs("Ali", Lang.KO))
        assertEquals("민준이에요.", ScanPhrases.thisIs("민준", Lang.KO))
    }

    @Test fun looksLike() {
        assertEquals("It looks like an umbrella.", ScanPhrases.looksLike("umbrella", Lang.EN))
        assertEquals("keyboard 같아요.", ScanPhrases.looksLike("keyboard", Lang.KO))
    }

    @Test fun people() {
        assertEquals("I don't know this person.", ScanPhrases.unknownPerson(Lang.EN))
        assertEquals("누군지 모르겠어요.", ScanPhrases.unknownPerson(Lang.KO))
        assertEquals("사람이 보이지 않아요.", ScanPhrases.nobody(Lang.KO))
    }

    @Test fun cameraAndWalk() {
        assertEquals("Front camera.", ScanPhrases.cameraSwitched(Facing.FRONT, Lang.EN))
        assertEquals("후면 카메라예요.", ScanPhrases.cameraSwitched(Facing.BACK, Lang.KO))
        assertEquals("Walk mode is not ready yet.", ScanPhrases.walkNotReady(Lang.EN))
        assertEquals("걷기 모드는 아직 준비 중이에요.", ScanPhrases.walkNotReady(Lang.KO))
    }
}

package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePickTest {
    @Test fun mapsTextToSpeechResultCodes() {
        assertEquals(LangSupport.AVAILABLE, VoicePick.support(2))
        assertEquals(LangSupport.AVAILABLE, VoicePick.support(1))
        assertEquals(LangSupport.AVAILABLE, VoicePick.support(0))
        assertEquals(LangSupport.MISSING_DATA, VoicePick.support(-1))
        assertEquals(LangSupport.NOT_SUPPORTED, VoicePick.support(-2))
    }

    @Test fun koreanWhenInstalled() {
        val choice = VoicePick.choose(Lang.KO, LangSupport.AVAILABLE)
        assertEquals(Lang.KO, choice.speak)
        assertNull(choice.notice)
    }

    @Test fun missingKoreanFallsBackToEnglishWithANotice() {
        for (support in listOf(LangSupport.MISSING_DATA, LangSupport.NOT_SUPPORTED)) {
            val choice = VoicePick.choose(Lang.KO, support)
            assertEquals(Lang.EN, choice.speak)
            assertNotNull(choice.notice)
        }
    }

    @Test fun theNoticeIsEnglishBecauseItIsSpokenByTheEnglishVoice() {
        val notice = VoicePick.choose(Lang.KO, LangSupport.MISSING_DATA).notice!!
        assertEquals(notice, notice.filter { it.code < 0x1100 })
    }

    @Test fun englishNeverProducesANotice() {
        assertNull(VoicePick.choose(Lang.EN, LangSupport.MISSING_DATA).notice)
        assertEquals(Lang.EN, VoicePick.choose(Lang.EN, LangSupport.NOT_SUPPORTED).speak)
    }
}

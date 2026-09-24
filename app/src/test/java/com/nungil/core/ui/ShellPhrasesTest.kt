package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPhrasesTest {
    private fun hasHangul(s: String) = s.any { it.code in 0xAC00..0xD7A3 }

    @Test fun canonicalNotUnderstood() {
        assertEquals("I did not understand.", ShellPhrases.text(Phrase.NOT_UNDERSTOOD, Lang.EN))
        assertEquals("잘 못 알아들었어요.", ShellPhrases.text(Phrase.NOT_UNDERSTOOD, Lang.KO))
    }

    @Test fun everyPhraseExistsInBothLanguages() {
        for (p in Phrase.values()) {
            val en = ShellPhrases.text(p, Lang.EN)
            val ko = ShellPhrases.text(p, Lang.KO)
            assertTrue(p.name, en.isNotBlank() && !hasHangul(en))
            assertTrue(p.name, hasHangul(ko))
        }
    }

    @Test fun koreanIsPoliteHaeyoStyle() {
        for (p in Phrase.values()) {
            val ko = ShellPhrases.text(p, Lang.KO)
            assertTrue("${p.name}: $ko", !ko.contains("습니다") && !ko.contains("음."))
        }
    }

    @Test fun noEmoji() {
        for (p in Phrase.values()) for (lang in Lang.values()) {
            val s = ShellPhrases.text(p, lang)
            assertTrue(p.name, s.none { Character.isSurrogate(it) })
        }
    }

    @Test fun languageSentenceIsSpokenInTheNewLanguage() {
        assertEquals("Language: English.", ShellPhrases.text(Phrase.LANGUAGE_SET, Lang.EN))
        assertEquals("언어를 한국어로 바꿨어요.", ShellPhrases.text(Phrase.LANGUAGE_SET, Lang.KO))
    }
}

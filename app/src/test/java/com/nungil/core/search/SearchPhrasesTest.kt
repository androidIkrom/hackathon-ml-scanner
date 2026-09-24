package com.nungil.core.search

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPhrasesTest {
    @Test fun canonicalSentences() {
        assertEquals("Looking for backpack.", SearchPhrases.looking("backpack", Lang.EN))
        assertEquals("배낭을 찾고 있어요.", SearchPhrases.looking("배낭", Lang.KO))
        assertEquals("Lost it.", SearchPhrases.lost(Lang.EN))
        assertEquals("놓쳤어요.", SearchPhrases.lost(Lang.KO))
    }

    @Test fun particlesFollowTheName() {
        assertEquals("의자를 찾고 있어요.", SearchPhrases.looking("의자", Lang.KO))
        assertEquals("Ali를 찾고 있어요.", SearchPhrases.looking("Ali", Lang.KO))
        assertEquals("민준이 정면에 있어요.", SearchPhrases.where("민준", Zone.AHEAD, Lang.KO))
        assertEquals("의자가 왼쪽 끝에 있어요.", SearchPhrases.where("의자", Zone.FAR_LEFT, Lang.KO))
    }

    @Test fun englishZones() {
        assertEquals("backpack ahead.", SearchPhrases.where("backpack", Zone.AHEAD, Lang.EN))
        assertEquals("Ali on your left.", SearchPhrases.where("Ali", Zone.LEFT, Lang.EN))
        assertEquals("cup far right.", SearchPhrases.where("cup", Zone.FAR_RIGHT, Lang.EN))
    }

    @Test fun unknown() {
        assertEquals("I don't know that. Say it another way.", SearchPhrases.unknown(Lang.EN))
        assertEquals("잘 모르겠어요. 다르게 말해 주세요.", SearchPhrases.unknown(Lang.KO))
    }
}

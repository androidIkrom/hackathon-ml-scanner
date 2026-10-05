package com.nungil.core.search

import com.nungil.contract.DirectionStyle
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
        assertEquals("민준이 앞에 있어요.", SearchPhrases.where("민준", Zone.AHEAD, DirectionStyle.WORDS, Lang.KO))
        assertEquals("의자가 왼쪽에 있어요.", SearchPhrases.where("의자", Zone.FAR_LEFT, DirectionStyle.WORDS, Lang.KO))
    }

    @Test fun englishZones() {
        assertEquals("backpack ahead.", SearchPhrases.where("backpack", Zone.AHEAD, DirectionStyle.WORDS, Lang.EN))
        assertEquals("Ali slightly left.", SearchPhrases.where("Ali", Zone.LEFT, DirectionStyle.WORDS, Lang.EN))
        assertEquals("cup on your right.", SearchPhrases.where("cup", Zone.FAR_RIGHT, DirectionStyle.WORDS, Lang.EN))
    }

    @Test fun unknown() {
        assertEquals("I don't know that. Say it another way.", SearchPhrases.unknown(Lang.EN))
        assertEquals("잘 모르겠어요. 다르게 말해 주세요.", SearchPhrases.unknown(Lang.KO))
    }

    @Test fun findDirectionsInWordsAndHours() {
        assertEquals("Cup slightly left.", SearchPhrases.where("Cup", Zone.LEFT, DirectionStyle.WORDS, Lang.EN))
        assertEquals("Cup on your left.", SearchPhrases.where("Cup", Zone.FAR_LEFT, DirectionStyle.WORDS, Lang.EN))
        assertEquals("Cup at 1 o'clock.", SearchPhrases.where("Cup", Zone.FAR_RIGHT, DirectionStyle.CLOCK, Lang.EN))
        assertEquals("컵이 조금 왼쪽에 있어요.", SearchPhrases.where("컵", Zone.LEFT, DirectionStyle.WORDS, Lang.KO))
    }
}

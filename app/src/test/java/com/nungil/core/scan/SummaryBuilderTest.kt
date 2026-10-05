package com.nungil.core.scan

import com.nungil.contract.DirectionStyle
import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SummaryBuilderTest {
    private val blueChairs = ObjectSummary("chair", 3, ColorName.BLUE, 0f)
    private val blackLaptop = ObjectSummary("laptop", 1, ColorName.BLACK, 90f)
    private val person = ObjectSummary("person", 1, null, 5f)
    private val ali = ObjectSummary("Ali", 1, null, 0f, isName = true, wasPerson = true)

    @Test fun canonicalEnglishSummary() = assertEquals(
        "Around you: 3 blue chairs ahead; a black laptop on your right.",
        SummaryBuilder.fullSummary(listOf(blueChairs, blackLaptop), 100, Lang.EN, DirectionStyle.WORDS),
    )

    @Test fun canonicalKoreanSummary() = assertEquals(
        "앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요.",
        SummaryBuilder.fullSummary(listOf(blueChairs, blackLaptop), 100, Lang.KO, DirectionStyle.WORDS),
    )

    @Test fun directionsAreAlwaysFrontRightBehindLeft() = assertEquals(
        "Around you: 3 blue chairs ahead; a black laptop on your right.",
        SummaryBuilder.fullSummary(listOf(blackLaptop, blueChairs), 100, Lang.EN, DirectionStyle.WORDS),
    )

    @Test fun twoGroupsInOneDirectionEnglish() = assertEquals(
        "Around you: 3 blue chairs and a person ahead.",
        SummaryBuilder.fullSummary(listOf(blueChairs, person), 100, Lang.EN, DirectionStyle.WORDS),
    )

    @Test fun twoGroupsInOneDirectionKorean() = assertEquals(
        "앞에 파란 의자 세 개와 사람 한 명이 있어요.",
        SummaryBuilder.fullSummary(listOf(blueChairs, person), 100, Lang.KO, DirectionStyle.WORDS),
    )

    private val mixedChairs = listOf(
        ObjectSummary("chair", 1, ColorName.BLUE, 0f),
        ObjectSummary("chair", 1, ColorName.RED, 30f),
        ObjectSummary("chair", 2, ColorName.GRAY, 40f),
    )

    @Test fun mixedColoursEnglish() = assertEquals(
        "Around you: 4 chairs in blue, red and gray ahead.",
        SummaryBuilder.fullSummary(mixedChairs, 100, Lang.EN, DirectionStyle.WORDS),
    )

    @Test fun mixedColoursKorean() = assertEquals(
        "앞에 파란색, 빨간색, 회색 의자 네 개가 있어요.",
        SummaryBuilder.fullSummary(mixedChairs, 100, Lang.KO, DirectionStyle.WORDS),
    )

    @Test fun savedNameHasNoArticleOrColour() {
        assertEquals("Around you: Ali ahead.", SummaryBuilder.fullSummary(listOf(ali), 100, Lang.EN, DirectionStyle.WORDS))
        assertEquals("앞에 Ali가 있어요.", SummaryBuilder.fullSummary(listOf(ali), 100, Lang.KO, DirectionStyle.WORDS))
    }

    @Test fun partialCoverageIsAdmitted() {
        assertEquals(
            "I scanned 70 percent of the room. Around you: 3 blue chairs ahead.",
            SummaryBuilder.fullSummary(listOf(blueChairs), 70, Lang.EN, DirectionStyle.WORDS),
        )
        assertEquals(
            "방의 70퍼센트를 살펴봤어요. 앞에 파란 의자 세 개가 있어요.",
            SummaryBuilder.fullSummary(listOf(blueChairs), 70, Lang.KO, DirectionStyle.WORDS),
        )
    }

    @Test fun emptyRoom() {
        assertEquals("No objects found. Try better lighting and turn slowly.", SummaryBuilder.fullSummary(emptyList(), 100, Lang.EN, DirectionStyle.WORDS))
        assertEquals("찾은 물건이 없어요. 밝은 곳에서 천천히 돌아 주세요.", SummaryBuilder.fullSummary(emptyList(), 100, Lang.KO, DirectionStyle.WORDS))
    }

    @Test fun peopleNeverGetAColour() {
        val redPeople = ObjectSummary("person", 2, ColorName.RED, 0f)
        assertEquals("Around you: 2 people ahead.", SummaryBuilder.fullSummary(listOf(redPeople), 100, Lang.EN, DirectionStyle.WORDS))
        assertEquals("앞에 사람 두 명이 있어요.", SummaryBuilder.fullSummary(listOf(redPeople), 100, Lang.KO, DirectionStyle.WORDS))
    }

    @Test fun darkRoomNamesObjectsWithoutColourWords() {
        val dark = listOf(ObjectSummary("chair", 3, null, 0f), ObjectSummary("laptop", 1, null, 90f))
        val en = SummaryBuilder.fullSummary(dark, 100, Lang.EN, DirectionStyle.WORDS)
        val ko = SummaryBuilder.fullSummary(dark, 100, Lang.KO, DirectionStyle.WORDS)
        assertEquals("Around you: 3 chairs ahead; a laptop on your right.", en)
        assertEquals("앞에 의자 세 개, 오른쪽에 노트북 한 대가 있어요.", ko)
        for (c in ColorName.entries) {
            assertFalse(en.contains(c.en))
            assertFalse(ko.contains(c.koNoun) || ko.contains(c.koAdjective + " "))
        }
    }

    @Test fun coloursCanBeSwitchedOff() = assertEquals(
        "Around you: 3 chairs ahead.",
        SummaryBuilder.fullSummary(listOf(blueChairs), 100, Lang.EN, DirectionStyle.WORDS, colorsOn = false),
    )

    @Test fun threeGroupsJoinWithCommasAndTheLastPair() {
        val items = listOf(
            ObjectSummary("chair", 2, null, 0f),
            ObjectSummary("book", 1, null, 0f),
            ObjectSummary("cup", 3, null, 0f),
        )
        assertEquals("Around you: 2 chairs, a book and 3 cups ahead.", SummaryBuilder.fullSummary(items, 100, Lang.EN, DirectionStyle.WORDS))
        assertEquals("앞에 의자 두 개, 책 한 권과 컵 세 개가 있어요.", SummaryBuilder.fullSummary(items, 100, Lang.KO, DirectionStyle.WORDS))
    }

    @Test fun plurals() {
        assertEquals("chairs", SummaryBuilder.plural("chair"))
        assertEquals("buses", SummaryBuilder.plural("bus"))
        assertEquals("people", SummaryBuilder.plural("person"))
        assertEquals("wine glasses", SummaryBuilder.plural("wine glass"))
        assertEquals("benches", SummaryBuilder.plural("bench"))
        assertEquals("cell phones", SummaryBuilder.plural("cell phone"))
        assertEquals("scissors", SummaryBuilder.plural("scissors"))
    }

    @Test fun articles() {
        assertEquals("an orange chair", SummaryBuilder.describe(ObjectSummary("chair", 1, ColorName.ORANGE, 0f), Lang.EN))
        assertEquals("an umbrella", SummaryBuilder.describe(ObjectSummary("umbrella", 1, null, 0f), Lang.EN))
        assertEquals("a book", SummaryBuilder.describe(ObjectSummary("book", 1, null, 0f), Lang.EN))
    }

    @Test fun livePhrases() {
        val blueChair = ObjectSummary("chair", 1, ColorName.BLUE, 0f)
        val chairsLeft = ObjectSummary("chair", 3, null, 270f)
        assertEquals("a blue chair ahead", SummaryBuilder.livePhrase(blueChair, Lang.EN, DirectionStyle.WORDS))
        assertEquals("앞에 파란 의자", SummaryBuilder.livePhrase(blueChair, Lang.KO, DirectionStyle.WORDS))
        assertEquals("3 chairs on your left", SummaryBuilder.livePhrase(chairsLeft, Lang.EN, DirectionStyle.WORDS))
        assertEquals("왼쪽에 의자 세 개", SummaryBuilder.livePhrase(chairsLeft, Lang.KO, DirectionStyle.WORDS))
        assertEquals("Ali ahead", SummaryBuilder.livePhrase(ali, Lang.EN, DirectionStyle.WORDS))
        assertEquals("앞에 Ali", SummaryBuilder.livePhrase(ali, Lang.KO, DirectionStyle.WORDS))
    }

    @Test fun clockDirections() {
        val chairsLeft = ObjectSummary("chair", 3, null, 270f)
        assertEquals("3 chairs at 9 o'clock", SummaryBuilder.livePhrase(chairsLeft, Lang.EN, DirectionStyle.CLOCK))
        assertEquals("9시 방향에 의자 세 개", SummaryBuilder.livePhrase(chairsLeft, Lang.KO, DirectionStyle.CLOCK))
        assertEquals(
            "Around you: 3 blue chairs at 12 o'clock; a black laptop at 3 o'clock.",
            SummaryBuilder.fullSummary(listOf(blueChairs, blackLaptop), 100, Lang.EN, DirectionStyle.CLOCK),
        )
    }
}

package com.nungil.core.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceNamesTest {
    private fun like(vararg heard: String) = PlaceNames.soundsLike(heard.toList(), PlaceNames.KNOWN)

    @Test fun whatTheRecognizerWroteForSeoul() {
        // The logs: the user said "Seoul" eight times.
        for (heard in listOf("Soul", "Cell", "Sable", "Sole")) assertEquals(heard, "Seoul", like(heard).first())
    }

    @Test fun whatTheRecognizerWroteForAsanAndBusan() {
        assertEquals("Asan", like("Assam").first())
        assertTrue(like("Asansa").contains("Asan"))
        assertEquals("Busan", like("Put some").first())
    }

    @Test fun whatTheRecognizerWroteForCheonan() {
        // "ch" came back as "t", and the "r" of "turn" is not a sound of its own.
        assertEquals("Cheonan", like("Turn on").first())
        assertEquals("Cheonan", like("Jonah", "Jonathan").first())
    }

    @Test fun moreOfWhatTheRecognizerWrote() {
        // "Go Seoul" came back as "Go save", "Go sale", "Go sail"; Asan as "Hassan", "Asans", "Assange".
        for (heard in listOf("save", "saved", "sale", "sail", "So", "See you")) assertEquals(heard, "Seoul", like(heard).first())
        for (heard in listOf("Hassan", "Asans", "Asante", "Assange")) assertEquals(heard, "Asan", like(heard).first())
    }

    @Test fun aCityWithSiIsTheSameCity() {
        // "Asan-si" came back as "Asansa" and "Asans"; the search then offered a college instead.
        assertEquals(listOf("Asan" to 0), PlaceNames.ranked(listOf("Asansa"), listOf("Asan", "Busan")))
        assertEquals(listOf("Asan" to 0), PlaceNames.ranked(listOf("Asans"), listOf("Asan", "Busan")))
        assertEquals("Cheonan", like("Cheonan-si").first())
    }

    @Test fun theSameSoundsRankBeforeOneSoundOff() {
        assertEquals(listOf("Seoul" to 0), PlaceNames.ranked(listOf("sail"), listOf("Seoul", "Busan")))
        // "school" is one sound more than Seoul: a weak match, tried only after the real search.
        assertEquals(listOf("Seoul" to 1), PlaceNames.ranked(listOf("school"), listOf("Seoul", "Busan")))
    }

    @Test fun aSearchResultHasToHaveSomethingToDoWithTheWords() {
        // The geocoder answered "Turn on" with Onyang-dong and "Assange" with Sejong-ro.
        assertFalse(PlaceNames.related("Turn on", "Onyang-dong"))
        assertFalse(PlaceNames.related("Assange", "Sejong-ro"))
        assertFalse(PlaceNames.related("Hassan", "Onyang oncheonyeok"))
        assertTrue(PlaceNames.related("Bus terminal", "Terminal 9-gil"))
        assertTrue(PlaceNames.related("Shop", "Barber Shop"))
        assertTrue(PlaceNames.related("sale", "Military Auto Sales"))
        assertTrue(PlaceNames.related("Asan", "Asan-Si"))
        // a Korean name cannot be compared with English words: keep it
        assertTrue(PlaceNames.related("Sun Moon University", "선문대학교"))
    }

    @Test fun theRightSpellingIsTheBestMatch() {
        assertEquals("Suwon", like("Suwon").first())
        assertEquals("Cheonan", like("Cheonan").first())
    }

    @Test fun anyOfTheGuessesMayMatch() {
        assertEquals("Seoul", like("See you", "Soul").first())
    }

    @Test fun tooLittleOrSomethingElseMatchesNothing() {
        for (heard in listOf("Shop", "Stop", "New York", "강남역", "")) assertEquals(heard, emptyList<String>(), like(heard))
    }

    @Test fun savedNamesAreMatchedTheSameWay() {
        assertEquals(listOf("School"), PlaceNames.soundsLike(listOf("skull"), listOf("School", "Office")))
    }

    @Test fun homeIsTheUsersOwnPlace() {
        assertTrue(PlaceNames.isHome("Home."))
        assertTrue(PlaceNames.isHome("my house"))
        assertTrue(PlaceNames.isHome("집"))
        assertFalse(PlaceNames.isHome("Home plus"))
    }
}

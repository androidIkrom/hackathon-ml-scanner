package com.nungil.core.search

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResolverTest {
    private val ali = SavedName(1, "Ali", TargetType.PERSON, "person")
    private val minjun = SavedName(2, "김민준", TargetType.PERSON, "person")
    private val me = SavedName(3, "Me", TargetType.PERSON, "person")
    private val na = SavedName(4, "나", TargetType.PERSON, "person")
    private val alisher = SavedName(5, "Alisher", TargetType.PERSON, "person")
    private val keys = SavedName(10, "Office chair", TargetType.ITEM, "chair")
    private val redBag = SavedName(11, "빨간 가방", TargetType.ITEM, "backpack")
    private val saved = listOf(ali, minjun, me, na, alisher, keys, redBag)

    private fun label(text: String, lang: Lang = Lang.EN) = SearchResolver.resolve(text, emptyList(), lang)

    @Test fun cocoLabel() = assertEquals(SearchTarget(TargetType.LABEL, -1L, "chair", "chair"), label("find the chair"))

    @Test fun englishSynonyms() {
        assertEquals("backpack", label("find my bag")?.label)
        assertEquals("cell phone", label("where's my phone")?.label)
        assertEquals("dining table", label("desk")?.label)
        assertEquals("couch", label("sofa")?.label)
        assertEquals("tv", label("the monitor")?.label)
        assertEquals("bicycle", label("bike")?.label)
        assertEquals("laptop", label("my computer")?.label)
        assertEquals("laptop", label("notebook")?.label)
    }

    @Test fun pluralsAndTwoWordLabels() {
        assertEquals("chair", label("chairs")?.label)
        assertEquals("cell phone", label("find my cell phone")?.label)
        assertEquals("wine glass", label("wine glasses")?.label)
        assertEquals("knife", label("knives")?.label)
        assertEquals("bus", label("bus")?.label)
    }

    @Test fun koreanLabelsWithParticles() {
        val bag = label("내 가방을 찾아줘", Lang.KO)
        assertEquals(SearchTarget(TargetType.LABEL, -1L, "backpack", "배낭"), bag)
        assertEquals("cell phone", label("핸드폰 어디 있어", Lang.KO)?.label)
        assertEquals("chair", label("의자들을 찾아", Lang.KO)?.label)
        assertEquals("cat", label("고양이", Lang.KO)?.label)
        assertEquals("suitcase", label("여행 가방", Lang.KO)?.label)
        assertEquals("dining table", label("책상이 어디야", Lang.KO)?.label)
        assertEquals("tv", label("티비", Lang.KO)?.label)
    }

    @Test fun savedNamesComeFirst() {
        assertEquals(SearchTarget(TargetType.PERSON, 1L, "person", "Ali"), SearchResolver.resolve("find Ali", saved, Lang.EN))
        assertEquals(keys.id, SearchResolver.resolve("office chair", saved, Lang.EN)?.id)
    }

    @Test fun containmentByWholeWords() {
        assertEquals(keys.id, SearchResolver.resolve("chair", listOf(keys), Lang.EN)?.id)
        // "cup" is never part of "cupboard".
        val cupboard = SavedName(12, "Cupboard key", TargetType.ITEM, "remote")
        assertEquals(TargetType.LABEL, SearchResolver.resolve("cup", listOf(cupboard), Lang.EN)?.type)
    }

    @Test fun aWordSlippedIntoTheName() {
        // The logs: "Find my new black box" for the item "My black box".
        val box = SavedName(21, "My black box", TargetType.ITEM, "object")
        assertEquals(21L, SearchResolver.resolve("find my new black box", listOf(box), Lang.EN)?.id)
        assertEquals(21L, SearchResolver.resolve("my big black box", listOf(box), Lang.EN)?.id)
        // Not in another order, not with a word missing, and never for a one-word name.
        assertNull(SearchResolver.resolve("my box black", listOf(box), Lang.EN))
        assertNull(SearchResolver.resolve("my new box", listOf(box), Lang.EN))
        assertNull(SearchResolver.resolve("alo", listOf(ali), Lang.EN))
        // A longer sentence that happens to hold the name's words is about something else.
        val bag = SavedName(22, "My bag", TargetType.ITEM, "object")
        assertEquals("cell phone", SearchResolver.resolve("find my phone in the bag", listOf(bag), Lang.EN)?.label)
        assertEquals(22L, SearchResolver.resolve("find my big red bag", listOf(bag), Lang.EN)?.id)
    }

    @Test fun hangulNamesWithParticlesAndGivenName() {
        assertEquals(minjun.id, SearchResolver.resolve("김민준을 찾아줘", saved, Lang.KO)?.id)
        assertEquals(minjun.id, SearchResolver.resolve("민준 어디 있어", saved, Lang.KO)?.id)
        assertEquals(redBag.id, SearchResolver.resolve("빨간 가방 찾아줘", saved, Lang.KO)?.id)
    }

    @Test fun namesThatAreFillerWords() {
        assertEquals(me.id, SearchResolver.resolve("find me", saved, Lang.EN)?.id)
        assertEquals(na.id, SearchResolver.resolve("나", saved, Lang.KO)?.id)
    }

    @Test fun fuzzyOnlyForLongNames() {
        assertEquals(alisher.id, SearchResolver.resolve("find alisha", saved, Lang.EN)?.id)
        // "Alo" is 1 edit from "Ali" but "Ali" is shorter than 4 characters: no fuzzy match, no label either.
        assertNull(SearchResolver.resolve("alo", listOf(ali), Lang.EN))
    }

    @Test fun unknownAndEmpty() {
        assertNull(label("spaceship"))
        assertNull(label("find"))
        assertNull(label(""))
        assertNull(label("우주선", Lang.KO))
    }

    @Test fun aMisheardNameIsFoundAmongTheOtherGuesses() {
        // The logs: "Find Dopey" was heard as "Find Dorothy", with "Find Dopey" as the second guess.
        val dopey = SavedName(20, "Dopey", TargetType.ITEM, "object")
        val guesses = listOf("Find Dorothy", "Find Dopey", "Find Dope", "Find Dolphy")
        assertNull(SearchResolver.resolve("Dorothy", listOf(dopey), Lang.EN))
        assertEquals(20L, SearchResolver.resolve("Dorothy", guesses, listOf(dopey), Lang.EN)?.id)
        // What was heard wins when it means something, and only saved names are taken from the other guesses.
        assertEquals("chair", SearchResolver.resolve("chair", listOf("Find chair", "Find Dopey"), listOf(dopey), Lang.EN)?.label)
        assertNull(SearchResolver.resolve("Dorothy", listOf("Find Dorothy", "Find chair"), listOf(dopey), Lang.EN))
        // Guesses for some other phrase (the query was typed later) are not used.
        assertNull(SearchResolver.resolve("spaceship", guesses, listOf(dopey), Lang.EN))
    }

    @Test fun labelForChips() {
        assertEquals("backpack", SearchResolver.labelFor("가방"))
        assertEquals("cell phone", SearchResolver.labelFor("Phone"))
        assertNull(SearchResolver.labelFor("   "))
    }

    @Test fun distanceCountsSyllables() {
        assertEquals(0, SearchResolver.distance("민준", "민준"))
        assertEquals(1, SearchResolver.distance("김민준", "김민중"))
        assertEquals(2, SearchResolver.distance("alisha", "alisher"))
        assertEquals(3, SearchResolver.distance("", "abc"))
    }

    // ---- A name of filler words ("My", "Me") -------------------------------------------------------------------

    private val my = SavedName(20, "My", TargetType.PERSON, "person")
    private val charger = SavedName(21, "My charger test one", TargetType.ITEM, "object")

    @Test fun theLongerNameBeatsAFillerName() {
        // "Find my charger" looked for the person "My" (the logs).
        assertEquals(charger.id, SearchResolver.resolve("my charger", listOf(my, charger), Lang.EN)?.id)
    }

    @Test fun aFillerNameAloneIsStillFound() {
        assertEquals(my.id, SearchResolver.resolve("my", listOf(my, charger), Lang.EN)?.id)
        assertEquals(me.id, SearchResolver.resolve("find me", saved, Lang.EN)?.id)
    }

    @Test fun aLabelBeatsAFillerName() =
        assertEquals("backpack", SearchResolver.resolve("my bag", listOf(my), Lang.EN)?.label)

    @Test fun mostWordsWin() {
        val blackBox = SavedName(22, "Black box", TargetType.ITEM, "object")
        val myBlackBox = SavedName(23, "My black box", TargetType.ITEM, "object")
        assertEquals(myBlackBox.id, SearchResolver.resolve("my black box", listOf(blackBox, myBlackBox), Lang.EN)?.id)
    }
}

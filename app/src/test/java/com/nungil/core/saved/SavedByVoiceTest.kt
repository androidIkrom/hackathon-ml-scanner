package com.nungil.core.saved

import com.nungil.contract.Lang
import com.nungil.core.search.SavedName
import com.nungil.core.search.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedByVoiceTest {
    private val charger = SavedName(23, "My charger test one", TargetType.ITEM, "object")
    private val bottle = SavedName(16, "My new white bottle", TargetType.ITEM, "bottle")
    private val bottle2 = SavedName(24, "My new white bottle", TargetType.ITEM, "bottle")
    private val ali = SavedName(3, "Ali", TargetType.PERSON, "person")
    private val my = SavedName(1, "My", TargetType.PERSON, "person")
    private val saved = listOf(charger, bottle, bottle2, ali, my)

    private fun named(vararg guesses: String) = SavedByVoice.named(guesses.toList(), saved, Lang.EN)

    @Test fun theNameAfterDeleteIsFound() = assertEquals(listOf(charger), named("Delete my charger test one"))

    @Test fun aMisheardNameIsFoundInTheOtherGuesses() {
        // The device log, 2026-10-11 12:22:54: the first guess was "test on", the second the real name.
        assertEquals(listOf(charger), named("Delete my charger test on", "Delete my charger test one", "Delete my charger test phone"))
    }

    @Test fun openWithTheName() = assertEquals(listOf(charger), named("Open my charger test one", "Open my charger test 1"))

    @Test fun aPersonIsFoundToo() = assertEquals(listOf(ali), named("Delete Ali"))

    @Test fun everyThingOfTheSameNameComesWithIt() =
        assertEquals(setOf(bottle, bottle2), named("Delete my new white bottle").toSet())

    @Test fun nothingSavedByThatName() = assertTrue(named("Delete the moon").isEmpty())

    @Test fun deleteAloneNamesNothing() = assertTrue(named("Delete").isEmpty())

    @Test fun aWordThatIsOnlyAThingsKindIsNotASavedName() = assertTrue(named("Delete bottle").isEmpty())
}

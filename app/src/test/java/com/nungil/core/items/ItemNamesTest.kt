package com.nungil.core.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemNamesTest {
    @Test fun theNameHeardComesFirstThenTheOtherGuesses() {
        // The logs: "My green chair" was heard as "My green tear" and saved under that name.
        val guesses = listOf("My green tear", "My green chair", "My green cheater", "My green teal")
        assertEquals(listOf("My green tear", "My green chair", "My green cheater"), ItemNames.candidates("My green tear", guesses))
    }

    @Test fun guessesThatDifferOnlyInCaseOrSignsAreOne() {
        assertEquals(listOf("My black box"), ItemNames.candidates("My black box", listOf("My black box", "My Black Box", "My blackbox", "my black box/")))
        assertEquals(listOf("Add my box"), ItemNames.candidates(" Add my box ", listOf("Add my box", "Add my box/")))
    }

    @Test fun aNameThatWasTypedOrGivenHasNoOtherGuesses() {
        assertEquals(listOf("Dad's car"), ItemNames.candidates("Dad's car", emptyList()))
        // Guesses for some other phrase are not this name's.
        assertEquals(listOf("My towel"), ItemNames.candidates("My towel", listOf("Add object", "Add objects")))
        assertEquals(emptyList<String>(), ItemNames.candidates("  ", listOf("x")))
    }

    @Test fun aNameIsTakenWhateverItsCaseAndSpaces() {
        val saved = listOf("My new white bottle", "Dopey")
        assertTrue(ItemNames.taken("my new white bottle ", saved))
        assertTrue(ItemNames.taken("DOPEY", saved))
        assertFalse(ItemNames.taken("My white bottle", saved))
        assertFalse(ItemNames.taken("", saved))
    }

    @Test fun answersToANameReadBack() {
        assertEquals(ItemNameAnswer.YES, ItemNames.answer("Yes"))
        assertEquals(ItemNameAnswer.YES, ItemNames.answer("yes it is"))
        assertEquals(ItemNameAnswer.YES, ItemNames.answer("네"))
        assertEquals(ItemNameAnswer.NO, ItemNames.answer("No"))
        assertEquals(ItemNameAnswer.NO, ItemNames.answer("아니요"))
        assertEquals(ItemNameAnswer.REPLACE, ItemNames.answer("Replace"))
        assertEquals(ItemNameAnswer.REPLACE, ItemNames.answer("replace it."))
        assertEquals(ItemNameAnswer.REPLACE, ItemNames.answer("바꾸기"))
        // Anything else is a name: the user says the right one instead of "no".
        assertEquals(ItemNameAnswer.OTHER, ItemNames.answer("My green chair"))
        assertEquals(ItemNameAnswer.OTHER, ItemNames.answer("my replacement key"))
    }
}

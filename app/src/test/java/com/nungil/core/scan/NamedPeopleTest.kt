package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class NamedPeopleTest {
    private val ali = ObjectSummary("Ali", 1, null, 10f, isName = true, wasPerson = true)

    @Test fun shadowDistanceIs20Degrees() = assertEquals(20f, NamedPeople.SHADOW_DEG)

    @Test fun plainPersonNextToANamedPersonIsDropped() {
        val out = NamedPeople.dropShadowedPersons(listOf(ali, ObjectSummary("person", 1, null, 25f)))
        assertEquals(listOf("Ali"), out.map { it.label })
    }

    @Test fun plainPersonFarAwayStays() {
        val out = NamedPeople.dropShadowedPersons(listOf(ali, ObjectSummary("person", 1, null, 40f)))
        assertEquals(listOf("Ali", "person"), out.map { it.label })
    }

    @Test fun aNamedItemDoesNotHideAPerson() {
        val bag = ObjectSummary("My bag", 1, null, 10f, isName = true, wasPerson = false)
        val out = NamedPeople.dropShadowedPersons(listOf(bag, ObjectSummary("person", 1, null, 12f)))
        assertEquals(listOf("My bag", "person"), out.map { it.label })
    }
}

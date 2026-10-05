package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundPlacesTest {
    private val places = FoundPlaces()
    private val left = Box(0.1f, 0.2f, 0.4f, 0.6f)
    private val right = Box(0.6f, 0.2f, 0.9f, 0.6f)

    @Test fun oneFindIsNotEnough() {
        places.found(left, "My towel", 0L)
        assertTrue(places.current(100L).isEmpty())
    }

    @Test fun twoFindsInARowShowTheItem() {
        places.found(left, "My towel", 0L)
        places.found(right, "My towel", 1_200L)
        assertEquals(listOf(FoundPlaces.Place(right, "My towel")), places.current(1_300L))
    }

    @Test fun anItemNotFoundAgainIsForgotten() {
        places.found(left, "My towel", 0L)
        places.found(left, "My towel", 1_000L)
        assertTrue(places.current(1_000L + FoundPlaces.KEEP_MS + 1).isEmpty())
        // Found again later, it needs two finds again.
        places.found(left, "My towel", 9_000L)
        assertTrue(places.current(9_000L).isEmpty())
    }

    @Test fun eachItemCountsOnItsOwn() {
        places.found(left, "My towel", 0L)
        places.found(right, "My charger", 500L)
        places.found(left, "My towel", 1_000L)
        assertEquals(listOf("My towel"), places.current(1_100L).map { it.name })
    }
}

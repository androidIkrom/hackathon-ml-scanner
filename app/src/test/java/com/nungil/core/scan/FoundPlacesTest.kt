package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun anItemASearchDidNotFindIsGoneAtOnce() {
        // "My drug" kept its frame for 3 s after the phone turned away (device log 2026-10-07 20:05:46-49).
        places.found(left, "My drug", 0L)
        places.found(left, "My drug", 1_000L)
        places.found(right, "My towel", 500L)
        places.found(right, "My towel", 1_500L)
        places.searched(setOf("My towel"))
        assertEquals(listOf("My towel"), places.current(1_600L).map { it.name })
        // Found again, it needs two finds again.
        places.found(left, "My drug", 2_000L)
        assertEquals(listOf("My towel"), places.current(2_000L).map { it.name })
    }

    @Test fun eachItemCountsOnItsOwn() {
        places.found(left, "My towel", 0L)
        places.found(right, "My charger", 500L)
        places.found(left, "My towel", 1_000L)
        assertEquals(listOf("My towel"), places.current(1_100L).map { it.name })
    }

    /** The thing's own outline, inside the left square. */
    private val towel = Box(0.2f, 0.3f, 0.3f, 0.5f)

    @Test fun anOutlineIsKeptAsThePlaceIsFoundAgain() {
        places.found(left, "My towel", 0L, outline = towel)
        places.found(left, "My towel", 1_000L)
        assertEquals(listOf(FoundPlaces.Place(left, "My towel", towel)), places.current(1_100L))
    }

    @Test fun aNewOutlineReplacesTheOldOne() {
        val moved = Box(0.25f, 0.3f, 0.35f, 0.5f)
        places.found(left, "My towel", 0L, outline = towel)
        places.found(left, "My towel", 1_000L, outline = moved)
        assertEquals(moved, places.current(1_100L).single().outline)
    }

    @Test fun theOutlineIsDroppedWhenTheSquareMovesAwayFromIt() {
        // The phone turned: the old outline is no longer where the item is.
        places.found(left, "My towel", 0L, outline = towel)
        places.found(right, "My towel", 1_000L)
        assertEquals(listOf(FoundPlaces.Place(right, "My towel", null)), places.current(1_100L))
    }

    @Test fun aPlaceWithoutAnOutlineHasNone() {
        places.found(left, "My towel", 0L)
        places.found(left, "My towel", 1_000L)
        assertNull(places.current(1_100L).single().outline)
    }
}

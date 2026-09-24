package com.nungil.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTrackerTest {
    private val t = SearchTracker()

    @Test fun silentUntilSeen() {
        assertNull(t.update(0, null).say)
        assertNull(t.update(10_000, null).say)
    }

    @Test fun firstSightingIsAnnouncedAtOnce() =
        assertEquals(SearchTracker.Say.Where(Zone.LEFT), t.update(100, Zone.LEFT).say)

    @Test fun sameZoneIsNotRepeated() {
        t.update(0, Zone.LEFT)
        assertNull(t.update(5_000, Zone.LEFT).say)
    }

    @Test fun newZoneWaitsTwoSeconds() {
        t.update(0, Zone.LEFT)
        assertNull(t.update(1_999, Zone.AHEAD).say)
        assertEquals(SearchTracker.Say.Where(Zone.AHEAD), t.update(2_000, Zone.AHEAD).say)
    }

    @Test fun lostOnceAfterThreeSeconds() {
        t.update(0, Zone.AHEAD)
        assertNull(t.update(2_999, null).say)
        assertEquals(SearchTracker.Say.Lost, t.update(3_000, null).say)
        assertNull(t.update(9_000, null).say)
    }

    @Test fun seeingAgainAfterLostIsAnnouncedAtOnce() {
        t.update(0, Zone.AHEAD)
        t.update(3_000, null)
        assertEquals(SearchTracker.Say.Where(Zone.AHEAD), t.update(3_100, Zone.AHEAD).say)
    }

    @Test fun briefGapsDoNotCountAsLost() {
        t.update(0, Zone.AHEAD)
        t.update(2_000, null)
        t.update(2_500, Zone.AHEAD)
        assertNull(t.update(5_000, null).say)
        assertEquals(SearchTracker.Say.Lost, t.update(5_500, null).say)
    }

    @Test fun vibratesOnEnteringTheCentre() {
        assertFalse(t.update(0, Zone.LEFT).enteredCenter)
        assertTrue(t.update(100, Zone.AHEAD).enteredCenter)
        assertFalse(t.update(200, Zone.AHEAD).enteredCenter)
        t.update(300, Zone.RIGHT)
        assertTrue(t.update(400, Zone.AHEAD).enteredCenter)
    }
}

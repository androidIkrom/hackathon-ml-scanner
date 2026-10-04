package com.nungil.core.walk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsSignalTest {
    @Test fun weakForTenSecondsThenOnceUntilGoodAgain() {
        val g = GpsSignal()
        assertFalse(g.update(0, 35f))
        assertFalse(g.update(9_999, 40f))
        assertTrue(g.update(10_000, 40f))
        assertFalse(g.update(20_000, 50f))
        assertFalse(g.update(21_000, 25f)) // better, not yet good
        assertFalse(g.update(22_000, 18f)) // good: re-armed
        assertFalse(g.update(23_000, 45f))
        assertTrue(g.update(33_000, 45f))
    }

    @Test fun onlyAFixOfTheLastTenSecondsTellsWhereTheWalkerIs() {
        assertTrue(FixAge.fresh(fixAtMs = 50_000, nowMs = 60_000))
        assertFalse(FixAge.fresh(fixAtMs = 49_999, nowMs = 60_000)) // the phone's last known place, minutes old
        assertFalse(FixAge.fresh(fixAtMs = null, nowMs = 60_000))
    }

    @Test fun aFixOfThisRunStaysGoodWhileTheWalkerStandsStill() {
        // Updates come only after 1 m of moving: standing indoors, "where am I" waited for a fix that never came (the logs).
        assertTrue(FixAge.usable(fixAtMs = 10_000, liveSinceMs = 5_000, nowMs = 120_000))
        // The phone's last known place, from before this run: only when it is recent.
        assertFalse(FixAge.usable(fixAtMs = 1_000, liveSinceMs = 5_000, nowMs = 120_000))
        assertTrue(FixAge.usable(fixAtMs = 1_000, liveSinceMs = 5_000, nowMs = 8_000))
        assertFalse(FixAge.usable(fixAtMs = 1_000, liveSinceMs = null, nowMs = 120_000))
        assertFalse(FixAge.usable(fixAtMs = null, liveSinceMs = 5_000, nowMs = 6_000))
    }

    @Test fun unknownAccuracyIsNotWeak() {
        val g = GpsSignal()
        for (s in 0..30) assertFalse(g.update(s * 1_000L, 0f))
    }
}

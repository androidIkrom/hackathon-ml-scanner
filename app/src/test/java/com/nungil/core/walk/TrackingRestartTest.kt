package com.nungil.core.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingRestartTest {
    @Test fun noRestartWhileTrackingOrJustStarted() {
        val r = TrackingRestart()
        assertFalse(r.shouldRestart(now = 10_000, lastTrackedAt = 9_000, startedAt = 0, selfRecovers = false))
        assertFalse(r.shouldRestart(now = 10_000, lastTrackedAt = 0, startedAt = 6_000, selfRecovers = false))
        assertTrue(r.shouldRestart(now = 11_000, lastTrackedAt = 0, startedAt = 6_000, selfRecovers = false))
    }

    @Test fun neverRestartsInTheDark() {
        val r = TrackingRestart()
        for (t in 0L..600_000L step 1_000L) assertFalse(r.shouldRestart(t, lastTrackedAt = 0, startedAt = 0, selfRecovers = true))
    }

    @Test fun eachFailedRestartDoublesTheWaitUpToTwoMinutes() {
        val r = TrackingRestart()
        var started = 0L
        val at = mutableListOf<Long>()
        for (t in 0L..400_000L step 1_000L) {
            if (r.shouldRestart(t, lastTrackedAt = 0, startedAt = started, selfRecovers = false)) {
                at += t
                started = t
            }
        }
        assertEquals(listOf(5_000L, 20_000L, 50_000L, 110_000L, 230_000L, 350_000L), at)
    }

    @Test fun trackingAgainResetsTheWait() {
        val r = TrackingRestart()
        assertTrue(r.shouldRestart(5_000, lastTrackedAt = 0, startedAt = 0, selfRecovers = false))
        assertTrue(r.shouldRestart(20_000, lastTrackedAt = 0, startedAt = 5_000, selfRecovers = false))
        // tracked again at 21 s, lost it: the next restart only needs 5 s stale, no 30 s gap
        assertTrue(r.shouldRestart(26_000, lastTrackedAt = 21_000, startedAt = 20_000, selfRecovers = false))
    }
}

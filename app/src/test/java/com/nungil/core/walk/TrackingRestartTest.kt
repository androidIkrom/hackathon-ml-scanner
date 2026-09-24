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
        assertTrue(r.shouldRestart(now = 16_000, lastTrackedAt = 0, startedAt = 6_000, selfRecovers = false))
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
        assertEquals(listOf(10_000L, 25_000L, 55_000L, 115_000L, 235_000L, 355_000L), at)
    }

    @Test fun trackingAgainResetsTheWait() {
        val r = TrackingRestart()
        assertTrue(r.shouldRestart(10_000, lastTrackedAt = 0, startedAt = 0, selfRecovers = false))
        assertTrue(r.shouldRestart(25_000, lastTrackedAt = 0, startedAt = 10_000, selfRecovers = false))
        // tracked again at 26 s, lost it: the next restart only needs 10 s stale, no 30 s gap
        assertTrue(r.shouldRestart(36_000, lastTrackedAt = 26_000, startedAt = 25_000, selfRecovers = false))
    }
}

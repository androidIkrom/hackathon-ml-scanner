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

    @Test fun unknownAccuracyIsNotWeak() {
        val g = GpsSignal()
        for (s in 0..30) assertFalse(g.update(s * 1_000L, 0f))
    }
}

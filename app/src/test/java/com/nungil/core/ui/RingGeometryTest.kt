package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RingGeometryTest {
    @Test fun firstSegmentStartsAtTwelveOClock() {
        val arc = RingGeometry.segment(0, 36)
        assertEquals(-89f, arc.startDeg, 1e-4f)
        assertEquals(8f, arc.sweepDeg, 1e-4f)
    }

    @Test fun lastSegmentEndsBeforeTwelveOClock() {
        val arc = RingGeometry.segment(35, 36)
        assertEquals(261f, arc.startDeg, 1e-4f)
        assertEquals(269f, arc.startDeg + arc.sweepDeg, 1e-4f)
    }

    @Test fun segmentsGoClockwiseWithEqualGaps() {
        val a = RingGeometry.segment(3, 36)
        val b = RingGeometry.segment(4, 36)
        assertEquals(10f, b.startDeg - a.startDeg, 1e-4f)
        assertEquals(RingGeometry.GAP_DEG, b.startDeg - (a.startDeg + a.sweepDeg), 1e-4f)
    }

    @Test fun aSingleSegmentIsAFullCircle() {
        val arc = RingGeometry.segment(0, 1)
        assertEquals(-90f, arc.startDeg, 1e-4f)
        assertEquals(360f, arc.sweepDeg, 1e-4f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun indexOutOfRangeIsRejected() {
        RingGeometry.segment(36, 36)
    }

    @Test fun percentIsClamped() {
        assertEquals(0, RingGeometry.clampPercent(-5))
        assertEquals(100, RingGeometry.clampPercent(140))
        assertEquals(42, RingGeometry.clampPercent(42))
    }
}

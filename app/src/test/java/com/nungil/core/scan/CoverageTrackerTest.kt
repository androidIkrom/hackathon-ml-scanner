package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTrackerTest {
    @Test fun tunedNumbers() {
        assertEquals(36, CoverageTracker.BIN_COUNT)
        assertEquals(45f, CoverageTracker.MAX_ARC_DEG)
        assertEquals(5f, CoverageTracker.STEP_DEG)
    }

    @Test fun startsEmpty() {
        val c = CoverageTracker()
        assertEquals(0, c.percent())
        assertFalse(c.isComplete())
    }

    @Test fun markFillsOneTenDegreeBin() {
        val c = CoverageTracker()
        c.mark(15f)
        assertTrue(c.bins()[1])
        assertEquals(1, c.bins().count { it })
    }

    @Test fun negativeHeadingsWrap() {
        val c = CoverageTracker()
        c.mark(-5f)
        assertTrue(c.bins()[35])
    }

    @Test fun shortArcFillsEveryBinBetween() {
        val c = CoverageTracker()
        c.markArc(0f, 40f)
        assertEquals(listOf(0, 1, 2, 3, 4), c.bins().indices.filter { c.bins()[it] })
    }

    @Test fun arcBackwardsAcrossZeroFillsTheShortWay() {
        val c = CoverageTracker()
        c.markArc(10f, -20f)
        assertEquals(listOf(0, 1, 34, 35), c.bins().indices.filter { c.bins()[it] })
    }

    @Test fun jumpLargerThan45MarksOnlyTheEnds() {
        val c = CoverageTracker()
        c.markArc(0f, 90f)
        assertEquals(listOf(0, 9), c.bins().indices.filter { c.bins()[it] })
    }

    @Test fun fullTurnIsComplete() {
        val c = CoverageTracker()
        var h = 0f
        while (h < 360f) {
            c.markArc(h, h + 30f)
            h += 30f
        }
        assertTrue(c.isComplete())
        assertEquals(100, c.percent())
    }

    @Test fun halfTurnIsFiftyPercent() {
        val c = CoverageTracker()
        for (i in 0 until 18) c.mark(i * 10f + 1f)
        assertEquals(50, c.percent())
    }

    @Test fun binsIsACopy() {
        val c = CoverageTracker()
        c.bins()[0] = true
        assertEquals(0, c.percent())
    }
}

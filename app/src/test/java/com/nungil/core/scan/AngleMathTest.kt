package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class AngleMathTest {
    private val eps = 0.001f

    @Test fun normalizeWrapsNegativeAndLarge() {
        assertEquals(270f, AngleMath.normalize(-90f), eps)
        assertEquals(0f, AngleMath.normalize(720f), eps)
        assertEquals(0f, AngleMath.normalize(360f), eps)
    }

    @Test fun normalizeNeverReturns360() = assertEquals(0f, AngleMath.normalize(-1e-6f), eps)

    @Test fun diffIsShortestSignedTurn() {
        assertEquals(20f, AngleMath.diff(10f, 350f), eps)
        assertEquals(-20f, AngleMath.diff(350f, 10f), eps)
        assertEquals(-180f, AngleMath.diff(0f, 180f), eps)
    }

    @Test fun sector8Has45DegreeSectorsCentredOnFront() {
        assertEquals(0, AngleMath.sector8(0f))
        assertEquals(0, AngleMath.sector8(22.4f))
        assertEquals(1, AngleMath.sector8(22.6f))
        assertEquals(0, AngleMath.sector8(-10f))
        assertEquals(2, AngleMath.sector8(90f))
        assertEquals(7, AngleMath.sector8(-30f))
    }

    @Test fun sector4Has90DegreeSectorsCentredOnFront() {
        assertEquals(0, AngleMath.sector4(44f))
        assertEquals(1, AngleMath.sector4(46f))
        assertEquals(2, AngleMath.sector4(180f))
        assertEquals(3, AngleMath.sector4(-46f))
        assertEquals(0, AngleMath.sector4(-44f))
    }

    @Test fun weightedMeanIsCircular() {
        assertEquals(0f, AngleMath.normalize(AngleMath.weightedMean(350f, 1, 10f) + 0.0001f), 0.01f)
        assertEquals(90f, AngleMath.weightedMean(0f, 0, 90f), eps)
        assertEquals(15f, AngleMath.weightedMean(10f, 1, 20f), 0.01f)
    }
}

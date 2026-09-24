package com.nungil.core.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceMatcherTest {
    /** A unit vector in the plane at [deg] degrees; cosine between two of them is cos(difference). */
    private fun at(deg: Double) = floatArrayOf(cos(Math.toRadians(deg)).toFloat(), sin(Math.toRadians(deg)).toFloat(), 0f)

    @Test fun cosine() {
        assertEquals(1f, FaceMatcher.cosine(at(0.0), at(0.0)), 1e-6f)
        assertEquals(0f, FaceMatcher.cosine(at(0.0), at(90.0)), 1e-6f)
        assertEquals(-1f, FaceMatcher.cosine(at(0.0), at(180.0)), 1e-6f)
        assertEquals(0f, FaceMatcher.cosine(floatArrayOf(0f, 0f, 0f), at(0.0)), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cosineRejectsSizeMismatch() {
        FaceMatcher.cosine(floatArrayOf(1f), floatArrayOf(1f, 0f))
    }

    @Test fun personScoreIsTheMeanOfTheBestThree() {
        // cos(0)=1, cos(60)=0.5, cos(90)=0, cos(120)=-0.5 -> best three are 1, 0.5, 0 -> 0.5
        val samples = listOf(at(90.0), at(0.0), at(120.0), at(60.0))
        assertEquals(0.5f, FaceMatcher.personScore(at(0.0), samples), 1e-5f)
        assertEquals(-1f, FaceMatcher.personScore(at(0.0), emptyList()), 0f)
    }

    @Test fun matchesTheRightPerson() {
        val known = mapOf(
            1L to listOf(at(5.0), at(10.0), at(15.0)),
            2L to listOf(at(80.0), at(85.0), at(90.0)),
        )
        assertEquals(1L, FaceMatcher.bestMatch(at(0.0), known)?.id)
        assertEquals(2L, FaceMatcher.bestMatch(at(88.0), known)?.id)
    }

    @Test fun strangerBelowThresholdIsNobody() {
        val known = mapOf(1L to listOf(at(0.0), at(0.0), at(0.0)))
        assertNull(FaceMatcher.bestMatch(at(61.0), known)) // cos(61) = 0.48 < 0.5
        assertEquals(1L, FaceMatcher.bestMatch(at(59.0), known)?.id) // cos(59) = 0.515
    }

    @Test fun oneLuckySampleDoesNotWin() {
        // One sample fits perfectly, the others are far: mean of best three = (1 + 0 + 0) / 3 = 0.33.
        val known = mapOf(1L to listOf(at(0.0), at(90.0), at(90.0), at(90.0)))
        assertNull(FaceMatcher.bestMatch(at(0.0), known))
    }

    @Test fun tooCloseToTheRunnerUpIsNobody() {
        val known = mapOf(
            1L to listOf(at(20.0), at(20.0), at(20.0)),   // cos 20 = 0.940
            2L to listOf(at(-25.0), at(-25.0), at(-25.0)), // cos 25 = 0.906, margin 0.034 < 0.08
        )
        assertNull(FaceMatcher.bestMatch(at(0.0), known))
        val clear = known + (2L to listOf(at(-45.0), at(-45.0), at(-45.0))) // cos 45 = 0.707, margin 0.23
        assertEquals(1L, FaceMatcher.bestMatch(at(0.0), clear)?.id)
    }

    @Test fun nobodySaved() {
        assertNull(FaceMatcher.bestMatch(at(0.0), emptyMap()))
        assertNull(FaceMatcher.bestMatch(at(0.0), mapOf(1L to emptyList())))
    }
}

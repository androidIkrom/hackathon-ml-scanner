package com.nungil.core.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ItemMatcherTest {
    private fun at(deg: Double) = floatArrayOf(cos(Math.toRadians(deg)).toFloat(), sin(Math.toRadians(deg)).toFloat())

    @Test fun closestSampleDecides() {
        val known = mapOf(
            1L to listOf(at(90.0), at(10.0)),  // best sample cos 10 = 0.985
            2L to listOf(at(30.0)),            // cos 30 = 0.866
        )
        assertEquals(1L, ItemMatcher.bestMatch(at(0.0), known)?.id)
    }

    @Test fun thresholdIsPointSevenFive() {
        val known = mapOf(1L to listOf(at(0.0)))
        assertEquals(1L, ItemMatcher.bestMatch(at(41.0), known)?.id) // cos 41 = 0.755
        assertNull(ItemMatcher.bestMatch(at(42.0), known))            // cos 42 = 0.743
    }

    @Test fun nothingSaved() {
        assertNull(ItemMatcher.bestMatch(at(0.0), emptyMap()))
        assertNull(ItemMatcher.bestMatch(at(0.0), mapOf(1L to emptyList())))
    }

    @Test fun mostCommonLabel() {
        assertEquals("backpack", ItemMatcher.mostCommon(listOf("suitcase", "backpack", "handbag", "backpack")))
        assertEquals("suitcase", ItemMatcher.mostCommon(listOf("suitcase", "backpack")))
        assertNull(ItemMatcher.mostCommon(emptyList()))
    }
}

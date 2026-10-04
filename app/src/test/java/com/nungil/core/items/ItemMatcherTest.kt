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

    @Test fun findingTakesALooserMatchThanNaming() {
        // The same hat seen a little to the side scored 0.5 to 0.75 against its samples (the logs), other things 0.25.
        val known = mapOf(1L to listOf(at(0.0)))
        assertEquals(1L, ItemMatcher.bestMatch(at(56.0), known, ItemMatcher.FIND_THRESHOLD)?.id) // cos 56 = 0.559
        assertNull(ItemMatcher.bestMatch(at(57.0), known, ItemMatcher.FIND_THRESHOLD))            // cos 57 = 0.545
    }

    @Test fun theItemAloneCanAddAFindAndNeverTakesOneAway() {
        val find = ItemMatcher.FIND_THRESHOLD
        assertEquals(ItemMatcher.Seen.BY_SQUARES, ItemMatcher.seen(0.55f, find, alone = 0.1f))
        assertEquals(ItemMatcher.Seen.BY_ITEM_ALONE, ItemMatcher.seen(0.40f, find, alone = 0.60f))
        assertEquals(ItemMatcher.Seen.NO, ItemMatcher.seen(0.40f, find, alone = 0.59f))
        // An empty view scored 0.29 at most: not worth a look, whatever the segmenter found there.
        assertEquals(ItemMatcher.Seen.NO, ItemMatcher.seen(0.34f, find, alone = 0.95f))
        // Once found it is kept at the lower score, as before.
        assertEquals(ItemMatcher.Seen.BY_SQUARES, ItemMatcher.seen(0.45f, ItemMatcher.KEEP_THRESHOLD, alone = null))
    }

    @Test fun oldItemsAreSeenBySquaresAlone() {
        // Nothing usable from the segmenter, or an item saved with squares only: the squares decide as they did.
        assertEquals(ItemMatcher.Seen.BY_SQUARES, ItemMatcher.seen(0.70f, ItemMatcher.FIND_THRESHOLD, alone = null))
        assertEquals(ItemMatcher.Seen.NO, ItemMatcher.seen(0.54f, ItemMatcher.FIND_THRESHOLD, alone = null))
    }

    @Test fun anItemSavedTwiceUnderOneNameIsOneItem() {
        // The logs: "My new white bottle" was saved twice. The search looked for the first, and every square
        // that looked more like the second counted for nothing.
        val names = mapOf(14L to "My new white bottle", 16L to " my new white bottle", 12L to "My white bottle")
        assertEquals(setOf(14L, 16L), ItemMatcher.sameName(14L, names))
        assertEquals(setOf(12L), ItemMatcher.sameName(12L, names))
        assertEquals(setOf(99L), ItemMatcher.sameName(99L, names))
    }
}

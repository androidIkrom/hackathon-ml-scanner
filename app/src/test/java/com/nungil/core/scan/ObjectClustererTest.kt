package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectClustererTest {
    private fun chair(angle: Float, color: ColorName? = null) = FrameDetection("chair", angle, color)

    @Test fun tunedNumbers() {
        assertEquals(20f, ObjectClusterer.MERGE_DEG)
        assertEquals(3, ObjectClusterer.CONFIRM_FRAMES)
        assertEquals(2, ObjectClusterer.MIN_COLOR_VOTES)
    }

    @Test fun countIsTheMaximumInOneFrameNeverASum() {
        val c = ObjectClusterer()
        repeat(100) { c.addFrame(listOf(chair(0f), chair(5f), chair(10f))) }
        val all = c.confirmed()
        assertEquals(1, all.size)
        assertEquals(3, all[0].count)
    }

    @Test fun countGrowsWhenOneFrameSeesMore() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f)))
        c.addFrame(listOf(chair(0f), chair(4f)))
        c.addFrame(listOf(chair(0f)))
        assertEquals(2, c.confirmed().single().count)
    }

    @Test fun unconfirmedClustersAreNeverReturned() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f)))
        c.addFrame(listOf(chair(0f)))
        assertTrue(c.confirmed().isEmpty())
    }

    @Test fun addFrameReportsTheFrameThatConfirms() {
        val c = ObjectClusterer()
        assertTrue(c.addFrame(listOf(chair(0f))).isEmpty())
        assertTrue(c.addFrame(listOf(chair(0f))).isEmpty())
        assertEquals("chair", c.addFrame(listOf(chair(0f))).single().label)
        assertTrue(c.addFrame(listOf(chair(0f))).isEmpty())
    }

    @Test fun farApartAnglesMakeTwoClusters() {
        val c = ObjectClusterer()
        repeat(3) { c.addFrame(listOf(chair(0f), chair(90f))) }
        assertEquals(2, c.confirmed().size)
    }

    @Test fun mergeAcrossNorth() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(355f)))
        c.addFrame(listOf(chair(8f)))
        c.addFrame(listOf(chair(2f)))
        assertEquals(1, c.confirmed().size)
    }

    @Test fun differentLabelsNeverMerge() {
        val c = ObjectClusterer()
        repeat(3) { c.addFrame(listOf(chair(0f), FrameDetection("laptop", 0f, null))) }
        assertEquals(setOf("chair", "laptop"), c.confirmed().map { it.label }.toSet())
    }

    @Test fun colourFlickerDoesNotSplitTheCluster() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        c.addFrame(listOf(chair(0f, ColorName.GRAY)))
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        val only = c.confirmed().single()
        assertEquals(ColorName.BLUE, only.color)
    }

    @Test fun oneVoteIsNotEnoughForAColour() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        c.addFrame(listOf(chair(0f)))
        c.addFrame(listOf(chair(0f)))
        assertNull(c.confirmed().single().color)
    }

    @Test fun aTieGivesNoColour() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        c.addFrame(listOf(chair(0f, ColorName.RED)))
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        c.addFrame(listOf(chair(0f, ColorName.RED)))
        assertNull(c.confirmed().single().color)
    }

    @Test fun namedPersonKeepsItsFlags() {
        val c = ObjectClusterer()
        repeat(3) { c.addFrame(listOf(FrameDetection("Ali", 10f, null, isName = true, wasPerson = true))) }
        val ali = c.confirmed().single()
        assertTrue(ali.isName)
        assertTrue(ali.wasPerson)
    }
}

package com.nungil.core.people

import com.nungil.contract.Box
import com.nungil.contract.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceBoxesTest {
    private val big = Detection("person", 0.9f, Box(0f, 0f, 1f, 1f))
    private val left = Detection("person", 0.9f, Box(0.1f, 0.1f, 0.4f, 0.9f))
    private val right = Detection("person", 0.9f, Box(0.6f, 0.1f, 0.9f, 0.9f))
    private val chair = Detection("chair", 0.9f, Box(0.1f, 0.1f, 0.4f, 0.9f))

    @Test fun smallestContainingPersonBox() {
        assertEquals(1, FaceBoxes.personBoxFor(0.2f, 0.2f, listOf(big, left, right)))
        assertEquals(0, FaceBoxes.personBoxFor(0.5f, 0.5f, listOf(big, left, right)))
        assertEquals(-1, FaceBoxes.personBoxFor(0.2f, 0.2f, listOf(chair)))
    }

    @Test fun oneNamePerBoxAndOneBoxPerPerson() {
        val dets = listOf(left, right)
        val ali = FaceHit(0.2f, 0.3f, 1, "Ali", 0.7f)
        val aliAgain = FaceHit(0.7f, 0.3f, 1, "Ali", 0.6f)   // same person twice: keep the better one
        val jiwoo = FaceHit(0.25f, 0.5f, 2, "지우", 0.65f)     // second face in the same box: dropped
        val result = FaceBoxes.assign(listOf(aliAgain, jiwoo, ali), dets)
        assertEquals(mapOf(0 to ali), result)
    }

    @Test fun faceOutsideEveryBoxIsIgnored() =
        assertTrue(FaceBoxes.assign(listOf(FaceHit(0.5f, 0.5f, 1, "Ali", 0.9f)), listOf(left, right)).isEmpty())

    @Test fun everyThirdPersonFrame() {
        val nth = EveryNth(3)
        assertEquals(listOf(true, false, false, true, false, false, true), List(7) { nth.take() })
        assertTrue(EveryNth(1).take())
        assertFalse(EveryNth(2).let { it.take(); it.take() })
    }
}

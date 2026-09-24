package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickyNamesTest {
    private val face = Box(0.4f, 0.2f, 0.6f, 0.8f)
    private val moved = Box(0.42f, 0.2f, 0.62f, 0.8f)
    private val elsewhere = Box(0f, 0f, 0.1f, 0.1f)

    @Test fun tunedNumbers() {
        assertEquals(2, StickyNames.CONFIRM_HITS)
        assertEquals(15, StickyNames.FORGET_AFTER_FRAMES)
        assertEquals(0.3f, StickyNames.MIN_IOU)
    }

    @Test fun oneHitIsNotEnough() {
        val s = StickyNames()
        s.recognized(face, "Ali", isPerson = true)
        assertNull(s.apply(listOf(face)).single())
    }

    @Test fun twoHitsMakeTheNameStickToTheMovingBox() {
        val s = StickyNames()
        s.recognized(face, "Ali", isPerson = true)
        s.recognized(face, "Ali", isPerson = true)
        assertEquals(StickyNames.Sticky("Ali", true), s.apply(listOf(moved)).single())
        assertEquals(StickyNames.Sticky("Ali", true), s.apply(listOf(moved)).single())
    }

    @Test fun otherBoxesStayUnnamed() {
        val s = StickyNames()
        repeat(2) { s.recognized(face, "Ali", isPerson = true) }
        assertEquals(listOf(StickyNames.Sticky("Ali", true), null), s.apply(listOf(face, elsewhere)))
    }

    @Test fun aDifferentNameRestartsTheCount() {
        val s = StickyNames()
        repeat(2) { s.recognized(face, "Ali", isPerson = true) }
        s.recognized(face, "Mina", isPerson = true)
        assertNull(s.apply(listOf(face)).single())
    }

    @Test fun trackIsForgottenAfter15FramesUnseen() {
        val s = StickyNames()
        repeat(2) { s.recognized(face, "Ali", isPerson = true) }
        repeat(16) { s.apply(emptyList()) }
        assertNull(s.apply(listOf(face)).single())
    }
}

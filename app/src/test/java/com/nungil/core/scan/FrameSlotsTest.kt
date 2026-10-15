package com.nungil.core.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSlotsTest {
    @Test fun twoFramesAtMostOneBeingMadeWhileOneIsDetected() {
        val slots = FrameSlots(2)
        assertTrue(slots.take())
        assertTrue(slots.take())
        assertFalse("a third frame is dropped", slots.take())
        slots.free()
        assertTrue(slots.take())
    }

    @Test fun oneSlotIsOneFrameAtATime() {
        val slots = FrameSlots(1)
        assertTrue(slots.take())
        assertFalse(slots.take())
        slots.free()
        assertTrue(slots.take())
    }
}

package com.nungil.core.people

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmWindowTest {
    private val c = ConfirmWindow()

    @Test fun firstPressOnlyArms() = assertFalse(c.press(0))

    @Test fun secondPressWithinFiveSecondsConfirms() {
        c.press(1_000)
        assertTrue(c.press(6_000))
    }

    @Test fun tooLateArmsAgain() {
        c.press(0)
        assertFalse(c.press(5_001))
        assertTrue(c.press(6_000))
    }

    @Test fun confirmingDisarms() {
        c.press(0)
        c.press(100)
        assertFalse(c.press(200))
    }
}

package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelabelsTest {
    private val keys = Box(0.3f, 0.5f, 0.7f, 0.8f)
    private val moved = Box(0.32f, 0.52f, 0.72f, 0.82f)
    private val keyboard = Detection("keyboard", 0.8f, keys)

    @Test fun aKeyboardNotLookedAtYetIsChecked() {
        val r = Relabels()
        assertTrue(r.needsCheck(keyboard))
        assertFalse(r.needsCheck(Detection("cup", 0.8f, keys)))
    }

    @Test fun aKeyboardThatIsALaptopIsCalledALaptopWhileInView() {
        val r = Relabels()
        r.checked(keys, "laptop")
        assertEquals(listOf("laptop"), r.apply(listOf(Detection("keyboard", 0.8f, moved))).map { it.label })
        assertFalse(r.needsCheck(Detection("keyboard", 0.8f, moved)))
    }

    @Test fun aKeyboardThatIsAKeyboardStaysOneAndIsNotCheckedAgain() {
        val r = Relabels()
        r.checked(keys, "keyboard")
        assertEquals(listOf("keyboard"), r.apply(listOf(keyboard)).map { it.label })
        assertFalse(r.needsCheck(keyboard))
    }

    @Test fun aCheckIsForgottenWhenTheThingIsGone() {
        val r = Relabels()
        r.checked(keys, "laptop")
        repeat(Relabels.FORGET_AFTER_FRAMES + 1) { r.apply(emptyList()) }
        assertTrue(r.needsCheck(keyboard))
    }
}

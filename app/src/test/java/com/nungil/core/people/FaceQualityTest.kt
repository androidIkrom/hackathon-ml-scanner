package com.nungil.core.people

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityTest {
    @Test fun goodFace() = assertTrue(FaceQuality.usable(64, 0f, 0f))

    @Test fun tooSmall() = assertFalse(FaceQuality.usable(63, 0f, 0f))

    @Test fun yawLimit() {
        assertTrue(FaceQuality.usable(100, 35f, 0f))
        assertFalse(FaceQuality.usable(100, -35.1f, 0f))
    }

    @Test fun pitchLimit() {
        assertTrue(FaceQuality.usable(100, 0f, -25f))
        assertFalse(FaceQuality.usable(100, 0f, 25.1f))
    }
}

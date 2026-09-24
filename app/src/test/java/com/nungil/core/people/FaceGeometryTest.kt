package com.nungil.core.people

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceGeometryTest {
    @Test fun eyeAngleIgnoresWhichEyeIsWhich() {
        assertEquals(0f, FaceGeometry.eyeAngleDeg(100f, 50f, 200f, 50f), 1e-4f)
        assertEquals(45f, FaceGeometry.eyeAngleDeg(100f, 50f, 200f, 150f), 1e-4f)
        assertEquals(45f, FaceGeometry.eyeAngleDeg(200f, 150f, 100f, 50f), 1e-4f)
        assertEquals(-10f, FaceGeometry.eyeAngleDeg(0f, 0f, 100f, -17.6327f), 1e-3f)
    }

    @Test fun levelingFromThreeDegrees() {
        assertFalse(FaceGeometry.needsLeveling(2.9f))
        assertTrue(FaceGeometry.needsLeveling(3f))
        assertTrue(FaceGeometry.needsLeveling(-4f))
    }

    @Test fun expandAddsMarginAndClamps() {
        assertArrayEquals(intArrayOf(90, 90, 210, 210), FaceGeometry.expand(100, 100, 200, 200, 0.1f, 640, 480))
        assertArrayEquals(intArrayOf(0, 0, 59, 59), FaceGeometry.expand(5, 5, 45, 45, 0.35f, 640, 480))
        assertArrayEquals(intArrayOf(600, 440, 640, 480), FaceGeometry.expand(610, 450, 640, 480, 0.35f, 640, 480))
    }

    @Test fun aroundACentre() =
        assertArrayEquals(intArrayOf(40, 30, 60, 70), FaceGeometry.around(50f, 50f, 10f, 20f, 640, 480))

    @Test fun minimumCropSize() {
        assertTrue(FaceGeometry.bigEnough(intArrayOf(0, 0, 24, 24)))
        assertFalse(FaceGeometry.bigEnough(intArrayOf(0, 0, 23, 40)))
    }
}

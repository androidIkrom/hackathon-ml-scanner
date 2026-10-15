package com.nungil.core.items

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentInputTest {
    @Test fun pixelsBecomeRedGreenBlueFromZeroToOneAndTheDot() {
        val out = FloatArray(4 * 4)
        // A 2 x 2 square from a 2 x 2 picture, pointed at its top-left pixel: a dot one pixel wide reaches its
        // neighbours across and down (OpenCV's circle), not the one corner to corner.
        SegmentInput.fill(intArrayOf(0xFFFF8000.toInt(), 0x00000033, 0, 0), 2, 0.25f, 0.25f, 2, 2, out)
        assertArrayEquals(floatArrayOf(1f, 128f / 255f, 0f, 1f, 0f, 0f, 51f / 255f, 1f), out.copyOf(8), 1e-6f)
        assertEquals(1f, out[11], 0f)
        assertEquals(0f, out[15], 0f)
    }

    @Test fun theDotIsWhereThePictureWasPointedAt() {
        val side = 16
        val out = FloatArray(side * side * 4)
        // A 64 x 32 picture pointed at pixel 48, 8: the dot is 4 picture pixels wide, so one square pixel across
        // and two down, around 11.6, 3.75 of the square.
        SegmentInput.fill(IntArray(side * side), side, 0.75f, 0.25f, 64, 32, out)
        val dot = (0 until side * side).filter { out[it * 4 + 3] == 1f }.map { it % side to it / side }
        assertEquals(listOf(12 to 2, 11 to 3, 12 to 3, 11 to 4, 12 to 4, 11 to 5, 12 to 5), dot)
    }

    @Test fun theAnswerIsStretchedOverThePicture() {
        // Equal everywhere stays equal.
        assertArrayEquals(FloatArray(12) { 0.5f }, SegmentInput.stretch(FloatArray(4) { 0.5f }, 2, 4, 3), 1e-6f)
        // Left 0, right 1: the edges keep them, the middle blends.
        val row = SegmentInput.stretch(floatArrayOf(0f, 1f, 0f, 1f), 2, 4, 1)
        assertArrayEquals(floatArrayOf(0f, 0.25f, 0.75f, 1f), row, 1e-6f)
        // Same size: unchanged.
        val v = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        assertArrayEquals(v, SegmentInput.stretch(v, 2, 2, 2), 1e-6f)
    }
}

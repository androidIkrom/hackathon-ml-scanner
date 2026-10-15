package com.nungil.core.items

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class EmbedInputTest {
    @Test fun pixelsBecomeRedGreenBlueFromZeroToOne() {
        val out = FloatArray(6)
        EmbedInput.fill(intArrayOf(0xFFFF8000.toInt(), 0x00000033), out)
        assertArrayEquals(floatArrayOf(1f, 128f / 255f, 0f, 0f, 0f, 51f / 255f), out, 1e-6f)
    }

    @Test fun theVectorIsMadeOneLong() {
        val v = EmbedInput.unit(floatArrayOf(3f, 4f))
        assertArrayEquals(floatArrayOf(0.6f, 0.8f), v, 1e-6f)
        assertEquals(1.0, sqrt(EmbedInput.unit(floatArrayOf(1f, 2f, 2f)).sumOf { it.toDouble() * it }), 1e-6)
        // All zero stays zero, not NaN.
        assertArrayEquals(floatArrayOf(0f, 0f), EmbedInput.unit(floatArrayOf(0f, 0f)), 0f)
    }
}

package com.nungil.core.people

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VectorBytesTest {
    @Test fun roundTrip() {
        val v = floatArrayOf(0f, 1.5f, -2.25f, 1e-7f, 512f)
        assertArrayEquals(v, VectorBytes.toFloats(VectorBytes.toBytes(v)), 0f)
    }

    @Test fun littleEndian() =
        assertArrayEquals(byteArrayOf(0, 0, 0x80.toByte(), 0x3F), VectorBytes.toBytes(floatArrayOf(1f)))

    @Test fun emptyVector() = assertArrayEquals(FloatArray(0), VectorBytes.toFloats(ByteArray(0)), 0f)

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBrokenLength() {
        VectorBytes.toFloats(ByteArray(5))
    }
}

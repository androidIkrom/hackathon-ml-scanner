package com.nungil.core.people

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class FaceNetPreprocessTest {
    @Test fun channelOrderIsRgb() {
        // One pixel R=16, G=32, B=48: mean 32, stddev sqrt(512/3) = 13.064
        val out = FaceNetPreprocess.standardize(intArrayOf(0xFF102030.toInt()))
        assertArrayEquals(floatArrayOf(-1.2247449f, 0f, 1.2247449f), out, 1e-5f)
    }

    @Test fun uniformImageBecomesZeros() {
        val out = FaceNetPreprocess.standardize(IntArray(4) { 0xFF808080.toInt() })
        assertArrayEquals(FloatArray(12), out, 0f)
    }

    @Test fun meanZeroStdOne() {
        val random = Random(7)
        val pixels = IntArray(FaceNetPreprocess.SIZE * FaceNetPreprocess.SIZE) { random.nextInt() or (0xFF shl 24) }
        val out = FaceNetPreprocess.standardize(pixels)
        assertEquals(FaceNetPreprocess.SIZE * FaceNetPreprocess.SIZE * 3, out.size)
        val mean = out.average()
        val std = sqrt(out.sumOf { (it - mean) * (it - mean) } / out.size)
        assertEquals(0.0, mean, 1e-4)
        assertEquals(1.0, std, 1e-4)
    }

    @Test fun emptyInput() = assertEquals(0, FaceNetPreprocess.standardize(IntArray(0)).size)
}

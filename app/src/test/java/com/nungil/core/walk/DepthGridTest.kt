package com.nungil.core.walk

import com.nungil.contract.Box
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepthGridTest {
    @Test fun fillsThroughTheLookupTable() {
        val depth = shortArrayOf(1500, 0, 3000, (-1).toShort())
        val out = FloatArray(4)
        DepthGrid.fill(depth, intArrayOf(2, -1, 0, 3), out)
        assertArrayEquals(floatArrayOf(3f, 0f, 1.5f, 65.535f), out, 0.0001f)
    }

    @Test fun medianInsideABox() {
        val w = 4
        val h = 2
        val grid = floatArrayOf(1f, 2f, 3f, 9f, 0f, 2f, 20f, 9f)
        assertEquals(2f, DepthGrid.medianIn(grid, w, h, Box(0f, 0f, 0.75f, 1f))!!, 0f)
        assertNull(DepthGrid.medianIn(FloatArray(8), w, h, Box(0f, 0f, 1f, 1f)))
    }

    @Test fun zonesAreThirds() {
        assertEquals(Zone.LEFT, DepthGrid.zoneOf(0.2f))
        assertEquals(Zone.AHEAD, DepthGrid.zoneOf(0.5f))
        assertEquals(Zone.RIGHT, DepthGrid.zoneOf(0.8f))
    }

    @Test fun arcoreGroundLabels() {
        assertEquals(GroundKind.ROAD, SemanticGround.kind(4))
        assertEquals(GroundKind.SIDEWALK, SemanticGround.kind(5))
        assertNull(SemanticGround.kind(1))
    }
}

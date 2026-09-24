package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayMathTest {
    private val eps = 0.01f
    private val full = Box(0f, 0f, 1f, 1f)

    @Test fun sameAspectFillsTheView() {
        val r = OverlayMath.toView(full, 480, 640, 960, 1280, mirrored = false)
        assertEquals(OverlayMath.ViewRect(0f, 0f, 960f, 1280f), r)
    }

    @Test fun tallerViewCropsTheSides() {
        // 3:4 image in a 1000 x 2000 view: scale = max(1000/480, 2000/640) = 3.125 -> 1500 x 2000, 250 px cut each side.
        val r = OverlayMath.toView(full, 480, 640, 1000, 2000, mirrored = false)
        assertEquals(-250f, r.left, eps)
        assertEquals(1250f, r.right, eps)
        assertEquals(0f, r.top, eps)
        assertEquals(2000f, r.bottom, eps)
    }

    @Test fun centreBoxStaysCentred() {
        val r = OverlayMath.toView(Box(0.4f, 0.4f, 0.6f, 0.6f), 480, 640, 1000, 2000, mirrored = false)
        assertEquals(500f, (r.left + r.right) / 2f, eps)
        assertEquals(1000f, (r.top + r.bottom) / 2f, eps)
    }

    @Test fun mirroredFlipsLeftAndRight() {
        val r = OverlayMath.toView(Box(0f, 0f, 0.25f, 1f), 480, 640, 480, 640, mirrored = true)
        assertEquals(360f, r.left, eps)
        assertEquals(480f, r.right, eps)
    }
}

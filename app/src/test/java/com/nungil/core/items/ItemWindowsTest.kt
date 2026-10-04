package com.nungil.core.items

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemWindowsTest {
    private fun assertBox(l: Float, t: Float, r: Float, b: Float, box: Box) {
        assertEquals(l, box.left, 1e-4f)
        assertEquals(t, box.top, 1e-4f)
        assertEquals(r, box.right, 1e-4f)
        assertEquals(b, box.bottom, 1e-4f)
    }

    @Test fun learningLooksAtASquareInTheMiddle() {
        // 0.7 of the shorter side: 336 px of a 480 x 640 frame.
        assertBox(0.15f, 0.2375f, 0.85f, 0.7625f, ItemWindows.center(480, 640))
        assertBox(0.2375f, 0.15f, 0.7625f, 0.85f, ItemWindows.center(640, 480))
    }

    @Test fun findingCoversTheFrameWithSquaresOfTwoSizes() {
        val grid = ItemWindows.grid(480, 640)
        // 336 px squares: 2 x 3. 216 px squares: 4 x 5.
        assertEquals(26, grid.size)
        assertBox(0f, 0f, 0.7f, 0.525f, grid.first())
        assertBox(0.55f, 0.6625f, 1f, 1f, grid.last())
        assertTrue(grid.all { it.left >= 0f && it.top >= 0f && it.right <= 1f && it.bottom <= 1f })
        // Squares in pixels, whatever the frame's shape.
        assertTrue(grid.all { Math.abs(it.width * 480 - it.height * 640) < 1f })
    }

    @Test fun theItemIsWhereTheBestWindowsAre() {
        val left = Box(0f, 0.2f, 0.4f, 0.6f)
        val middle = Box(0.2f, 0.2f, 0.6f, 0.6f)
        val right = Box(0.6f, 0.2f, 1f, 0.6f)
        val windows = listOf(left, middle, right)
        assertNull(ItemWindows.locate(windows, floatArrayOf(0.3f, 0.5f, 0.1f), 0.55f))
        assertBox(0f, 0.2f, 0.4f, 0.6f, ItemWindows.locate(windows, floatArrayOf(0.9f, 0.5f, 0.1f), 0.55f)!!)
        // Two windows see it equally well: it is between them.
        assertBox(0.1f, 0.2f, 0.5f, 0.6f, ItemWindows.locate(windows, floatArrayOf(0.8f, 0.8f, 0.1f), 0.55f)!!)
        // The better window pulls it its way.
        val pulled = ItemWindows.locate(windows, floatArrayOf(0.9f, 0.6f, 0.1f), 0.55f)!!
        assertTrue(pulled.centerX > left.centerX && pulled.centerX < 0.3f)
    }

    @Test fun aCloserLookGoesAroundTheBestWindow() {
        // A 216 px square in the middle of a 480 x 640 frame.
        val window = Box(0.275f, 0.33125f, 0.725f, 0.66875f)
        val around = ItemWindows.around(window)
        // Smaller in place and a quarter to each side, the same size a quarter to each side, bigger in place.
        assertEquals(10, around.size)
        assertTrue(around.none { it == window })
        assertBox(0.33125f, 0.3734375f, 0.66875f, 0.6265625f, around.first())
        assertBox(0.1625f, 0.33125f, 0.6125f, 0.66875f, around[5])
        assertBox(0.2075f, 0.280625f, 0.7925f, 0.719375f, around.last())
        // Still squares in pixels.
        assertTrue(around.all { Math.abs(it.width * 480 - it.height * 640) < 0.01f })
    }

    @Test fun aCloserLookStaysInsideTheFrame() {
        val corner = Box(0f, 0f, 0.45f, 0.3375f)
        val around = ItemWindows.around(corner)
        assertTrue(around.all { it.left >= 0f && it.top >= 0f && it.right <= 1f && it.bottom <= 1f })
        // Moved back inside, not cut: every window keeps its size.
        assertTrue(around.all { it.width > 0.33f && it.height > 0.25f })
        // What would land on the window itself is left out.
        assertTrue(around.none { Math.abs(it.left - corner.left) < 1e-4f && Math.abs(it.top - corner.top) < 1e-4f && Math.abs(it.width - corner.width) < 1e-4f })
    }

    @Test fun aSquareAroundTheThing() {
        // A 100 x 200 px thing in a 480 x 640 frame: a square of 230 px (200 + 15%) around its middle.
        val around = ItemWindows.square(Box(0.2f, 0.25f, 0.4083333f, 0.5625f), 480, 640)
        assertEquals(230f, around.width * 480, 0.6f)
        assertEquals(230f, around.height * 640, 0.6f)
        assertEquals(0.3041667f, around.centerX, 1e-3f)
        assertEquals(0.40625f, around.centerY, 1e-3f)
        // At the edge of the frame the square is moved back inside, never cut.
        val corner = ItemWindows.square(Box(0f, 0f, 0.3f, 0.1f), 480, 640)
        assertEquals(0f, corner.left, 1e-6f)
        assertEquals(0f, corner.top, 1e-6f)
        assertEquals(166f, corner.width * 480, 0.6f)
        // A thing bigger than the shorter side of the frame: the whole width.
        assertEquals(1f, ItemWindows.square(Box(0f, 0.1f, 1f, 0.9f), 480, 640).width, 1e-6f)
    }

    @Test fun nothingToLocate() = assertNull(ItemWindows.locate(emptyList(), FloatArray(0), 0.55f))

    @Test fun followingTheItemKeepsTheWindowsSize() {
        // Following with around() changed the window in every frame: it never holds the window itself, and its
        // 0.75x and 1.3x sizes added up until the square was the whole frame (the logs: 8 squares, not 10).
        val window = Box(0.3f, 0.3f, 0.6f, 0.525f)   // 144 x 144 px of 480 x 640
        val near = ItemWindows.near(window)
        assertEquals(9, near.size)
        assertBox(0.3f, 0.3f, 0.6f, 0.525f, near.first())
        assertTrue(near.all { Math.abs(it.width - window.width) < 1e-5f && Math.abs(it.height - window.height) < 1e-5f })
        // A quarter of the window to each side, and to the corners.
        assertTrue(near.any { Math.abs(it.left - 0.375f) < 1e-5f && Math.abs(it.top - 0.3f) < 1e-5f })
        assertTrue(near.any { Math.abs(it.left - 0.225f) < 1e-5f && Math.abs(it.top - 0.24375f) < 1e-5f })
    }

    @Test fun followingAtTheEdgeOfTheFrame() {
        // As wide as the frame: it can only move up and down.
        val wide = ItemWindows.near(Box(0f, 0.125f, 1f, 0.875f))
        assertEquals(3, wide.size)
        assertTrue(wide.all { it.left == 0f && it.right == 1f })
        // In a corner: the shifts that would leave the frame fall onto the window or a neighbour.
        val corner = ItemWindows.near(Box(0f, 0f, 0.3f, 0.225f))
        assertEquals(4, corner.size)
        assertTrue(corner.all { it.left >= 0f && it.top >= 0f })
    }
}

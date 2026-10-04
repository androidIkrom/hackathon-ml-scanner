package com.nungil.core.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemMaskTest {
    private val fill = 0x66112233
    private val edge = 0xFF112233.toInt()

    /** A [size] x [size] picture with [inside] in columns and rows [from]..[to] and [outside] everywhere else. */
    private fun square(size: Int, from: Int, to: Int, inside: Float = 1f, outside: Float = 0f) =
        FloatArray(size * size) { i -> if (i % size in from..to && i / size in from..to) inside else outside }

    @Test fun theItemIsTintedWithALineAroundIt() {
        val pixels = ItemMask.of(square(20, 6, 13), 20, 20)!!.pixels(fill, edge)
        assertEquals(0, pixels[2 * 20 + 2])       // the table
        assertEquals(0, pixels[10 * 20 + 5])      // just outside
        assertEquals(edge, pixels[10 * 20 + 6])   // the item's rim, two pixels wide
        assertEquals(edge, pixels[10 * 20 + 7])
        assertEquals(fill, pixels[10 * 20 + 8])
        assertEquals(fill, pixels[10 * 20 + 10])
        assertEquals(edge, pixels[13 * 20 + 10])
    }

    @Test fun theItemIsWhatIsInTheMiddleWhicheverWayTheModelCountsIt() {
        // 0 for the thing and 1 for the rest gives the same picture.
        val pixels = ItemMask.of(square(20, 6, 13, inside = 0f, outside = 1f), 20, 20)!!.pixels(fill, edge)
        assertEquals(0, pixels[2 * 20 + 2])
        assertEquals(fill, pixels[10 * 20 + 10])
        assertEquals(edge, pixels[10 * 20 + 6])
    }

    @Test fun theTableOrTheWholeViewIsNotAnItem() {
        assertNull(ItemMask.of(FloatArray(400) { 1f }, 20, 20))
        assertNull(ItemMask.of(FloatArray(400) { 0f }, 20, 20))
        // 16 x 16 of 20 x 20 is 64%: still an item. 18 x 18 is 81%: the table.
        assertEquals(400, ItemMask.of(square(20, 2, 17), 20, 20)?.pixels(fill, edge)?.size)
        assertNull(ItemMask.of(square(20, 1, 18), 20, 20))
    }

    @Test fun aSpeckIsNotAnItem() {
        // 9 of 2 500 pixels: under 1% of the frame.
        assertNull(ItemMask.of(square(50, 24, 26), 50, 50))
    }

    @Test fun aMaskOfTheWrongSizeIsIgnored() = assertNull(ItemMask.of(FloatArray(399) { 1f }, 20, 20))

    @Test fun colourIsReadOnTheItemOnly() {
        val points = ItemMask.of(square(20, 6, 13), 20, 20)!!.samplePoints(grid = 4)
        // 4 x 4 points over the item's own 8 x 8 pixels, as x0, y0, x1, y1, ...
        assertEquals(32, points.size)
        assertTrue(points.all { it in 6..13 })
        assertEquals(listOf(7, 7), points.take(2))
        // A corner of the square is table: the 4 points that land there are left out.
        val bitten = square(20, 6, 13).also { for (y in 11..13) for (x in 11..13) it[y * 20 + x] = 0f }
        assertEquals(24, ItemMask.of(bitten, 20, 20)!!.samplePoints(grid = 4).size)
    }

    @Test fun theAnchorIsOnTheItem() {
        // A thin bar from top-left to bottom-right: the middle of its box is on it, and so is the anchor.
        val bar = FloatArray(400) { i -> if (Math.abs(i % 20 - i / 20) <= 1) 1f else 0f }
        val onBar = ItemMask.of(bar, 20, 20)!!
        assertEquals(0.5f, onBar.anchorX, 0.06f)
        assertEquals(0.5f, onBar.anchorY, 0.06f)
        // A thin bar from top-right to bottom-left, missing its middle: the middle of the box is table.
        val broken = FloatArray(400) { i -> if (Math.abs(i % 20 + i / 20 - 19) <= 2 && !(i / 20 in 8..11)) 1f else 0f }
        val onBroken = ItemMask.of(broken, 20, 20, x = 0.15f, y = 0.85f)!!
        val ax = (onBroken.anchorX * 20).toInt()
        val ay = (onBroken.anchorY * 20).toInt()
        assertTrue("anchor $ax,$ay is not on the bar", onBroken.inside[ay * 20 + ax])
    }

    @Test fun theItemAloneKeepsItsPixelsAndGraysTheRest() {
        val mask = ItemMask.of(square(20, 6, 13), 20, 20)!!
        val pixels = IntArray(400) { 0xFF000000.toInt() or it }
        mask.alone(pixels, 0, 0, 20, 20, 20, 20)
        assertEquals(ItemMask.ALONE_FILL, pixels[2 * 20 + 2])
        assertEquals(0xFF000000.toInt() or (10 * 20 + 10), pixels[10 * 20 + 10])
        assertEquals(ItemMask.ALONE_FILL, pixels[10 * 20 + 5])
        assertEquals(0xFF000000.toInt() or (10 * 20 + 6), pixels[10 * 20 + 6])
    }

    @Test fun aPartOfTheFrame() {
        // The 10 x 10 part that starts at (5, 5): the item is its columns and rows 1..8.
        val mask = ItemMask.of(square(20, 6, 13), 20, 20)!!
        val pixels = IntArray(100) { 0xFF123456.toInt() }
        mask.alone(pixels, 5, 5, 10, 10, 20, 20)
        assertEquals(ItemMask.ALONE_FILL, pixels[0])
        assertEquals(0xFF123456.toInt(), pixels[1 * 10 + 1])
        assertEquals(0xFF123456.toInt(), pixels[8 * 10 + 8])
        assertEquals(ItemMask.ALONE_FILL, pixels[9 * 10 + 9])
    }

    @Test fun aMaskOfAnotherSizeIsStretchedOverTheFrame() {
        // A 20 x 20 mask over a 40 x 40 frame: the item is frame pixels 12..27.
        val mask = ItemMask.of(square(20, 6, 13), 20, 20)!!
        val pixels = IntArray(1600) { 0xFF123456.toInt() }
        mask.alone(pixels, 0, 0, 40, 40, 40, 40)
        assertEquals(ItemMask.ALONE_FILL, pixels[20 * 40 + 11])
        assertEquals(0xFF123456.toInt(), pixels[20 * 40 + 12])
        assertEquals(0xFF123456.toInt(), pixels[20 * 40 + 27])
        assertEquals(ItemMask.ALONE_FILL, pixels[20 * 40 + 28])
    }

    @Test fun aSquareAtTheEdgeOfTheFrame() {
        // The last 8 columns and rows of the frame: nothing is read past its edge.
        val mask = ItemMask.of(square(20, 6, 13), 20, 20)!!
        val pixels = IntArray(64) { 0xFF123456.toInt() }
        mask.alone(pixels, 12, 12, 8, 8, 20, 20)
        assertEquals(0xFF123456.toInt(), pixels[0])          // frame (12, 12)
        assertEquals(ItemMask.ALONE_FILL, pixels[7 * 8 + 7]) // frame (19, 19)
    }

    @Test fun theItemsBox() {
        val box = ItemMask.of(square(20, 6, 13), 20, 20)!!.box
        assertEquals(0.3f, box.left, 1e-6f)
        assertEquals(0.7f, box.right, 1e-6f)
        assertEquals(0.3f, box.top, 1e-6f)
        assertEquals(0.7f, box.bottom, 1e-6f)
    }

    @Test fun howMuchOfTheViewTheThingCovers() {
        assertEquals(1f, ItemMask.coverAt(FloatArray(400) { 1f }, 20, 20), 1e-6f)
        // Too much of the view for an item, so of() gives nothing; the cover says why.
        assertEquals(0.81f, ItemMask.coverAt(square(20, 1, 18), 20, 20), 1e-6f)
        assertEquals(0.16f, ItemMask.coverAt(square(20, 6, 13), 20, 20), 1e-6f)
        assertEquals(0.16f, ItemMask.coverAt(square(20, 6, 13, inside = 0f, outside = 1f), 20, 20), 1e-6f)
        assertEquals(0f, ItemMask.coverAt(FloatArray(399) { 1f }, 20, 20), 1e-6f)
    }
}

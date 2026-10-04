package com.nungil.core.items

import com.nungil.core.scan.ColorName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemColorTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun many(n: Int, p: Int) = IntArray(n) { p }

    private val plain = floatArrayOf(1f, 1f, 1f)
    private val black = rgb(20, 20, 25)
    private val glossyBlack = rgb(55, 60, 80)      // v 0.31, s 0.31: a dark glossy surface with a bluish sheen
    private val navy = rgb(20, 30, 90)              // v 0.35, s 0.78: a real dark blue
    private val blue = rgb(40, 90, 200)
    private val white = rgb(240, 240, 235)
    private val gray = rgb(150, 150, 150)

    @Test fun theMostCommonColourWinsEvenWhenItIsBlack() {
        // The hat (the logs): 48% black, 33% blue sheen. Scan's rule let the colourful third win; on an item it is black.
        assertEquals(ColorName.BLACK, ItemColor.name(many(48, black) + many(33, blue) + many(19, white), plain, frameIsDark = false))
        assertEquals(ColorName.BLUE, ItemColor.name(many(60, blue) + many(40, black), plain, frameIsDark = false))
    }

    @Test fun darkDullPixelsAreBlackNotTheirSheen() {
        assertEquals(ColorName.BLACK, ItemColor.name(many(10, glossyBlack), plain, frameIsDark = false))
        assertEquals(ColorName.BLUE, ItemColor.name(many(10, navy), plain, frameIsDark = false))
    }

    @Test fun lightThings() {
        assertEquals(ColorName.WHITE, ItemColor.name(many(43, white) + many(38, gray) + many(19, blue), plain, frameIsDark = false))
        assertEquals(ColorName.GRAY, ItemColor.name(many(5, gray) + many(4, white), plain, frameIsDark = false))
    }

    @Test fun whiteUnderWarmLightIsStillWhite() {
        // Warm light: white comes out as (240, 215, 170), hue 32, saturation 0.29. The frame's gains undo it.
        val warmWhite = rgb(240, 215, 170)
        assertEquals(ColorName.ORANGE, ItemColor.name(many(10, warmWhite), plain, frameIsDark = false))
        assertEquals(ColorName.WHITE, ItemColor.name(many(10, warmWhite), floatArrayOf(0.87f, 0.97f, 1.23f), frameIsDark = false))
    }

    @Test fun whiteOnAWarmTableIsNotMadeBlue() {
        // The charger (the logs): white, a little cool from the camera, saturation 0.11, on a light wooden table
        // that filled the frame. The frame's gains took it to 0.2, and it was called blue (63%).
        val coolWhite = rgb(214, 226, 240)
        val woodFrame = floatArrayOf(0.95f, 1f, 1.05f)
        assertEquals(ColorName.WHITE, ItemColor.name(many(10, coolWhite), woodFrame, frameIsDark = false))
        // A blue thing on the same table is still blue.
        assertEquals(ColorName.BLUE, ItemColor.name(many(10, blue), woodFrame, frameIsDark = false))
    }

    @Test fun whiteInADimFrameIsJudgedAgainstTheBrightestThingInIt() {
        // The bottle (the logs): value 0.6 in a frame whose brightest parts were 0.71. White on that evening, gray at noon.
        val dim = rgb(153, 153, 150)
        val frame = many(90, rgb(60, 60, 60)) + many(10, rgb(181, 181, 181))
        assertEquals(0.71f, ItemColor.whiteLevel(frame, plain), 0.01f)
        assertEquals(ColorName.GRAY, ItemColor.name(many(10, dim), plain, frameIsDark = false))
        assertEquals(ColorName.WHITE, ItemColor.name(many(10, dim), plain, frameIsDark = false, whiteLevel = 0.71f))
        // The wall behind it, 0.45, is still gray; a red thing is still red.
        assertEquals(ColorName.GRAY, ItemColor.name(many(10, rgb(115, 115, 115)), plain, frameIsDark = false, whiteLevel = 0.71f))
        assertEquals(ColorName.RED, ItemColor.name(many(10, rgb(150, 20, 20)), plain, frameIsDark = false, whiteLevel = 0.71f))
        // A bright frame is read as it is, and a very dark one is not stretched past MIN_WHITE_LEVEL.
        assertEquals(1f, ItemColor.whiteLevel(many(10, rgb(255, 255, 255)), plain), 0.01f)
        assertEquals(ItemColor.MIN_WHITE_LEVEL, ItemColor.whiteLevel(many(10, rgb(30, 30, 30)), plain), 0.01f)
    }

    @Test fun nothingInTheDarkOrFromNoPixels() {
        assertNull(ItemColor.name(many(10, blue), plain, frameIsDark = true))
        assertNull(ItemColor.name(IntArray(0), plain, frameIsDark = false))
    }
}

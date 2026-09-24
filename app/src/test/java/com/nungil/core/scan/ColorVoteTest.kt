package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorVoteTest {
    private val neutral = floatArrayOf(1f, 1f, 1f)
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun pixels(vararg groups: Pair<Int, Int>): IntArray = groups.flatMap { (color, n) -> List(n) { color } }.toIntArray()

    private val blue = rgb(30, 60, 200)
    private val gray = rgb(120, 120, 120)

    @Test fun tunedNumbers() {
        assertEquals(24, ColorVote.SAMPLE_GRID)
        assertEquals(0.5f, ColorVote.CENTER_SHARE)
        assertEquals(0.3f, ColorVote.MIN_CHROMATIC_SHARE)
        assertEquals(0.6f, WhiteBalance.MIN_GAIN)
        assertEquals(1.6f, WhiteBalance.MAX_GAIN)
    }

    @Test fun colourfulNameWinsWithThirtyPercent() =
        assertEquals(ColorName.BLUE, ColorVote.vote(pixels(blue to 30, gray to 70), neutral, frameIsDark = false))

    @Test fun belowThirtyPercentTheGrayWins() =
        assertEquals(ColorName.GRAY, ColorVote.vote(pixels(blue to 29, gray to 71), neutral, frameIsDark = false))

    @Test fun darkFrameGivesNull() =
        assertNull(ColorVote.vote(pixels(blue to 100), neutral, frameIsDark = true))

    @Test fun emptyGivesNull() = assertNull(ColorVote.vote(IntArray(0), neutral, frameIsDark = false))

    @Test fun warmLampIsCorrected() {
        // A gray chair under a warm lamp looks orange; the frame is orange-tinted everywhere.
        val warmGray = rgb(170, 130, 90)
        val frame = pixels(warmGray to 100)
        val gains = WhiteBalance.gains(frame)
        assertEquals(ColorName.ORANGE, ColorVote.vote(frame, neutral, frameIsDark = false))
        assertEquals(ColorName.GRAY, ColorVote.vote(frame, gains, frameIsDark = false))
    }

    @Test fun gainsAreClamped() {
        val gains = WhiteBalance.gains(pixels(rgb(250, 10, 10) to 10))
        assertEquals(0.6f, gains[0], 0.001f)
        assertEquals(1.6f, gains[1], 0.001f)
    }

    @Test fun blackFrameHasNeutralGainsAndIsDark() {
        val frame = pixels(rgb(0, 0, 0) to 10)
        assertEquals(1f, WhiteBalance.gains(frame)[0], 0.001f)
        assertTrue(ColorVote.isDark(frame))
    }

    @Test fun darknessThreshold() {
        assertEquals(0.25f, ColorVote.DARK_FRAME_V)
        assertTrue(ColorVote.isDark(pixels(rgb(60, 60, 60) to 10)))
        assertFalse(ColorVote.isDark(pixels(rgb(70, 70, 70) to 10)))
    }

    @Test fun sampleRegionIsTheCentreHalf() =
        assertEquals(Box(0.25f, 0.25f, 0.75f, 0.75f), ColorVote.sampleRegion(Box(0f, 0f, 1f, 1f)))

    @Test fun gridPointsCoverTheRegion() {
        val pts = ColorVote.gridPoints(Box(0f, 0f, 1f, 1f), 240, 480)
        assertEquals(24 * 24 * 2, pts.size)
        assertEquals(5, pts[0])
        assertEquals(10, pts[1])
        assertTrue(pts[pts.size - 2] in 234..235)
        assertTrue(pts[pts.size - 1] in 469..470)
    }
}

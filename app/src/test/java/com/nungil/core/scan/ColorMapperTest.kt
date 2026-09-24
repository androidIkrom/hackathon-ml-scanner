package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMapperTest {
    private fun name(h: Float, s: Float, v: Float) = ColorMapper.nameFromHsv(h, s, v, frameIsDark = false)

    @Test fun darkFrameGivesNoColourAtAll() = assertNull(ColorMapper.nameFromHsv(220f, 0.9f, 0.9f, frameIsDark = true))

    @Test fun lowValueIsBlack() {
        assertEquals(ColorName.BLACK, name(220f, 0.9f, 0.19f))
        assertEquals(ColorName.BLUE, name(220f, 0.9f, 0.2f))
    }

    @Test fun lowSaturationIsWhiteOrGray() {
        assertEquals(ColorName.WHITE, name(0f, 0.14f, 0.81f))
        assertEquals(ColorName.GRAY, name(0f, 0.14f, 0.8f))
        assertEquals(ColorName.RED, name(0f, 0.15f, 0.5f))
    }

    @Test fun hueBoundaries() {
        assertEquals(ColorName.RED, name(14.9f, 0.9f, 0.9f))
        assertEquals(ColorName.RED, name(345f, 0.9f, 0.9f))
        assertEquals(ColorName.ORANGE, name(15f, 0.9f, 0.9f))
        assertEquals(ColorName.YELLOW, name(45f, 0.9f, 0.9f))
        assertEquals(ColorName.GREEN, name(70f, 0.9f, 0.9f))
        assertEquals(ColorName.BLUE, name(170f, 0.9f, 0.9f))
        assertEquals(ColorName.PURPLE, name(260f, 0.9f, 0.9f))
        assertEquals(ColorName.PINK, name(290f, 0.9f, 0.9f))
        assertEquals(ColorName.PINK, name(344.9f, 0.9f, 0.9f))
    }

    @Test fun darkOrangeIsBrown() {
        assertEquals(ColorName.BROWN, name(30f, 0.8f, 0.59f))
        assertEquals(ColorName.ORANGE, name(30f, 0.8f, 0.6f))
    }

    @Test fun lightRedIsPink() {
        assertEquals(ColorName.PINK, name(5f, 0.49f, 0.71f))
        assertEquals(ColorName.RED, name(5f, 0.5f, 0.71f))
        assertEquals(ColorName.RED, name(5f, 0.49f, 0.7f))
    }

    @Test fun hsvConversion() {
        val red = ColorMapper.hsv(255, 0, 0)
        assertEquals(0f, red[0], 0.01f)
        assertEquals(1f, red[1], 0.01f)
        assertEquals(1f, red[2], 0.01f)
        assertEquals(240f, ColorMapper.hsv(0, 0, 255)[0], 0.01f)
        assertEquals(120f, ColorMapper.hsv(0, 255, 0)[0], 0.01f)
        assertEquals(330f, ColorMapper.hsv(255, 0, 128)[0], 0.5f)
        assertEquals(0f, ColorMapper.hsv(128, 128, 128)[1], 0.01f)
    }

    @Test fun elevenNamesInBothLanguages() {
        assertEquals(11, ColorName.entries.size)
        assertEquals("파란", ColorName.BLUE.koAdjective)
        assertEquals("파란색", ColorName.BLUE.koNoun)
        assertEquals("주황색", ColorName.ORANGE.koAdjective)
        assertEquals("gray", ColorName.GRAY.en)
        assertTrue(ColorName.BROWN.isChromatic)
        assertFalse(ColorName.GRAY.isChromatic)
    }

    @Test fun peopleNeverGetAColour() {
        assertFalse(ColorPolicy.hasColor("person"))
        assertTrue(ColorPolicy.hasColor("chair"))
    }
}

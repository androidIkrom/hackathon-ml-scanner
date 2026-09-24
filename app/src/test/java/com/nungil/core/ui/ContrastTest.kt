package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ContrastTest {
    @Test fun parsesRgbAndArgb() {
        assertEquals(0x1B64DA, Contrast.parseHex("#1B64DA"))
        assertEquals(0x1B64DA, Contrast.parseHex("#FF1B64DA"))
        assertEquals(0xFFFFFF, Contrast.parseHex("#fff"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTranslucentColours() {
        Contrast.parseHex("#801B64DA")
    }

    @Test fun blackOnWhiteIs21() = assertEquals(21.0, Contrast.ratio(0x000000, 0xFFFFFF), 0.01)

    @Test fun ratioIsSymmetric() =
        assertEquals(Contrast.ratio(0x1B64DA, 0xFFFFFF), Contrast.ratio(0xFFFFFF, 0x1B64DA), 1e-9)

    @Test fun sameColourIsOne() = assertEquals(1.0, Contrast.ratio(0x777777, 0x777777), 1e-9)

    @Test fun knownPair() {
        // WCAG reference: #767676 on white is the lightest grey that passes 4.5:1.
        assertEquals(4.54, Contrast.ratio(0x767676, 0xFFFFFF), 0.01)
    }
}

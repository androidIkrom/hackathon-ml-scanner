package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraMathTest {
    private val eps = 0.001f

    @Test fun rotationBy90SwapsWidthAndHeight() {
        assertEquals(480 to 640, FrameMath.uprightSize(640, 480, 90))
        assertEquals(480 to 640, FrameMath.uprightSize(640, 480, 270))
        assertEquals(640 to 480, FrameMath.uprightSize(640, 480, 180))
        assertEquals(640 to 480, FrameMath.uprightSize(640, 480, 0))
    }

    @Test fun pixelBoxIsNormalisedAndClamped() =
        assertEquals(Box(0.25f, 0.5f, 1f, 0f), FrameMath.normalizeBox(120f, 320f, 500f, -10f, 480, 640))

    @Test fun fovUsesSensorHeightForSidewaysSensors() {
        // 4.8 x 3.6 mm sensor, 3.0 mm lens, mounted at 90°: 2*atan(3.6/6) = 61.93°
        assertEquals(61.93f, FovMath.horizontalFovDeg(4.8f, 3.6f, 3.0f, 90), 0.01f)
        // mounted at 0°: 2*atan(4.8/6) = 77.32°
        assertEquals(77.32f, FovMath.horizontalFovDeg(4.8f, 3.6f, 3.0f, 0), 0.01f)
    }

    @Test fun fovFallsBackTo65() {
        assertEquals(65f, FovMath.FALLBACK_DEG)
        assertEquals(65f, FovMath.horizontalFovDeg(0f, 0f, 3f, 90), eps)
        assertEquals(65f, FovMath.horizontalFovDeg(4.8f, 3.6f, 0f, 90), eps)
        assertEquals(65f, FovMath.horizontalFovDeg(Float.NaN, Float.NaN, 3f, 90), eps)
    }

    @Test fun headingFilterStartsAtFirstReading() =
        assertEquals(100f, HeadingFilter().update(100f), eps)

    @Test fun headingFilterMovesAFifthOfTheWay() {
        val f = HeadingFilter()
        f.update(0f)
        assertEquals(0.2f, HeadingFilter.ALPHA)
        assertEquals(2f, f.update(10f), eps)
    }

    @Test fun headingFilterTurnsTheShortWayAcrossNorth() {
        val f = HeadingFilter()
        f.update(350f)
        assertEquals(354f, f.update(10f), eps)
    }
}

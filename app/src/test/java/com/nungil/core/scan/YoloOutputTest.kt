package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class YoloOutputTest {
    @Test fun aPortraitFrameFitsTheSquareInputWithBarsAtTheSides() {
        // Live's 480 x 640 frame in a 640 x 640 input: no scaling, 80 px bars left and right.
        val fit = YoloOutput.fit(480, 640, 640, 640)
        assertEquals(1f, fit.scale, 1e-6f)
        assertEquals(80f, fit.padX, 1e-6f)
        assertEquals(0f, fit.padY, 1e-6f)
    }

    @Test fun aBiggerFrameIsScaledDown() {
        val fit = YoloOutput.fit(1440, 1920, 640, 640)
        assertEquals(1f / 3, fit.scale, 1e-6f)
        assertEquals(80f, fit.padX, 1e-6f)
    }

    @Test fun rowsBecomeDetectionsOnTheFrame() {
        // One row per thing: x1, y1, x2, y2 (input pixels), score, class. Class 39 is a bottle, 63 a laptop.
        val rows = floatArrayOf(
            80f, 0f, 320f, 320f, 0.9f, 39f,
            400f, 320f, 560f, 640f, 0.6f, 63f,
            100f, 100f, 200f, 200f, 0.1f, 0f,
        )
        val fit = YoloOutput.fit(480, 640, 640, 640)
        val found = YoloOutput.detections(rows, rowSize = 6, fit = fit, inputW = 640, inputH = 640, frameW = 480, frameH = 640, minScore = 0.3f)
        assertEquals(listOf("bottle", "laptop"), found.map { it.label })
        val bottle = found[0].box
        assertEquals(0f, bottle.left, 1e-4f)
        assertEquals(0f, bottle.top, 1e-4f)
        assertEquals(0.5f, bottle.right, 1e-4f)
        assertEquals(0.5f, bottle.bottom, 1e-4f)
        assertEquals(1f, found[1].box.bottom, 1e-4f)
    }

    @Test fun coordinatesGivenAsSharesOfTheInputAreTakenToo() {
        val rows = floatArrayOf(0.125f, 0f, 0.5f, 0.5f, 0.9f, 39f)
        val fit = YoloOutput.fit(480, 640, 640, 640)
        val found = YoloOutput.detections(rows, 6, fit, 640, 640, 480, 640, 0.3f, normalized = true)
        assertEquals(0.5f, found.single().box.right, 1e-4f)
    }

    @Test fun boxesStayInsideTheFrame() {
        val rows = floatArrayOf(40f, -10f, 700f, 660f, 0.8f, 0f)
        val found = YoloOutput.detections(rows, 6, YoloOutput.fit(480, 640, 640, 640), 640, 640, 480, 640, 0.3f)
        val b = found.single().box
        assertEquals(0f, b.left, 1e-4f)
        assertEquals(0f, b.top, 1e-4f)
        assertEquals(1f, b.right, 1e-4f)
        assertEquals(1f, b.bottom, 1e-4f)
        assertEquals("person", found.single().label)
    }

    @Test fun eightyCocoNames() {
        assertEquals(80, YoloOutput.COCO.size)
        assertEquals("cell phone", YoloOutput.COCO[67])
        assertEquals("dining table", YoloOutput.COCO[60])
    }
}

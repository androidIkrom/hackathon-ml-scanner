package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Facing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxGeometryTest {
    private val eps = 0.001f

    @Test fun centreOfTheImageIsTheHeading() =
        assertEquals(0f, BoxGeometry.objectAngle(0f, 0.5f, 60f, Facing.BACK), eps)

    @Test fun rightEdgeIsHalfTheFieldOfViewClockwise() =
        assertEquals(30f, BoxGeometry.objectAngle(0f, 1f, 60f, Facing.BACK), eps)

    @Test fun leftEdgeWrapsBelowZero() =
        assertEquals(330f, BoxGeometry.objectAngle(0f, 0f, 60f, Facing.BACK), eps)

    @Test fun frontCameraLooksBehind() =
        assertEquals(180f, BoxGeometry.objectAngle(0f, 0.5f, 60f, Facing.FRONT), eps)

    @Test fun headingAndOffsetAdd() =
        assertEquals(5f, BoxGeometry.objectAngle(350f, 0.75f, 60f, Facing.BACK), eps)

    @Test fun edgeMarginIsTwoPercent() = assertEquals(0.02f, BoxGeometry.EDGE_MARGIN)

    @Test fun boxTouchingOnlyTheLeftEdgeIsHalfSeen() =
        assertTrue(BoxGeometry.touchesOneSideEdge(Box(0.01f, 0.2f, 0.5f, 0.8f)))

    @Test fun boxTouchingOnlyTheRightEdgeIsHalfSeen() =
        assertTrue(BoxGeometry.touchesOneSideEdge(Box(0.5f, 0.2f, 0.985f, 0.8f)))

    @Test fun boxSpanningTheWholeWidthIsKept() =
        assertFalse(BoxGeometry.touchesOneSideEdge(Box(0.01f, 0.2f, 0.99f, 0.8f)))

    @Test fun boxAwayFromEdgesIsKept() =
        assertFalse(BoxGeometry.touchesOneSideEdge(Box(0.1f, 0.2f, 0.9f, 0.8f)))

    @Test fun boxJustInsideTheMarginIsKept() =
        assertFalse(BoxGeometry.touchesOneSideEdge(Box(0.03f, 0.2f, 0.5f, 0.8f)))

    @Test fun horizontalCenterIsClamped() {
        assertEquals(0.5f, BoxGeometry.horizontalCenter(Box(0.4f, 0f, 0.6f, 1f)), eps)
        assertEquals(1f, BoxGeometry.horizontalCenter(Box(0.9f, 0f, 1.3f, 1f)), eps)
    }

    @Test fun iouOfSameBoxIsOne() {
        val b = Box(0.1f, 0.1f, 0.5f, 0.5f)
        assertEquals(1f, BoxGeometry.iou(b, b), eps)
    }

    @Test fun iouOfDisjointBoxesIsZero() =
        assertEquals(0f, BoxGeometry.iou(Box(0f, 0f, 0.2f, 0.2f), Box(0.5f, 0.5f, 0.7f, 0.7f)), eps)

    @Test fun iouOfHalfOverlap() =
        assertEquals(1f / 3f, BoxGeometry.iou(Box(0f, 0f, 0.2f, 0.2f), Box(0.1f, 0f, 0.3f, 0.2f)), eps)
}

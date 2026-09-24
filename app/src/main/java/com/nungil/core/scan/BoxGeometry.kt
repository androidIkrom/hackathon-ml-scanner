package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Facing
import kotlin.math.max
import kotlin.math.min

/** Turns a detector box into a direction; the whole sensor fusion lives in [objectAngle]. */
object BoxGeometry {
    /** A box this close to a side edge counts as touching it. */
    const val EDGE_MARGIN = 0.02f

    /** Box centre across the upright image, 0 = left edge, 1 = right edge. */
    fun horizontalCenter(box: Box): Float = box.centerX.coerceIn(0f, 1f)

    /**
     * Compass angle of an object: relHeading + (centerX - 0.5) * hfov.
     * The front camera looks the other way, so its base direction is turned by 180°.
     */
    fun objectAngle(relHeading: Float, centerX: Float, hfovDeg: Float, facing: Facing): Float {
        val base = if (facing == Facing.FRONT) relHeading + 180f else relHeading
        return AngleMath.normalize(base + (centerX - 0.5f) * hfovDeg)
    }

    /**
     * True when the box touches exactly one side edge: a half-seen object that will be seen whole
     * in another frame. Dropping these removed most double counts. A box spanning the full width is kept.
     */
    fun touchesOneSideEdge(box: Box, margin: Float = EDGE_MARGIN): Boolean {
        val left = box.left <= margin
        val right = box.right >= 1f - margin
        return left != right
    }

    /** Intersection over union of two boxes, 0..1. */
    fun iou(a: Box, b: Box): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        val inter = w * h
        val union = a.area + b.area - inter
        return if (union <= 0f) 0f else inter / union
    }
}

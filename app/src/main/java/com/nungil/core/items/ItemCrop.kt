package com.nungil.core.items

import com.nungil.contract.Box
import com.nungil.contract.Detection
import kotlin.math.roundToInt

/** Which detector boxes to embed, and their pixel rects. */
object ItemCrop {
    const val MIN_CROP_PX = 16
    const val MAX_CROPS_PER_FRAME = 3
    private const val PERSON = "person"

    /** Pixel rect [left, top, right, bottom] of [box] in a [width] x [height] image; null under 16 x 16 px. */
    fun rect(box: Box, width: Int, height: Int): IntArray? {
        val l = (box.left * width).roundToInt().coerceIn(0, width)
        val t = (box.top * height).roundToInt().coerceIn(0, height)
        val r = (box.right * width).roundToInt().coerceIn(0, width)
        val b = (box.bottom * height).roundToInt().coerceIn(0, height)
        if (r - l < MIN_CROP_PX || b - t < MIN_CROP_PX) return null
        return intArrayOf(l, t, r, b)
    }

    /**
     * Up to [MAX_CROPS_PER_FRAME] indices of non-person boxes big enough to crop: boxes labelled [preferLabel]
     * first, then the largest.
     */
    fun candidates(detections: List<Detection>, width: Int, height: Int, preferLabel: String? = null): List<Int> =
        detections.indices
            .filter { detections[it].label != PERSON && rect(detections[it].box, width, height) != null }
            .sortedWith(
                compareByDescending<Int> { detections[it].label == preferLabel }
                    .thenByDescending { detections[it].box.area },
            )
            .take(MAX_CROPS_PER_FRAME)
}

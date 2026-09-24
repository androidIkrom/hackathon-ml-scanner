package com.nungil.core.scan

import com.nungil.contract.Box
import kotlin.math.max

/** Where a normalised box lands on screen when the picture is shown like PreviewView's FILL_CENTER. */
object OverlayMath {
    data class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /**
     * FILL_CENTER scales the [imageWidth] x [imageHeight] picture by max(viewW / imageW, viewH / imageH), so it
     * covers the view, and centres it; the overflow is cropped equally on both sides. [mirrored] flips the box
     * left-right, because the front camera's preview is mirrored while the analysed frame is not.
     */
    fun toView(box: Box, imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int, mirrored: Boolean): ViewRect {
        val scale = max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        val shownW = imageWidth * scale
        val shownH = imageHeight * scale
        val dx = (viewWidth - shownW) / 2f
        val dy = (viewHeight - shownH) / 2f
        val left = if (mirrored) 1f - box.right else box.left
        val right = if (mirrored) 1f - box.left else box.right
        return ViewRect(dx + left * shownW, dy + box.top * shownH, dx + right * shownW, dy + box.bottom * shownH)
    }
}

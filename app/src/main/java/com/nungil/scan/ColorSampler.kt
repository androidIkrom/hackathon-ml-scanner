package com.nungil.scan

import android.graphics.Bitmap
import com.nungil.contract.Box
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.ColorVote
import com.nungil.core.scan.WhiteBalance

/** Reads the pixels ColorVote needs from an upright frame bitmap. Call on a worker thread. */
object ColorSampler {
    /** Light of the whole frame: white-balance gains and whether it is too dark for colours. */
    class FrameLight(val gains: FloatArray, val isDark: Boolean)

    fun frameLight(bitmap: Bitmap): FrameLight {
        val pixels = read(bitmap, Box(0f, 0f, 1f, 1f), ColorVote.FRAME_GRID)
        return FrameLight(WhiteBalance.gains(pixels), ColorVote.isDark(pixels))
    }

    /** Colour of the object in [box], or null when the frame is dark or nothing is named. */
    fun colorOf(bitmap: Bitmap, box: Box, light: FrameLight): ColorName? {
        if (light.isDark) return null
        val pixels = read(bitmap, ColorVote.sampleRegion(box), ColorVote.SAMPLE_GRID)
        return ColorVote.vote(pixels, light.gains, light.isDark)
    }

    private fun read(bitmap: Bitmap, region: Box, grid: Int): IntArray {
        val points = ColorVote.gridPoints(region, bitmap.width, bitmap.height, grid)
        return IntArray(points.size / 2) { i -> bitmap.getPixel(points[2 * i], points[2 * i + 1]) }
    }
}

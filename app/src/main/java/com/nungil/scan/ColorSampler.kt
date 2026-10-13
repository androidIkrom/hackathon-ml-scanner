package com.nungil.scan

import android.graphics.Bitmap
import com.nungil.contract.Box
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.ColorVote
import com.nungil.core.scan.WhiteBalance
import java.util.Locale

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

    /**
     * For the log: the votes in [box] before and after white balance, and the frame's gains (Task 2 of the
     * live-quality plan: why a white bottle was "blue").
     */
    fun explain(bitmap: Bitmap, box: Box, light: FrameLight): String {
        val pixels = read(bitmap, ColorVote.sampleRegion(box), ColorVote.SAMPLE_GRID)
        val g = light.gains
        return "raw " + ColorVote.shares(ColorVote.counts(pixels, NEUTRAL)) +
            "; balanced " + ColorVote.shares(ColorVote.counts(pixels, g)) +
            String.format(Locale.US, "; gains %.2f %.2f %.2f", g[0], g[1], g[2])
    }

    private val NEUTRAL = floatArrayOf(1f, 1f, 1f)

    private fun read(bitmap: Bitmap, region: Box, grid: Int): IntArray {
        val points = ColorVote.gridPoints(region, bitmap.width, bitmap.height, grid)
        return IntArray(points.size / 2) { i -> bitmap.getPixel(points[2 * i], points[2 * i + 1]) }
    }
}

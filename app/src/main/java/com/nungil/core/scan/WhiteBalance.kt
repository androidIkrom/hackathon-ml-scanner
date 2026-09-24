package com.nungil.core.scan

/**
 * Gray-world white balance: scale R, G and B so their frame averages become equal. Warm lamps and shadows
 * otherwise turn everything brown or gray. Gains come from the WHOLE frame; computed on one object they
 * would erase that object's own colour.
 */
object WhiteBalance {
    const val MIN_GAIN = 0.6f
    const val MAX_GAIN = 1.6f

    /** Gains for R, G and B from ARGB [pixels]. An empty or black frame gives neutral gains. */
    fun gains(pixels: IntArray): FloatArray {
        if (pixels.isEmpty()) return floatArrayOf(1f, 1f, 1f)
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in pixels) {
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        val n = pixels.size.toFloat()
        val means = floatArrayOf(r / n, g / n, b / n)
        val gray = (means[0] + means[1] + means[2]) / 3f
        return FloatArray(3) { i ->
            if (means[i] <= 0f) 1f else (gray / means[i]).coerceIn(MIN_GAIN, MAX_GAIN)
        }
    }

    /** One ARGB pixel with the gains applied, each channel clamped to 0..255. */
    fun apply(pixel: Int, gains: FloatArray): Int {
        val r = (((pixel shr 16) and 0xFF) * gains[0]).toInt().coerceIn(0, 255)
        val g = (((pixel shr 8) and 0xFF) * gains[1]).toInt().coerceIn(0, 255)
        val b = ((pixel and 0xFF) * gains[2]).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

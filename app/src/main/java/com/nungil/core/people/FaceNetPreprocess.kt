package com.nungil.core.people

import kotlin.math.max
import kotlin.math.sqrt

/** Input preparation for FaceNet-512: 160×160 RGB floats with per-image standardisation. */
object FaceNetPreprocess {
    const val SIZE = 160

    /**
     * [argb] packed pixels (row-major, as Bitmap.getPixels returns them) to R, G, B floats per pixel,
     * standardised as (x - mean) / max(stddev, 1 / sqrt(n)) over all n values, like TensorFlow's
     * per_image_standardization.
     */
    fun standardize(argb: IntArray): FloatArray {
        val out = FloatArray(argb.size * 3)
        for (i in argb.indices) {
            val p = argb[i]
            out[3 * i] = ((p shr 16) and 0xFF).toFloat()
            out[3 * i + 1] = ((p shr 8) and 0xFF).toFloat()
            out[3 * i + 2] = (p and 0xFF).toFloat()
        }
        if (out.isEmpty()) return out
        val mean = out.average()
        var sq = 0.0
        for (v in out) sq += (v - mean) * (v - mean)
        val std = sqrt(sq / out.size)
        val adjusted = max(std, 1.0 / sqrt(out.size.toDouble()))
        for (i in out.indices) out[i] = ((out[i] - mean) / adjusted).toFloat()
        return out
    }
}

package com.nungil.core.walk

import com.nungil.contract.Box

/** Pure helpers between the captured depth image and the screen-shaped grid. */
object DepthGrid {
    /**
     * Fills [out] (metres, 0 = no reading) from [depthMm] through [lut]: for each grid cell, the index into
     * the depth image or -1. Depth values are unsigned millimetres; 0 means "no reading".
     */
    fun fill(depthMm: ShortArray, lut: IntArray, out: FloatArray) {
        require(out.size == lut.size)
        for (i in lut.indices) {
            val k = lut[i]
            out[i] = if (k < 0 || k >= depthMm.size) 0f else (depthMm[k].toInt() and 0xFFFF) / 1000f
        }
    }

    /** Median depth inside [box] (normalised screen coordinates), or null when nothing was measured there. */
    fun medianIn(gridM: FloatArray, width: Int, height: Int, box: Box): Float? {
        val x0 = (box.left * width).toInt().coerceIn(0, width - 1)
        val x1 = (box.right * width).toInt().coerceIn(x0 + 1, width)
        val y0 = (box.top * height).toInt().coerceIn(0, height - 1)
        val y1 = (box.bottom * height).toInt().coerceIn(y0 + 1, height)
        val values = ArrayList<Float>()
        for (y in y0 until y1) for (x in x0 until x1) {
            val d = gridM[y * width + x]
            if (d >= DepthObstacles.MIN_M && d <= DepthObstacles.MAX_M) values.add(d)
        }
        if (values.isEmpty()) return null
        values.sort()
        return values[values.size / 2]
    }

    /** Left third, middle third or right third of the screen. */
    fun zoneOf(centerX: Float): Zone = when {
        centerX < 1f / 3f -> Zone.LEFT
        centerX > 2f / 3f -> Zone.RIGHT
        else -> Zone.AHEAD
    }
}

/** ARCore SemanticLabel numbers for the ground classes walk mode speaks about. */
object SemanticGround {
    const val ROAD = 4
    const val SIDEWALK = 5
    const val TERRAIN = 6

    fun kind(label: Int): GroundKind? = when (label) {
        ROAD -> GroundKind.ROAD
        SIDEWALK -> GroundKind.SIDEWALK
        TERRAIN -> GroundKind.TERRAIN
        else -> null
    }
}

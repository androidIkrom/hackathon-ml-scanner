package com.nungil.core.items

import com.nungil.contract.Box

/**
 * Which pixels of the frame are the thing being learned: the segmenter's answer for one point of the frame.
 * The item is whatever that point belongs to, so the answer reads the same whether the model gives the thing
 * 1 and the rest 0 or the other way round. [inside] is row by row, [width] x [height]; [cover] is how much of
 * the frame the item takes (0..1).
 */
class ItemMask private constructor(val width: Int, val height: Int, val inside: BooleanArray, val cover: Float) {
    /** The item's box in the frame, 0..1. */
    val box: Box by lazy {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!inside[y * width + x]) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        Box(left.toFloat() / width, top.toFloat() / height, (right + 1f) / width, (bottom + 1f) / height)
    }

    /**
     * Where to ask for the item in the next frame (0..1): the pixel of the item nearest to the middle of all
     * its pixels. The middle of its box is off a long thin thing lying at an angle, and asking there gave the
     * table (the logs).
     */
    val anchorX: Float get() = anchor[0]
    val anchorY: Float get() = anchor[1]

    private val anchor: FloatArray by lazy {
        var n = 0L
        var sumX = 0L
        var sumY = 0L
        for (i in inside.indices) {
            if (!inside[i]) continue
            n++
            sumX += i % width
            sumY += i / width
        }
        val cx = sumX.toDouble() / n
        val cy = sumY.toDouble() / n
        var best = -1
        var bestDistance = Double.MAX_VALUE
        for (i in inside.indices) {
            if (!inside[i]) continue
            val dx = i % width - cx
            val dy = i / width - cy
            val d = dx * dx + dy * dy
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        floatArrayOf((best % width + 0.5f) / width, (best / width + 0.5f) / height)
    }

    /** ARGB pixels, row by row, that show the item on the preview: [fill] on it, [edge] on its rim, 0 elsewhere. */
    fun pixels(fill: Int, edge: Int): IntArray {
        val out = IntArray(inside.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!inside[y * width + x]) continue
                // The rim: something that is not the item within EDGE_PX. The frame's own edge is not a rim.
                val rim = (1..EDGE_PX).any { d ->
                    x - d >= 0 && !inside[y * width + x - d] || x + d < width && !inside[y * width + x + d] ||
                        y - d >= 0 && !inside[(y - d) * width + x] || y + d < height && !inside[(y + d) * width + x]
                }
                out[y * width + x] = if (rim) edge else fill
            }
        }
        return out
    }

    /**
     * Where to read the item's colour: the points of a [grid] x [grid] net over its box that are on the item,
     * as pixel coordinates x0, y0, x1, y1, ...
     */
    fun samplePoints(grid: Int = SAMPLE_GRID): List<Int> {
        val b = box
        val out = ArrayList<Int>(grid * grid * 2)
        for (row in 0 until grid) {
            val y = ((b.top + (row + 0.5f) * b.height / grid) * height).toInt().coerceIn(0, height - 1)
            for (col in 0 until grid) {
                val x = ((b.left + (col + 0.5f) * b.width / grid) * width).toInt().coerceIn(0, width - 1)
                if (inside[y * width + x]) {
                    out += x
                    out += y
                }
            }
        }
        return out
    }

    /**
     * The item alone: [pixels] ([w] x [h], ARGB, row by row) is the part of a [frameWidth] x [frameHeight] frame
     * that starts at ([left], [top]), and every pixel of it that is not on the item becomes [fill], in place.
     * A learned square is 19 to 41% item and the rest whatever it stood on; the same item on another
     * background scored 0.50 against such a square, and 0.81 to 0.95 against itself alone (measured).
     */
    fun alone(pixels: IntArray, left: Int, top: Int, w: Int, h: Int, frameWidth: Int, frameHeight: Int, fill: Int = ALONE_FILL) {
        for (y in 0 until h) {
            val row = ((top + y) * height / frameHeight).coerceIn(0, height - 1) * width
            for (x in 0 until w) {
                val column = ((left + x) * width / frameWidth).coerceIn(0, width - 1)
                if (!inside[row + column]) pixels[y * w + x] = fill
            }
        }
    }

    companion object {
        /**
         * What is painted over everything that is not the item: opaque middle gray. With it the item alone
         * scored 0.70 and more and a view without the item 0.24 at most; black gave 0.61 and 0.33, a blurred
         * background 0.61 and 0.30 (measured).
         */
        const val ALONE_FILL = 0xFF808080.toInt()

        /** Less of the frame than this is a speck, not an item. */
        const val MIN_COVER = 0.01f

        /** More of the frame than this is the table or the whole view, not an item on it. */
        const val MAX_COVER = 0.7f

        const val SAMPLE_GRID = 24
        private const val INSIDE = 0.5f
        private const val EDGE_PX = 2
        private const val MIDDLE_PX = 2

        /**
         * The item in a [width] x [height] answer asked for at ([x], [y]) (0..1). Null when there is no item to
         * show: [confidence] is not [width] x [height], or the thing there covers under [MIN_COVER] or over
         * [MAX_COVER] of the frame.
         */
        fun of(confidence: FloatArray, width: Int, height: Int, x: Float = 0.5f, y: Float = 0.5f): ItemMask? {
            if (width <= 0 || height <= 0 || confidence.size != width * height) return null
            val high = pointIsHigh(confidence, width, height, (x * width).toInt().coerceIn(0, width - 1), (y * height).toInt().coerceIn(0, height - 1))
            var count = 0
            val inside = BooleanArray(confidence.size) { i ->
                val on = (confidence[i] >= INSIDE) == high
                if (on) count++
                on
            }
            val cover = count.toFloat() / confidence.size
            if (cover < MIN_COVER || cover > MAX_COVER) return null
            return ItemMask(width, height, inside, cover)
        }

        /** Whether the few pixels around ([px], [py]), which are the item, have the high values. */
        private fun pointIsHigh(confidence: FloatArray, width: Int, height: Int, px: Int, py: Int): Boolean {
            var sum = 0f
            var n = 0
            for (y in (py - MIDDLE_PX).coerceAtLeast(0)..(py + MIDDLE_PX).coerceAtMost(height - 1)) {
                for (x in (px - MIDDLE_PX).coerceAtLeast(0)..(px + MIDDLE_PX).coerceAtMost(width - 1)) {
                    sum += confidence[y * width + x]
                    n++
                }
            }
            return sum / n >= INSIDE
        }
    }
}

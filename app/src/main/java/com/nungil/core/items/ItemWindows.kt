package com.nungil.core.items

import com.nungil.contract.Box
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The parts of a frame a saved item is looked at in. The detector is not asked: it only knows its 80 kinds of
 * things, and for anything else it gave no box, or the box of the table the item stood on (the logs).
 * Learning looks at a square in the middle, where the user points the phone; finding looks at squares all over
 * the frame and takes the ones that look like the item.
 */
object ItemWindows {
    /** Side of the square that is learned, as a part of the frame's shorter side. */
    const val LEARN_SIDE = 0.7f

    /** Sides of the squares that are searched: the item as near as it was learned, and farther away. */
    val FIND_SIDES = listOf(0.7f, 0.45f)

    /** Room around a thing's outline in the square that is learned, as a part of its longer side. */
    const val SQUARE_PAD = 0.15f

    /** How much a window just at the threshold still counts when placing the item. */
    private const val EDGE_WEIGHT = 0.05f

    // The closer look (around): sizes and shifts as parts of the window.
    private val AROUND_SCALES = listOf(0.75f, 1f)
    private val AROUND_SHIFTS = listOf(0f to 0f, -0.25f to 0f, 0.25f to 0f, 0f to -0.25f, 0f to 0.25f)
    private const val AROUND_BIGGER = 1.3f
    private const val SAME = 1e-4f

    /** The square in the middle of a [width] x [height] image. */
    fun center(width: Int, height: Int): Box {
        val side = (LEARN_SIDE * minOf(width, height)).roundToInt()
        return box((width - side) / 2, (height - side) / 2, side, width, height)
    }

    /** Squares of each of [FIND_SIDES], at most half a square apart, covering the image from edge to edge. */
    fun grid(width: Int, height: Int): List<Box> = FIND_SIDES.flatMap { part ->
        val side = (part * minOf(width, height)).roundToInt()
        starts(height, side).flatMap { y -> starts(width, side).map { x -> box(x, y, side, width, height) } }
    }

    /**
     * Where the item is, or null when no window scores [threshold]: the best window, moved to the middle of all
     * windows at or above the threshold (the better a window, the more it counts).
     */
    fun locate(windows: List<Box>, scores: FloatArray, threshold: Float): Box? {
        var best = -1
        var sum = 0f
        var x = 0f
        var y = 0f
        for (i in windows.indices) {
            val score = scores[i]
            if (score < threshold) continue
            if (best < 0 || score > scores[best]) best = i
            val weight = score - threshold + EDGE_WEIGHT
            sum += weight
            x += weight * windows[i].centerX
            y += weight * windows[i].centerY
        }
        if (best < 0) return null
        val halfWidth = windows[best].width / 2f
        val halfHeight = windows[best].height / 2f
        val centerX = (x / sum).coerceIn(halfWidth, 1f - halfWidth)
        val centerY = (y / sum).coerceIn(halfHeight, 1f - halfHeight)
        return Box(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
    }

    /**
     * A closer look around the grid's best [window]: three quarters of its size and its own size, each in place
     * and a quarter of the window to each side, and 1.3 times its size in place. An item farther away than it was
     * learned fills only part of a grid window (0.5 to 0.7 against its samples from twice the distance; a smaller
     * window on it 0.7 to 0.85). Windows are moved back inside the frame, and [window] itself is left out.
     */
    fun around(window: Box): List<Box> {
        val out = mutableListOf<Box>()
        for (scale in AROUND_SCALES) {
            for ((dx, dy) in AROUND_SHIFTS) {
                val width = window.width * scale
                val height = window.height * scale
                val left = (window.centerX + dx * window.width - width / 2f).coerceIn(0f, 1f - width)
                val top = (window.centerY + dy * window.height - height / 2f).coerceIn(0f, 1f - height)
                val box = Box(left, top, left + width, top + height)
                if (!same(box, window) && out.none { same(it, box) }) out += box
            }
        }
        val width = window.width * AROUND_BIGGER
        val height = window.height * AROUND_BIGGER
        if (width <= 1f && height <= 1f) {
            val left = (window.centerX - width / 2f).coerceIn(0f, 1f - width)
            val top = (window.centerY - height / 2f).coerceIn(0f, 1f - height)
            out += Box(left, top, left + width, top + height)
        }
        return out
    }

    private fun same(a: Box, b: Box): Boolean =
        abs(a.left - b.left) < SAME && abs(a.top - b.top) < SAME && abs(a.right - b.right) < SAME && abs(a.bottom - b.bottom) < SAME

    /**
     * The square that is learned around a thing whose outline fills [box]: its longer side plus [SQUARE_PAD],
     * centred on it, moved back inside the frame and never wider than the frame's shorter side.
     */
    fun square(box: Box, width: Int, height: Int): Box {
        val sidePx = (maxOf(box.width * width, box.height * height) * (1f + SQUARE_PAD)).coerceAtMost(minOf(width, height).toFloat())
        val w = sidePx / width
        val h = sidePx / height
        val left = (box.centerX - w / 2f).coerceIn(0f, 1f - w)
        val top = (box.centerY - h / 2f).coerceIn(0f, 1f - h)
        return Box(left, top, left + w, top + h)
    }

    /** Where squares of [side] px start along [length] px: evenly spread, the first and last at the edges. */
    private fun starts(length: Int, side: Int): List<Int> {
        val room = length - side
        if (room <= 0) return listOf(0)
        val steps = ceil(room / (side / 2f)).toInt()
        return (0..steps).map { (it * room.toFloat() / steps).roundToInt() }
    }

    private fun box(x: Int, y: Int, side: Int, width: Int, height: Int): Box =
        Box(x.toFloat() / width, y.toFloat() / height, (x + side).toFloat() / width, (y + side).toFloat() / height)
}

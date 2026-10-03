package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import com.nungil.contract.Box
import com.nungil.core.items.ItemColor
import com.nungil.core.items.ItemEnrollmentGuide
import com.nungil.core.items.ItemLook
import com.nungil.core.items.ItemLooks
import com.nungil.core.items.ItemMask
import com.nungil.core.items.ItemWindows
import com.nungil.scan.ColorSampler
import java.io.Closeable

/**
 * Everything one frame tells about the thing at one point of it: its outline (ItemSegmenter), what it looks
 * like (ItemLook) and its embedding (ItemEmbedder) of the square around it. One worker thread only.
 * Throws from the constructor when a model is missing.
 */
class ItemSight(context: Context) : Closeable {
    /**
     * The thing at the point asked for.
     * @param square the part of the frame that is learned: ItemWindows.square around the outline.
     * @param colours the colours seen on it, for the log.
     */
    class Sighting(
        val mask: ItemMask,
        val look: ItemLook,
        val view: ItemEnrollmentGuide.View,
        val square: Box,
        val colours: String,
    )

    private val segmenter = ItemSegmenter(context)
    private val embedder = ItemEmbedder(context)

    /**
     * The thing at ([x], [y]) (0..1) of [frame], or null when there is nothing usable there (nothing, the whole
     * view, or a failure). [focusDistanceM] is where the lens is focused, for the size and distance.
     */
    fun see(frame: Bitmap, x: Float, y: Float, hfovDeg: Float, focusDistanceM: Float?): Sighting? {
        val answer = segmenter.at(frame, x, y) ?: return null
        val mask = ItemMask.of(answer.values, answer.width, answer.height, x, y) ?: return null
        val square = ItemWindows.square(mask.box, frame.width, frame.height)
        val vector = embedder.embed(frame, square) ?: return null

        val light = ColorSampler.frameLight(frame)
        val points = mask.samplePoints()
        val pixels = IntArray(points.size / 2) { i ->
            val px = (points[2 * i] * frame.width / mask.width).coerceIn(0, frame.width - 1)
            val py = (points[2 * i + 1] * frame.height / mask.height).coerceIn(0, frame.height - 1)
            frame.getPixel(px, py)
        }
        val color = ItemColor.name(pixels, light.gains, light.isDark)
        val outline = ItemLooks.outline(mask.inside, mask.width, mask.height)
        val look = ItemLooks.look(outline, color, focusDistanceM, hfovDeg, mask.width)
        val view = ItemEnrollmentGuide.View(vector, mask.box.centerX, mask.box.centerY, mask.cover)
        return Sighting(mask, look, view, square, ItemColor.shares(pixels, light.gains))
    }

    override fun close() {
        segmenter.close()
        embedder.close()
    }
}

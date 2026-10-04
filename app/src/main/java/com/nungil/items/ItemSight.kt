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
 * like (ItemLook) and its embeddings (ItemEmbedder): of the square around it, and of the thing alone. One
 * worker thread only. Throws from the constructor when a model is missing.
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

    /** The last look found nothing because the thing there fills the view (over ItemMask.MAX_COVER of it). */
    var filled = false
        private set

    private val segmenter = ItemSegmenter(context)
    private val embedder = ItemEmbedder(context)

    /**
     * The thing at ([x], [y]) (0..1) of [frame], or null when there is nothing usable there (nothing, the whole
     * view, or a failure). [focusDistanceM] is where the lens is focused, for the size and distance.
     */
    fun see(frame: Bitmap, x: Float, y: Float, hfovDeg: Float, focusDistanceM: Float?): Sighting? {
        filled = false
        val answer = segmenter.at(frame, x, y) ?: return null
        val mask = ItemMask.of(answer.values, answer.width, answer.height, x, y)
        if (mask == null) {
            filled = ItemMask.coverAt(answer.values, answer.width, answer.height, x, y) > ItemMask.MAX_COVER
            return null
        }
        val square = ItemWindows.square(mask.box, frame.width, frame.height)
        val vector = embedder.embed(frame, square) ?: return null

        val light = ColorSampler.frameLight(frame)
        val points = mask.samplePoints()
        val pixels = IntArray(points.size / 2) { i ->
            val px = (points[2 * i] * frame.width / mask.width).coerceIn(0, frame.width - 1)
            val py = (points[2 * i + 1] * frame.height / mask.height).coerceIn(0, frame.height - 1)
            frame.getPixel(px, py)
        }
        val whiteLevel = ItemColor.whiteLevel(framePixels(frame), light.gains)
        val color = ItemColor.name(pixels, light.gains, light.isDark, whiteLevel)
        val outline = ItemLooks.outline(mask.inside, mask.width, mask.height)
        val look = ItemLooks.look(outline, color, focusDistanceM, hfovDeg, mask.width)
        val alone = embedder.embedAlone(frame, square, mask)
        val view = ItemEnrollmentGuide.View(vector, mask.box.centerX, mask.box.centerY, mask.cover, alone)
        val colours = ItemColor.shares(pixels, light.gains, whiteLevel) + ", white level %.2f".format(whiteLevel)
        return Sighting(mask, look, view, square, colours)
    }

    /** [FRAME_GRID] x [FRAME_GRID] pixels spread over the whole frame, for its white level. */
    private fun framePixels(frame: Bitmap): IntArray = IntArray(FRAME_GRID * FRAME_GRID) { i ->
        val x = ((i % FRAME_GRID + 0.5f) * frame.width / FRAME_GRID).toInt().coerceIn(0, frame.width - 1)
        val y = ((i / FRAME_GRID + 0.5f) * frame.height / FRAME_GRID).toInt().coerceIn(0, frame.height - 1)
        frame.getPixel(x, y)
    }

    private companion object {
        const val FRAME_GRID = 20
    }

    override fun close() {
        segmenter.close()
        embedder.close()
    }
}

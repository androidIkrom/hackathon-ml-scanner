package com.nungil.core.items

import com.nungil.core.scan.ColorName
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** The rough shape of a thing, as its outline in the picture shows it. */
enum class ItemShape { ROUND, SQUARE, OBLONG, RECTANGULAR, LONG }

/**
 * What the thing being learned looks like, said aloud so that someone who cannot see the screen knows which
 * thing it is. A part that is not known is null and is not said. Round and square things have one size
 * ([lengthCm]); the others a length and a width.
 */
data class ItemLook(
    val color: ColorName?,
    val shape: ItemShape?,
    val lengthCm: Int?,
    val widthCm: Int?,
    val distanceCm: Int?,
)

/** Measures an [ItemMask] for an [ItemLook]. All of it is rough: one camera, one picture. */
object ItemLooks {
    /** A thing at least this many times as long as it is wide is long. */
    const val LONG_RATIO = 2f

    /** Under this, length and width are called the same. */
    const val EVEN_RATIO = 1.35f

    /** A thing that fills at least this much of the box around it has corners; a disc fills 0.79. */
    const val CORNERED_FILL = 0.9f

    /** A thing that fills less than this is neither round nor square: a triangle, a square on its corner. */
    const val MIN_FILL = 0.68f

    /** The lens's own distance is believed between these; nearer or farther it says little. */
    const val MIN_DISTANCE_M = 0.1f
    const val MAX_DISTANCE_M = 2f

    /** The thing's extent along and across its own direction, in pixels, and how much of that box it fills. */
    class Outline(val lengthPx: Float, val widthPx: Float, val fill: Float)

    /** Measures the pixels that are [inside] a [width] x [height] mask; null when there are none. */
    fun outline(inside: BooleanArray, width: Int, height: Int): Outline? {
        var n = 0
        var sumX = 0.0
        var sumY = 0.0
        for (i in inside.indices) {
            if (!inside[i]) continue
            n++
            sumX += i % width
            sumY += i / width
        }
        if (n == 0) return null
        val cx = sumX / n
        val cy = sumY / n
        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (i in inside.indices) {
            if (!inside[i]) continue
            val dx = i % width - cx
            val dy = i / width - cy
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        // The direction the thing lies in: the main axis of its pixels.
        val angle = 0.5 * atan2(2 * sxy, sxx - syy)
        val c = cos(angle)
        val s = sin(angle)
        var minU = Double.MAX_VALUE
        var maxU = -Double.MAX_VALUE
        var minV = Double.MAX_VALUE
        var maxV = -Double.MAX_VALUE
        for (i in inside.indices) {
            if (!inside[i]) continue
            val dx = i % width - cx
            val dy = i / width - cy
            val u = dx * c + dy * s
            val v = -dx * s + dy * c
            if (u < minU) minU = u
            if (u > maxU) maxU = u
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }
        val along = (maxU - minU + 1).toFloat()
        val across = (maxV - minV + 1).toFloat()
        return Outline(maxOf(along, across), minOf(along, across), n / (along * across))
    }

    /** The shape's name, or null when it has none worth saying. */
    fun shape(outline: Outline): ItemShape? {
        val ratio = outline.lengthPx / outline.widthPx
        return when {
            ratio >= LONG_RATIO -> ItemShape.LONG
            outline.fill >= CORNERED_FILL -> if (ratio < EVEN_RATIO) ItemShape.SQUARE else ItemShape.RECTANGULAR
            ratio >= EVEN_RATIO -> ItemShape.OBLONG
            outline.fill >= MIN_FILL -> ItemShape.ROUND
            else -> null
        }
    }

    /** How long [px] pixels are, in centimetres, on a thing [distanceM] away, in an image [imageWidth] px and [hfovDeg] wide. */
    fun cm(px: Float, distanceM: Float, hfovDeg: Float, imageWidth: Int): Float {
        val frameWidthCm = 2f * distanceM * tan(Math.toRadians(hfovDeg / 2.0)).toFloat() * 100f
        return px * frameWidthCm / imageWidth
    }

    /** A size to say: whole centimetres under 10, fives up to 50, tens above. */
    fun roundCm(cm: Float): Int = when {
        cm < 10f -> cm.roundToInt().coerceAtLeast(1)
        cm <= 50f -> (cm / 5f).roundToInt() * 5
        else -> (cm / 10f).roundToInt() * 10
    }

    /** A distance to say, in centimetres: tens under a metre, half metres above. Null when the lens cannot tell. */
    fun distanceCm(distanceM: Float?): Int? {
        if (distanceM == null || distanceM < MIN_DISTANCE_M || distanceM > MAX_DISTANCE_M) return null
        val cm = distanceM * 100f
        return if (cm < 95f) (cm / 10f).roundToInt() * 10 else (cm / 50f).roundToInt() * 50
    }

    /**
     * One look from several frames: the most common colour and shape, the middle size and distance. One frame
     * in which the phone had wandered onto the table (orange, 45 cm) does not become the description.
     */
    fun agree(looks: List<ItemLook>): ItemLook = ItemLook(
        color = looks.mapNotNull { it.color }.commonest(),
        shape = looks.mapNotNull { it.shape }.commonest(),
        lengthCm = looks.mapNotNull { it.lengthCm }.median(),
        widthCm = looks.mapNotNull { it.widthCm }.median(),
        distanceCm = looks.mapNotNull { it.distanceCm }.median(),
    )

    private fun <T> List<T>.commonest(): T? = groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /** The lower middle value, so that with few frames one far-off frame does not pull it up. */
    private fun List<Int>.median(): Int? = sorted().let { if (it.isEmpty()) null else it[(it.size - 1) / 2] }

    /** Everything known about the thing. Without a distance there is no size either. */
    fun look(outline: Outline?, color: ColorName?, distanceM: Float?, hfovDeg: Float, imageWidth: Int): ItemLook {
        val shape = outline?.let { shape(it) }
        val distance = distanceCm(distanceM)
        if (outline == null || distance == null || distanceM == null) return ItemLook(color, shape, null, null, distance)
        val length = cm(outline.lengthPx, distanceM, hfovDeg, imageWidth)
        val width = cm(outline.widthPx, distanceM, hfovDeg, imageWidth)
        return if (outline.lengthPx / outline.widthPx < EVEN_RATIO) {
            ItemLook(color, shape, roundCm((length + width) / 2f), null, distance)
        } else {
            ItemLook(color, shape, roundCm(length), roundCm(width), distance)
        }
    }
}

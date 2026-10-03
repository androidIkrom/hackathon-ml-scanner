package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenterlegacy.InteractiveSegmenterLegacy
import java.io.Closeable
import java.nio.ByteOrder
import java.util.Locale

/**
 * MediaPipe's interactive segmenter (magic_touch, 6 MB): the outline of the thing at one point of the picture,
 * whatever kind of thing it is: the middle of the frame to begin with, then wherever the thing went.
 * The "legacy" API is the one that takes this model; the new one wants a bundle Google does not publish.
 * One worker thread only. Throws from the constructor when the model is missing.
 */
class ItemSegmenter(context: Context) : Closeable {
    /** One answer: [values] row by row for a [width] x [height] picture (ItemMask reads them). */
    class Answer(val values: FloatArray, val width: Int, val height: Int)

    private val segmenter: InteractiveSegmenterLegacy = InteractiveSegmenterLegacy.createFromOptions(
        context,
        InteractiveSegmenterLegacy.InteractiveSegmenterLegacyOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setOutputConfidenceMasks(true)
            .setOutputCategoryMask(false)
            .build(),
    )

    private var calls = 0

    /**
     * The thing at ([x], [y]) (0..1) of [frame], or null when the segmenter fails. The first confidence mask
     * is taken; whether it counts the thing high or low, ItemMask reads it by the point asked for.
     */
    fun at(frame: Bitmap, x: Float, y: Float): Answer? = try {
        // The point is given in pixels: with 0.5, 0.5 the mask came out at the top-left corner (the logs).
        val point = InteractiveSegmenterLegacy.RegionOfInterest.create(NormalizedKeypoint.create(x * frame.width, y * frame.height))
        val result = segmenter.segment(BitmapImageBuilder(frame).build(), point)
        val masks = result.confidenceMasks().get()
        if (calls++ % 8 == 0) {
            val about = masks.joinToString(" | ") { m ->
                val f = ByteBufferExtractor.extract(m).order(ByteOrder.nativeOrder()).asFloatBuffer()
                var max = 0f
                var high = 0
                val n = f.remaining()
                val mid = f.get((m.height / 2) * m.width + m.width / 2)
                var sx = 0L
                var sy = 0L
                for (i in 0 until n) {
                    val v = f.get(i)
                    if (v > max) max = v
                    if (v >= 0.5f) {
                        high++
                        sx += i % m.width
                        sy += i / m.width
                    }
                }
                val at = if (high > 0) "at ${sx / high},${sy / high}" else "nowhere"
                String.format(Locale.US, "%dx%d mid %.2f max %.2f high %d%% %s", m.width, m.height, mid, max, high * 100 / n, at)
            }
            Log.i("Nungil", "Item segmenter: ${masks.size} masks: $about; category ${result.categoryMask().isPresent}")
        }
        val mask = masks.first()
        val floats = ByteBufferExtractor.extract(mask).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val values = FloatArray(floats.remaining())
        floats.get(values)
        Answer(values, mask.width, mask.height)
    } catch (e: Exception) {
        Log.i("Nungil", "Item segmentation failed: ${e.message}")
        null
    }

    override fun close() = segmenter.close()

    private companion object {
        const val MODEL = "magic_touch.tflite"
    }
}

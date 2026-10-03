package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.nungil.contract.Box
import com.nungil.core.items.ItemCrop
import com.nungil.core.items.ItemMask
import java.io.Closeable

/**
 * MobileNetV3-Small image embeddings (about 4 MB, L2-normalised) through MediaPipe's ImageEmbedder.
 * One worker thread only. Throws from the constructor when the model is missing.
 */
class ItemEmbedder(context: Context) : Closeable {
    private val embedder: ImageEmbedder = ImageEmbedder.createFromOptions(
        context,
        ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setL2Normalize(true)
            .setQuantize(false)
            .build(),
    )

    /** Embedding of the part of [frame] inside [box], or null when it is under 16 x 16 px or fails. */
    fun embed(frame: Bitmap, box: Box): FloatArray? {
        val r = ItemCrop.rect(box, frame.width, frame.height) ?: return null
        return embed(Bitmap.createBitmap(frame, r[0], r[1], r[2] - r[0], r[3] - r[1]))
    }

    /**
     * Embedding of the thing alone: the part of [frame] inside [square] with everything that is not on [mask]
     * painted over (ItemMask.alone). Learning and finding both make the picture here, so they make the same one.
     * Null as [embed].
     */
    fun embedAlone(frame: Bitmap, square: Box, mask: ItemMask): FloatArray? {
        val r = ItemCrop.rect(square, frame.width, frame.height) ?: return null
        val w = r[2] - r[0]
        val h = r[3] - r[1]
        val pixels = IntArray(w * h)
        frame.getPixels(pixels, 0, w, r[0], r[1], w, h)
        mask.alone(pixels, r[0], r[1], w, h, frame.width, frame.height)
        return embed(Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888))
    }

    fun embed(image: Bitmap): FloatArray? = try {
        embedder.embed(BitmapImageBuilder(image).build())
            .embeddingResult()
            .embeddings()
            .firstOrNull()
            ?.floatEmbedding()
    } catch (e: Exception) {
        Log.i("Nungil", "Item embedding failed: ${e.message}")
        null
    }

    override fun close() = embedder.close()

    private companion object {
        const val MODEL = "mobilenet_v3_small.tflite"
    }
}

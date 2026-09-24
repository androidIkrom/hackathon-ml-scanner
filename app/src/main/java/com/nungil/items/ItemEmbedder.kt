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

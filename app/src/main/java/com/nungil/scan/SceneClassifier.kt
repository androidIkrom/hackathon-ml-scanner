package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import com.nungil.contract.Box
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.nungil.core.scan.SceneRules
import java.io.Closeable

/**
 * Fallback namer: EfficientNet-Lite0 (1000 ImageNet classes) on the middle half of the frame.
 * Its names are English only, so Korean speech says the English word ("keyboard 같아요").
 * Create and use it on one worker thread; it is slow to load (about 18 MB).
 */
class SceneClassifier(context: Context) : Closeable {
    private val classifier: ImageClassifier = ImageClassifier.createFromOptions(
        context,
        ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_FILE).build())
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(MAX_GUESSES)
            .build(),
    )

    /** The best guess for the middle of [bitmap], or null below [SceneRules.MIN_SCORE] (checked here: [guesses] wants the low ones too). */
    fun nameCenter(bitmap: Bitmap): String? {
        val c = SceneRules.centerCrop(bitmap.width, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, c[0], c[1], c[2], c[3])
        val result = classifier.classify(BitmapImageBuilder(crop).build())
        val top = result.classificationResult().classifications().firstOrNull()?.categories()?.firstOrNull() ?: return null
        if (top.score() < SceneRules.MIN_SCORE) return null
        return top.displayName().ifBlank { top.categoryName() }
    }

    /** The classifier's top names and scores for the part of [bitmap] in [box] (LaptopCheck). */
    fun guesses(bitmap: Bitmap, box: Box): List<Pair<String, Float>> {
        val l = (box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val t = (box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val r = (box.right * bitmap.width).toInt().coerceIn(l + 1, bitmap.width)
        val b = (box.bottom * bitmap.height).toInt().coerceIn(t + 1, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
        val result = classifier.classify(BitmapImageBuilder(crop).build())
        return result.classificationResult().classifications().firstOrNull()?.categories().orEmpty()
            .map { (it.displayName().ifBlank { it.categoryName() }) to it.score() }
    }

    override fun close() = classifier.close()

    companion object {
        const val MODEL_FILE = "efficientnet-lite0.tflite"
        const val MAX_GUESSES = 5
    }
}

package com.nungil.people

import android.content.Context
import android.graphics.Bitmap
import com.nungil.core.people.FaceHit
import com.nungil.core.people.FaceQuality
import java.io.Closeable
import kotlin.math.min

/**
 * Finds faces, skips the unusable ones (under 64 px, turned more than 35° or tilted more than 25°), embeds the
 * rest and matches them against saved people. Worker thread only; loads the model and the people on creation.
 */
class FaceIdentifier(context: Context) : Closeable {
    private val embedder = FaceEmbedder(context)
    private val finder = FaceFinder()
    private val recognizer = FaceRecognizer(context).also { it.reload() }

    fun identify(bitmap: Bitmap): List<FaceHit> {
        if (recognizer.isEmpty) return emptyList()
        return finder.find(bitmap).mapNotNull { face ->
            val box = face.boundingBox
            if (!FaceQuality.usable(min(box.width(), box.height()), face.headEulerAngleY, face.headEulerAngleX)) {
                return@mapNotNull null
            }
            val vector = embedder.embed(bitmap, face) ?: return@mapNotNull null
            val match = recognizer.identify(vector) ?: return@mapNotNull null
            val name = recognizer.nameOf(match.id) ?: return@mapNotNull null
            FaceHit(box.exactCenterX() / bitmap.width, box.exactCenterY() / bitmap.height, match.id, name, match.score)
        }
    }

    override fun close() {
        finder.close()
        embedder.close()
    }
}

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

    /** A face in a frame, at its middle (0..1), and the saved person it is; null when it is nobody saved or too turned to tell. */
    data class FaceSeen(val centerX: Float, val centerY: Float, val name: String?)

    /** Every face in [bitmap], recognised or not: "who is this" tells "I don't know this person" from "nobody". */
    fun faces(bitmap: Bitmap): List<FaceSeen> = finder.find(bitmap).map { face ->
        val box = face.boundingBox
        val usable = FaceQuality.usable(min(box.width(), box.height()), face.headEulerAngleY, face.headEulerAngleX)
        val name = if (!usable || recognizer.isEmpty) {
            null
        } else {
            embedder.embed(bitmap, face)?.let { recognizer.identify(it) }?.let { recognizer.nameOf(it.id) }
        }
        FaceSeen(box.exactCenterX() / bitmap.width, box.exactCenterY() / bitmap.height, name)
    }

    override fun close() {
        finder.close()
        embedder.close()
    }
}

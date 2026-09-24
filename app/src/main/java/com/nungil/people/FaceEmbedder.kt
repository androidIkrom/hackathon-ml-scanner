package com.nungil.people

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.nungil.core.people.FaceNetPreprocess
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * FaceNet-512 embeddings. Each face is embedded as seen and mirrored, and the two are averaged (L2-normalised).
 * Not thread-safe: use it from one worker thread. Throws from the constructor when the model is missing.
 */
class FaceEmbedder(context: Context) : Closeable {
    private val interpreter = Interpreter(loadModel(context), Interpreter.Options().setNumThreads(THREADS))
    private val outputSize = interpreter.getOutputTensor(0).shape().last()
    private val input: ByteBuffer = ByteBuffer
        .allocateDirect(4 * SIZE * SIZE * 3)
        .order(ByteOrder.nativeOrder())
    private val pixels = IntArray(SIZE * SIZE)

    /** Embedding of [face] inside [frame], or null when the face crop is under 24 px. */
    fun embed(frame: Bitmap, face: Face): FloatArray? {
        val crop = FaceCrops.crop(frame, face) ?: return null
        return embedCrop(crop)
    }

    /** Embedding of an already cut-out face. */
    fun embedCrop(crop: Bitmap): FloatArray {
        val a = normalize(run(crop))
        val b = normalize(run(FaceCrops.mirror(crop)))
        return normalize(FloatArray(a.size) { (a[it] + b[it]) / 2f })
    }

    private fun run(face: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(face, SIZE, SIZE, true)
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.rewind()
        input.asFloatBuffer().put(FaceNetPreprocess.standardize(pixels))
        val output = arrayOf(FloatArray(outputSize))
        interpreter.run(input, output)
        return output[0]
    }

    override fun close() = interpreter.close()

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum).toFloat()
        return if (norm == 0f) v else FloatArray(v.size) { v[it] / norm }
    }

    private companion object {
        const val MODEL = "facenet_512.tflite"
        const val SIZE = FaceNetPreprocess.SIZE
        const val THREADS = 4

        /** Memory-maps the model from assets (app/build.gradle keeps .tflite uncompressed). */
        fun loadModel(context: Context): MappedByteBuffer =
            context.assets.openFd(MODEL).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}

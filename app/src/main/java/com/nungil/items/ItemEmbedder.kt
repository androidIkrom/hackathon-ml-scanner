package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.nungil.contract.Box
import com.nungil.core.items.EmbedInput
import com.nungil.core.items.ItemCrop
import com.nungil.core.items.ItemMask
import com.nungil.core.scan.ModelLock
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * MobileNetV3-Small image embeddings (about 4 MB, L2-normalised), straight through LiteRT: the picture stretched to
 * 224 x 224, red, green, blue from 0 to 1 (EmbedInput), as MediaPipe's ImageEmbedder made them, which saved the
 * items before 2026-10-15 (cosine 0.99 at least, ItemEmbedderParityTest). MediaPipe took 26 ms a square of Live's
 * saved-item search on the phone, the model itself 6.
 * Shared by the screens (SharedModels), one call at a time. Throws from the constructor when the model is missing.
 */
class ItemEmbedder(context: Context) : Closeable {
    private val interpreter: Interpreter = ModelLock.shared.load {
        Interpreter(loadModel(context), Interpreter.Options().setNumThreads(THREADS))
    }
    private val side = interpreter.getInputTensor(0).shape()[1]
    private val pixels = IntArray(side * side)
    private val floats = FloatArray(side * side * 3)
    private val input = ByteBuffer.allocateDirect(4 * floats.size).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(interpreter.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder())

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
    fun embedAlone(frame: Bitmap, square: Box, mask: ItemMask): FloatArray? = aloneImage(frame, square, mask)?.let(::embed)

    /** The image [embedAlone] embeds: [square] of [frame] with everything but the item masked out. */
    fun aloneImage(frame: Bitmap, square: Box, mask: ItemMask): Bitmap? {
        val r = ItemCrop.rect(square, frame.width, frame.height) ?: return null
        val w = r[2] - r[0]
        val h = r[3] - r[1]
        val pixels = IntArray(w * h)
        frame.getPixels(pixels, 0, w, r[0], r[1], w, h)
        mask.alone(pixels, r[0], r[1], w, h, frame.width, frame.height)
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    @Synchronized
    fun embed(image: Bitmap): FloatArray? = try {
        val scaled = Bitmap.createScaledBitmap(image, side, side, true)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== image) scaled.recycle()
        EmbedInput.fill(pixels, floats)
        input.rewind()
        input.asFloatBuffer().put(floats)
        output.rewind()
        ModelLock.shared.run { interpreter.run(input, output) }
        output.rewind()
        val raw = FloatArray(output.capacity() / 4)
        output.asFloatBuffer().get(raw)
        EmbedInput.unit(raw)
    } catch (e: Exception) {
        Log.i("Nungil", "Item embedding failed: ${e.message}")
        null
    }

    override fun close() = ModelLock.shared.load { interpreter.close() }

    private companion object {
        const val MODEL = "mobilenet_v3_small.tflite"
        /** The model took 5.9 ms on two threads on the phone. */
        const val THREADS = 2

        /** Memory-maps the model from assets (app/build.gradle keeps .tflite uncompressed). */
        fun loadModel(context: Context) =
            context.assets.openFd(MODEL).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}

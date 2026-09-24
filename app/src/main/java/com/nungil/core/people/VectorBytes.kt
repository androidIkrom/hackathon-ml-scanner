package com.nungil.core.people

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Embedding vectors as stored in Room (FaceEmbeddingEntity.vector, ItemEmbeddingEntity.vector): little-endian floats. */
object VectorBytes {
    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(vector)
        return buffer.array()
    }

    fun toFloats(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "byte count ${bytes.size} is not a multiple of 4" }
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also { floats.get(it) }
    }
}

package com.nungil.walk

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * Debug builds only: writes every analysed depth frame into the app's files, so a walk can be replayed
 * off the phone (`adb exec-out run-as com.nungil cat files/walk-rec/<name>`). Depth on a plain floor is
 * too noisy to tune steps and stairs from a description of what was said. Worker thread only.
 *
 * One frame: wall-clock ms (long), grid width and height (int), focal length in grid pixels, pitch and
 * metres advanced (float), the standpoint's x, z and yaw (float, NaN when unknown), a byte "has depth",
 * then width x height depths in millimetres (unsigned short).
 */
internal class WalkRecorder(context: Context) : Closeable {
    private var out: DataOutputStream? = null
    private var bytes = ByteBuffer.allocate(0)
    private var frames = 0

    init {
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            dir.listFiles().orEmpty().sortedByDescending { it.name }.drop(KEEP - 1).forEach { it.delete() }
            val file = File(dir, "walk2-${System.currentTimeMillis()}.bin")
            out = DataOutputStream(BufferedOutputStream(file.outputStream()))
            Log.i(TAG, "Walk recording ${file.path}")
        }
    }

    fun frame(input: WalkInput, gridM: FloatArray?) {
        val o = out ?: return
        if (frames++ >= MAX_FRAMES) return
        runCatching {
            o.writeLong(System.currentTimeMillis())
            o.writeInt(input.gridWidth)
            o.writeInt(input.gridHeight)
            o.writeFloat(input.focalGridPx)
            o.writeFloat(input.pitchRad)
            o.writeFloat(input.advancedM)
            o.writeFloat(input.standpoint?.x ?: Float.NaN)
            o.writeFloat(input.standpoint?.z ?: Float.NaN)
            o.writeFloat(input.standpoint?.yawRad ?: Float.NaN)
            o.writeBoolean(gridM != null)
            if (gridM != null) {
                if (bytes.capacity() != gridM.size * 2) bytes = ByteBuffer.allocate(gridM.size * 2)
                bytes.clear()
                for (m in gridM) bytes.putShort((if (m.isNaN()) 0f else m * 1000f).toInt().coerceIn(0, 0xFFFF).toShort())
                o.write(bytes.array())
            }
        }.onFailure { close() }
    }

    override fun close() {
        runCatching { out?.close() }
        out = null
    }

    private companion object {
        const val TAG = "Nungil"
        const val DIR = "walk-rec"
        const val KEEP = 3
        const val MAX_FRAMES = 2_400
    }
}

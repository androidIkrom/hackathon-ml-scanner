package com.nungil.core.ui

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The app's own "microphone on" / "microphone off" sound: two soft sine notes, rising for on and
 * falling for off, with short fades so they never click. It replaces the recognizer's system beep.
 */
object ChimeSynth {
    const val SAMPLE_RATE = 44_100
    const val NOTE_MS = 90
    const val GAP_MS = 30
    const val FADE_MS = 12
    const val AMPLITUDE = 0.35

    val MIC_ON = doubleArrayOf(660.0, 990.0)
    val MIC_OFF = doubleArrayOf(990.0, 660.0)

    fun render(notesHz: DoubleArray): ShortArray {
        val note = SAMPLE_RATE * NOTE_MS / 1000
        val gap = SAMPLE_RATE * GAP_MS / 1000
        val fade = SAMPLE_RATE * FADE_MS / 1000
        val out = ShortArray(notesHz.size * note + (notesHz.size - 1) * gap)
        notesHz.forEachIndexed { n, hz ->
            val start = n * (note + gap)
            for (i in 0 until note) {
                val envelope = min(1.0, min(i, note - 1 - i).toDouble() / fade)
                val v = sin(2 * PI * hz * i / SAMPLE_RATE) * AMPLITUDE * envelope
                out[start + i] = (v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }
}

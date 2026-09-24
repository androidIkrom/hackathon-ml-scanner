package com.nungil.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import com.nungil.core.ui.ChimeSynth

/** Plays the app's own "microphone on" / "microphone off" sound. Safe from any thread. */
class MicChime {
    private val thread = HandlerThread("nungil-chime").apply { start() }
    private val handler = Handler(thread.looper)
    private val on by lazy { ChimeSynth.render(ChimeSynth.MIC_ON) }
    private val off by lazy { ChimeSynth.render(ChimeSynth.MIC_OFF) }

    fun playOn() = handler.post { play(on) }

    fun playOff() = handler.post { play(off) }

    fun release() {
        thread.quitSafely()
    }

    /** Runs on the chime thread. */
    private fun play(pcm: ShortArray) {
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(ChimeSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        }.getOrNull() ?: return
        runCatching {
            track.write(pcm, 0, pcm.size)
            track.play()
        }
        val lengthMs = pcm.size * 1000L / ChimeSynth.SAMPLE_RATE
        handler.postDelayed({ runCatching { track.release() } }, lengthMs + 100)
    }
}

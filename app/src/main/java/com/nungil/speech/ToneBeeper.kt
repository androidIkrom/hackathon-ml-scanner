package com.nungil.speech

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.HandlerThread
import com.nungil.contract.app.Beeper
import com.nungil.core.ui.BeepPolicy

/**
 * Short beeps on their own thread, so a busy main thread never makes the search beep stutter.
 * Safe from any thread. Call [release] when the activity is destroyed.
 */
class ToneBeeper : Beeper {
    private val thread = HandlerThread("nungil-beeper").apply { start() }
    private val handler = Handler(thread.looper)
    private var tone: ToneGenerator? = null

    @Volatile
    private var intervalMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            val interval = intervalMs
            if (interval <= 0) return
            play()
            handler.postDelayed(this, interval)
        }
    }

    override fun beep() {
        handler.post { play() }
    }

    override fun pulse(intervalMs: Long) {
        if (intervalMs <= 0) {
            stop()
            return
        }
        val old = this.intervalMs
        val next = BeepPolicy.clamp(intervalMs)
        this.intervalMs = next
        if (BeepPolicy.restartNow(old, next)) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun stop() {
        intervalMs = 0
        handler.removeCallbacks(tick)
    }

    fun release() {
        stop()
        handler.post {
            tone?.release()
            tone = null
        }
        thread.quitSafely()
    }

    /** Runs on the beeper thread. */
    private fun play() {
        val generator = tone ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }
            .getOrNull()
            ?.also { tone = it }
            ?: return
        generator.startTone(ToneGenerator.TONE_PROP_BEEP, BeepPolicy.BEEP_MS)
    }

    private companion object {
        const val VOLUME = 80
    }
}

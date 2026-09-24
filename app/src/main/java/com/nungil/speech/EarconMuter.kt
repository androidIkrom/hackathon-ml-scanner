package com.nungil.speech

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.nungil.core.ui.RecognizerPolicy

/**
 * Silences the speech recognizer's own start and stop beeps by muting the notification and system
 * streams (and music too while the app itself is quiet) for a short window. Only streams that were
 * not already muted are touched, and every one is unmuted again within MUTE_MAX_MS. Main thread.
 */
class EarconMuter(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val mutedByUs = mutableSetOf<Int>()
    private val unmute = Runnable { unmuteAll() }

    fun mute(includeMusic: Boolean) {
        val streams = if (includeMusic) BEEP_STREAMS + AudioManager.STREAM_MUSIC else BEEP_STREAMS
        for (stream in streams) {
            if (stream in mutedByUs || isMuted(stream)) continue
            // Changing a stream can throw while Do Not Disturb is on; then that stream stays as it is.
            runCatching { audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0) }
                .onSuccess { mutedByUs += stream }
        }
        main.removeCallbacks(unmute)
        main.postDelayed(unmute, RecognizerPolicy.MUTE_MAX_MS)
    }

    /** Unmute after the recognizer's beep has had time to (not) play. */
    fun unmuteSoon() {
        main.removeCallbacks(unmute)
        main.postDelayed(unmute, RecognizerPolicy.MUTE_TAIL_MS)
    }

    /** The app is about to answer: give it its voice back at once. */
    fun unmuteMusicNow() {
        if (mutedByUs.remove(AudioManager.STREAM_MUSIC)) {
            runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }
        }
    }

    fun unmuteAll() {
        main.removeCallbacks(unmute)
        for (stream in mutedByUs) {
            runCatching { audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0) }
        }
        mutedByUs.clear()
    }

    private fun isMuted(stream: Int): Boolean = runCatching { audio.isStreamMute(stream) }.getOrDefault(true)

    private companion object {
        val BEEP_STREAMS = listOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)
    }
}

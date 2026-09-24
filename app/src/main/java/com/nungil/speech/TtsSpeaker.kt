package com.nungil.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nungil.contract.Lang
import com.nungil.contract.app.Speaker
import com.nungil.core.ui.SpeechQueue
import com.nungil.core.ui.VoiceChoice
import com.nungil.core.ui.VoicePick
import java.util.Locale

/**
 * The app's only voice. Wraps TextToSpeech with [SpeechQueue] so phrases never talk over each other.
 * Safe to call from any thread; all work happens on the main thread.
 *
 * @param wanted the language the user chose (UI language).
 * @param onCaption every phrase, as it starts, for the caption bar.
 * @param onVoiceChoice the voice actually used; a missing Korean voice reports English plus a notice.
 */
class TtsSpeaker(
    context: Context,
    private val wanted: () -> Lang,
    private val onCaption: (String) -> Unit,
    private val onVoiceChoice: (VoiceChoice) -> Unit,
) : Speaker, TextToSpeech.OnInitListener {

    private enum class State { STARTING, READY, FAILED }

    private val main = Handler(Looper.getMainLooper())
    private val queue = SpeechQueue { SystemClock.elapsedRealtime() }
    private val tts = TextToSpeech(context.applicationContext, this)
    private var state = State.STARTING
    private var currentId: String? = null
    private var counter = 0
    private var finalCallback: (() -> Unit)? = null
    private var closed = false

    /** The last phrase spoken, for "repeat". Main thread. */
    val lastSpoken: String? get() = queue.lastSpoken

    /** Speaking now or about to. The microphone waits for this to become false. Main thread. */
    val isBusy: Boolean get() = queue.isSpeaking || queue.pendingCount > 0

    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            pump()
            if (queue.finalFinished()) finishFinal()
            main.postDelayed(this, SpeechQueue.POLL_MS)
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    init {
        main.post(poll)
    }

    override fun onInit(status: Int) {
        main.post {
            if (closed) return@post
            if (status != TextToSpeech.SUCCESS) {
                state = State.FAILED
                return@post
            }
            tts.setOnUtteranceProgressListener(progress)
            applyLanguage()
            state = State.READY
        }
    }

    /** Re-reads [wanted] and picks the voice; call after the language changes. Main thread. */
    fun applyLanguage() {
        val want = wanted()
        val result = runCatching { tts.isLanguageAvailable(Locale.forLanguageTag(want.speechTag)) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val choice = VoicePick.choose(want, VoicePick.support(result))
        tts.language = Locale.forLanguageTag(choice.speak.speechTag)
        onVoiceChoice(choice)
        choice.notice?.let { queue.add(it) }
    }

    override fun say(text: String) = onMain {
        queue.add(text)
        pump()
    }

    override fun sayNow(text: String) = onMain {
        if (state == State.STARTING) {
            queue.clear()
            queue.add(text)
        } else {
            queue.now(text)?.let { speak(it, TextToSpeech.QUEUE_FLUSH) }
        }
    }

    override fun sayFinal(text: String, onDone: () -> Unit) = onMain {
        finishFinal()
        if (state == State.STARTING) {
            // The engine is still starting: the sentence is spoken as soon as it is ready.
            queue.clear()
            queue.add(text)
            main.post(onDone)
            return@onMain
        }
        val t = queue.final(text)
        if (t == null) {
            onDone()
        } else {
            finalCallback = onDone
            speak(t, TextToSpeech.QUEUE_FLUSH)
        }
    }

    override fun stop() = onMain {
        queue.clear()
        currentId = null
        runCatching { tts.stop() }
        finishFinal()
    }

    /** Says the last phrase again; false when nothing has been said yet. Main thread. */
    fun repeatLast(): Boolean {
        val t = queue.lastSpoken ?: return false
        sayNow(t)
        return true
    }

    fun shutdown() {
        closed = true
        main.removeCallbacks(poll)
        finishFinal()
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    private fun pump() {
        if (state == State.STARTING) return
        queue.next()?.let { speak(it, TextToSpeech.QUEUE_ADD) }
    }

    private fun speak(text: String, mode: Int) {
        onCaption(text)
        if (state != State.READY) {
            // No working engine: the caption still shows the sentence.
            queue.done()
            return
        }
        val id = "nungil-${counter++}"
        currentId = id
        if (tts.speak(text, mode, null, id) != TextToSpeech.SUCCESS) {
            currentId = null
            queue.done()
        }
    }

    private fun finished(utteranceId: String?) {
        main.post {
            if (utteranceId != null && utteranceId == currentId) {
                currentId = null
                queue.done()
            }
        }
    }

    private fun finishFinal() {
        val callback = finalCallback ?: return
        finalCallback = null
        callback()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}

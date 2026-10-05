package com.nungil.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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
    private val resolver = context.applicationContext.contentResolver

    /** Sentences to say in the brighter voice; each is said so once. */
    private val bright = HashSet<String>()

    /** The pitch was raised for the last sentence and has to go back. */
    private var pitchRaised = false
    private var closed = false

    /** True while the user is talking: nothing is spoken until [resumeAfterUser]. */
    private var held = false
    private var currentText: String? = null
    private var lastEndText: String? = null
    private var lastEndAt = 0L

    /** The last phrase spoken, for "repeat". Main thread. */
    val lastSpoken: String? get() = queue.lastSpoken

    /**
     * What the microphone may be hearing of the app's own voice: the sentence being spoken, or the one
     * that ended less than [ECHO_WINDOW_MS] ago. Main thread.
     */
    fun recentSpeech(): String? =
        currentText ?: lastEndText?.takeIf { SystemClock.elapsedRealtime() - lastEndAt < ECHO_WINDOW_MS }

    /** The user started talking: stop at once, drop what was queued and stay quiet. Main thread. */
    fun holdForUser() {
        held = true
        stop()
    }

    /** The user finished: speak again (answers to their command come next). Main thread. */
    fun resumeAfterUser() {
        held = false
        pump()
    }

    private var whenQuiet: (() -> Unit)? = null
    private var wasSpeaking = false

    /** Called on the main thread every time the app has finished saying everything it had to say. */
    var onQuiet: () -> Unit = {}

    /**
     * Runs [action] once everything queued has been said (at once when nothing is). A question asked
     * with say() must be heard before the microphone takes over; only the latest action is kept.
     */
    fun whenQuiet(action: () -> Unit) = onMain {
        if (queue.isQuiet) action() else whenQuiet = action
    }

    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            pump()
            if (queue.isQuiet) {
                if (wasSpeaking) onQuiet()
                whenQuiet?.let {
                    whenQuiet = null
                    it()
                }
            }
            wasSpeaking = !queue.isQuiet
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

    override fun sayLive(text: String) = onMain {
        queue.addLive(text)
        pump()
    }

    /**
     * [text] is good news ("I see it!", "All done!"): the next time it is said, it is said in a brighter voice.
     * The engine has no feelings to pick from; a pitch a little above the user's own setting is the nearest
     * thing. Call just before say, sayNow or sayFinal with the same text.
     */
    fun brighten(text: String) = onMain {
        if (bright.size >= MAX_BRIGHT) bright.clear()
        bright += text.trim()
    }

    override fun sayNow(text: String) = onMain {
        if (state == State.STARTING || held) {
            queue.clear()
            queue.add(text)
        } else {
            queue.now(text)?.let { speak(it, TextToSpeech.QUEUE_FLUSH) }
        }
    }

    override fun sayFinal(text: String, onDone: () -> Unit) = onMain {
        finishFinal()
        if (state == State.STARTING || held) {
            // The engine is still starting, or the user is talking: the sentence waits its turn.
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
        // What was cut off is still in the air: the microphone may hand it back as words (recentSpeech).
        currentText?.let {
            lastEndText = it
            lastEndAt = SystemClock.elapsedRealtime()
        }
        currentText = null
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
        if (state == State.STARTING || held) return
        queue.next()?.let { speak(it, TextToSpeech.QUEUE_ADD) }
    }

    private fun speak(text: String, mode: Int) {
        onCaption(text)
        if (state != State.READY) {
            // No working engine: the caption still shows the sentence.
            queue.done()
            return
        }
        val cheer = bright.remove(text)
        if (cheer || pitchRaised) {
            // The user's own pitch (phone settings) stays the base; before the first bright sentence it is never set.
            val own = Settings.Secure.getInt(resolver, Settings.Secure.TTS_DEFAULT_PITCH, NORMAL_PITCH) / NORMAL_PITCH.toFloat()
            runCatching { tts.setPitch(if (cheer) own * BRIGHT_PITCH else own) }
            pitchRaised = cheer
        }
        val id = "nungil-${counter++}"
        // With the moment a thing was confirmed ("Live:"), this shows how late it was said.
        Log.i("Nungil", "Saying \"$text\"")
        currentId = id
        currentText = text
        if (tts.speak(text, mode, null, id) != TextToSpeech.SUCCESS) {
            currentId = null
            queue.done()
        }
    }

    private fun finished(utteranceId: String?) {
        main.post {
            if (utteranceId != null && utteranceId == currentId) {
                currentId = null
                lastEndText = currentText
                lastEndAt = SystemClock.elapsedRealtime()
                currentText = null
                queue.done()
            }
        }
    }

    private fun finishFinal() {
        val callback = finalCallback ?: return
        finalCallback = null
        callback()
    }

    private companion object {
        /** How long after a sentence ends the microphone may still be hearing it. */
        const val ECHO_WINDOW_MS = 1_500L

        /** How much higher the brighter voice is. */
        const val BRIGHT_PITCH = 1.18f

        /** The phone's pitch setting for an unchanged voice. */
        const val NORMAL_PITCH = 100
        const val MAX_BRIGHT = 8
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}

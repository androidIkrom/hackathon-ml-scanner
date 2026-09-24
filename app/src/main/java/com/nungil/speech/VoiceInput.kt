package com.nungil.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.nungil.contract.Lang
import com.nungil.core.ui.BargeIn
import com.nungil.core.ui.ListenAction
import com.nungil.core.ui.Phrase
import com.nungil.core.ui.RecognizerPolicy
import com.nungil.core.ui.VoiceBargeIn

/**
 * The app's only microphone. Main thread only.
 *
 * Always-on mode ([start]/[stop]) listens the whole time the app is open, also while the app is
 * talking. As soon as the user's words appear, every app sound stops and stays stopped until the
 * phrase is finished ([holdSound]/[releaseSound]), so the user is never talked over or cut off.
 * The app's own voice picked up by the microphone is recognised and ignored ([VoiceBargeIn]).
 * The recognizer's system beeps are muted ([EarconMuter]); the app plays its own chime instead.
 * [listenOnce] hears one phrase for a screen while always-on is off.
 *
 * @param appSaying what the app is saying now or has just said (for echo detection).
 */
class VoiceInput(
    private val context: Context,
    private val language: () -> Lang,
    private val appSaying: () -> String?,
    private val holdSound: () -> Unit,
    private val releaseSound: () -> Unit,
    private val onHeard: (String) -> Unit,
    private val onProblem: (Phrase) -> Unit,
) : RecognitionListener {

    private val main = Handler(Looper.getMainLooper())
    private val policy = RecognizerPolicy()
    private val muter = EarconMuter(context)
    private val chime = MicChime()
    private var recognizer: SpeechRecognizer? = null
    private var alwaysOn = false
    private var oneShot: ((String) -> Unit)? = null
    private var lastPartial = ""
    private var holding = false

    /** Set by a microphone button: app sound stays off until then, even through silence. */
    private var talkUntil = 0L
    private val listenNow = Runnable { listen() }
    private val holdSafety = Runnable { endHold() }

    val isOn: Boolean get() = alwaysOn

    /** Turns the always-on microphone on (a no-op when it already is) and plays the "on" chime. */
    fun start() {
        if (alwaysOn) return
        alwaysOn = true
        chime.playOn()
        schedule(RecognizerPolicy.DELAY_AFTER_ENABLE_MS)
    }

    /** A microphone button was pressed: every app sound stops and stays off while the user talks. */
    fun talkNow() {
        if (alwaysOn) chime.playOn() else start()
        holdForTalk()
    }

    /** [chime] plays the "off" sound: true when the user turned voice off, false when the app pauses. */
    fun stop(chime: Boolean = false) {
        val wasOn = alwaysOn
        alwaysOn = false
        oneShot = null
        main.removeCallbacks(listenNow)
        recognizer?.cancel()
        endHold(force = true)
        muter.unmuteAll()
        if (chime && wasOn) this.chime.playOff()
    }

    /** One phrase for a screen (always-on is off). */
    fun listenOnce(onText: (String) -> Unit) {
        oneShot = onText
        if (!alwaysOn) {
            chime.playOn()
            schedule(RecognizerPolicy.DELAY_AFTER_ENABLE_MS)
        }
        holdForTalk()
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
        chime.release()
    }

    private fun schedule(delayMs: Long) {
        main.removeCallbacks(listenNow)
        main.postDelayed(listenNow, delayMs)
    }

    private fun listen() {
        if (!alwaysOn && oneShot == null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stop()
            onProblem(Phrase.MIC_NEEDED)
            return
        }
        val r = recognizer ?: create() ?: run {
            stop()
            onProblem(Phrase.VOICE_UNAVAILABLE)
            return
        }
        lastPartial = ""
        // Hide the recognizer's start beep. Music stays on while the app is talking.
        muter.mute(includeMusic = appSaying() == null)
        r.startListening(intent())
    }

    /** Some phones report no recognition service but still have Android's on-device recognizer. */
    private fun create(): SpeechRecognizer? {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        val r = when {
            available -> SpeechRecognizer.createSpeechRecognizer(context)
            Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else -> null
        }
        r?.setRecognitionListener(this)
        recognizer = r
        return r
    }

    /**
     * Never add EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS: on Google's on-device recognizer it switches to
     * continuous dictation and every final result arrives empty (build guide §10.2).
     */
    private fun intent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, policy.language(language()).speechTag)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

    override fun onReadyForSpeech(params: Bundle?) = muter.unmuteSoon()

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
        if (text.isNullOrEmpty()) return
        lastPartial = text
        if (!holding && VoiceBargeIn.onPartial(text, appSaying()) == BargeIn.STOP_ALL_SOUND) beginHold()
    }

    /** Hide the recognizer's end beep; the app is silent while the user speaks, so music may go too. */
    override fun onEndOfSpeech() = muter.mute(includeMusic = holding || appSaying() == null)

    override fun onResults(results: Bundle?) {
        muter.unmuteMusicNow()
        muter.unmuteSoon()
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            .orEmpty()
            .ifEmpty { lastPartial }
        if (text.isEmpty()) {
            onError(SpeechRecognizer.ERROR_NO_MATCH)
            return
        }
        // The app heard only itself: carry on as if it were silence.
        if (!holding && VoiceBargeIn.isEcho(text, appSaying())) {
            onError(SpeechRecognizer.ERROR_NO_MATCH)
            return
        }
        val delay = policy.afterResult()
        endHold(force = true)
        val once = oneShot
        if (once != null) {
            oneShot = null
            once(text)
        } else {
            onHeard(text)
        }
        if (alwaysOn) schedule(delay)
    }

    override fun onError(error: Int) {
        muter.unmuteSoon()
        endHold()
        val decision = policy.afterError(error, language())
        when (decision.action) {
            ListenAction.STOP_NO_PERMISSION -> {
                stop()
                onProblem(Phrase.MIC_NEEDED)
                return
            }
            ListenAction.FALL_BACK_TO_ENGLISH -> onProblem(Phrase.KOREAN_RECOGNITION_MISSING)
            ListenAction.RETRY -> if (!alwaysOn) oneShot = null // a one-shot request gives up on silence
        }
        if (decision.rebuild) {
            recognizer?.destroy()
            recognizer = null
        }
        if (alwaysOn || oneShot != null) schedule(decision.delayMs)
    }

    private fun beginHold() {
        holding = true
        holdSound()
        main.removeCallbacks(holdSafety)
        main.postDelayed(holdSafety, RecognizerPolicy.HOLD_SAFETY_MS)
    }

    private fun holdForTalk() {
        talkUntil = SystemClock.elapsedRealtime() + RecognizerPolicy.TALK_WINDOW_MS
        beginHold()
    }

    /** Silence or an error ends the hold only after a button's talk window; a heard phrase always does. */
    private fun endHold(force: Boolean = false) {
        if (!holding) return
        val left = talkUntil - SystemClock.elapsedRealtime()
        if (!force && left > 0) {
            main.removeCallbacks(holdSafety)
            main.postDelayed(holdSafety, left)
            return
        }
        talkUntil = 0
        holding = false
        main.removeCallbacks(holdSafety)
        releaseSound()
    }

    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

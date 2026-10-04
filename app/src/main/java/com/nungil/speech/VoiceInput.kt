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
import android.util.Log
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
import com.nungil.core.voice.WakeWord

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
 * While the app is asleep (see WakeWord) only the wake word stops app sound.
 *
 * @param appSaying what the app is saying now or has just said (for echo detection).
 * @param isAwake true between the wake word and "Eye stop".
 * @param understood true when a recognizer guess would do something; such a guess beats a misheard first one.
 * @param answersNow true for words that are the short answer a screen waits for; it is taken while they are
 *   still being said (VoiceBargeIn.answerIn), not at the end of the phrase.
 * @param onGuesses every guess for the phrase, just before [onHeard] gets the chosen one.
 * @param bias words the recognizer should lean towards right now (place names while a place is expected).
 */
class VoiceInput(
    private val context: Context,
    private val language: () -> Lang,
    private val appSaying: () -> String?,
    private val isAwake: () -> Boolean,
    private val holdSound: () -> Unit,
    private val releaseSound: () -> Unit,
    private val understood: (String) -> Boolean,
    private val answersNow: (String) -> Boolean,
    private val onGuesses: (List<String>) -> Unit,
    private val bias: () -> List<String>,
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
    private var speechBegan = false
    private var listeningSince = 0L

    /** Set by a microphone button: app sound stays off until then, even through silence. */
    private var talkUntil = 0L
    private val listenNow = Runnable { listen() }
    private val holdSafety = Runnable { endHold() }

    /** The words so far have stood still and hold the answer a screen waits for: it is heard now. */
    private val earlyAnswer = Runnable {
        val answer = VoiceBargeIn.answerIn(lastPartial, appSaying(), answersNow)
        if (alwaysOn && oneShot == null && answer != null) {
            Log.i(TAG, "Answer \"$answer\" taken from the words so far: \"$lastPartial\"")
            recognizer?.cancel()
            lastPartial = ""
            speechBegan = false
            muter.unmuteMusicNow()
            muter.unmuteSoon()
            val delay = policy.afterResult()
            endHold(force = true)
            onGuesses(listOf(answer))
            onHeard(answer)
            if (alwaysOn) schedule(delay)
        }
    }

    val isOn: Boolean get() = alwaysOn

    /** Turns the always-on microphone on (a no-op when it already is) and plays the "on" chime. */
    fun start() {
        if (alwaysOn) return
        alwaysOn = true
        chime.playOn()
        schedule(RecognizerPolicy.DELAY_AFTER_ENABLE_MS)
    }

    fun chimeOn() = chime.playOn()

    fun chimeOff() = chime.playOff()

    /** A microphone button was pressed: every app sound stops and stays off while the user talks. */
    fun talkNow() {
        if (alwaysOn) chime.playOn() else start()
        holdForTalk()
    }

    /**
     * The app has just stopped talking. A recognition session that ran while it talked comes back empty
     * (the logs: three in a row, and the user's answer to "Say yes to go" was lost in one of them), so
     * that session is dropped and a clean one starts for what the user says next. Not when the user is
     * already talking over the app: partial words that are not the app's own.
     */
    fun freshSession() {
        if (!alwaysOn || oneShot != null) return
        if (lastPartial.isNotEmpty() && !VoiceBargeIn.isEcho(lastPartial, appSaying())) {
            Log.i(TAG, "Recognizer session kept after the app spoke: heard \"$lastPartial\"")
            return
        }
        if (SystemClock.elapsedRealtime() - listeningSince < RecognizerPolicy.FRESH_SESSION_MIN_MS) return
        main.removeCallbacks(earlyAnswer)
        recognizer?.cancel()
        schedule(RecognizerPolicy.DELAY_AFTER_SILENCE_MS)
    }

    /** [chime] plays the "off" sound: true when the user turned voice off, false when the app pauses. */
    fun stop(chime: Boolean = false) {
        val wasOn = alwaysOn
        alwaysOn = false
        oneShot = null
        main.removeCallbacks(listenNow)
        main.removeCallbacks(earlyAnswer)
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
        speechBegan = false
        listeningSince = SystemClock.elapsedRealtime()
        // Hide the recognizer's start beep. Music (the TTS stream) is muted only while the user is talking.
        muter.mute(includeMusic = holding)
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
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, RecognizerPolicy.MAX_GUESSES)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .apply {
                val words = bias()
                if (Build.VERSION.SDK_INT >= 33 && words.isNotEmpty()) {
                    putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(words))
                }
            }

    override fun onReadyForSpeech(params: Bundle?) = muter.unmuteSoon()

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
        if (text.isNullOrEmpty()) return
        lastPartial = text
        main.removeCallbacks(earlyAnswer)
        if (oneShot == null && isAwake() && VoiceBargeIn.answerIn(text, appSaying(), answersNow) != null) {
            main.postDelayed(earlyAnswer, RecognizerPolicy.EARLY_ANSWER_MS)
        }
        if (holding) return
        val userTalking = if (isAwake()) {
            VoiceBargeIn.onPartial(text, appSaying()) == BargeIn.STOP_ALL_SOUND
        } else {
            WakeWord.addressed(text)
        }
        if (userTalking) beginHold()
    }

    /**
     * Hide the recognizer's end beep. Music (the stream TTS speaks on) goes quiet only while the user is
     * talking: muting it whenever the app happened to be silent swallowed the live-scan announcements,
     * because the always-on recognizer ends a cycle every second or so and re-muted it each time.
     */
    override fun onEndOfSpeech() = muter.mute(includeMusic = holding)

    override fun onResults(results: Bundle?) {
        main.removeCallbacks(earlyAnswer)
        muter.unmuteMusicNow()
        muter.unmuteSoon()
        val guesses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        // One phrase for a screen is a name or a query: there the first guess is the one to keep.
        val text = RecognizerPolicy.choose(guesses) { oneShot == null && understood(it) }.ifEmpty { lastPartial }
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
        onGuesses(guesses.map { it.trim() }.filter { it.isNotEmpty() })
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
        main.removeCallbacks(earlyAnswer)
        // Speech was heard but no words came out of it: worth a line, plain silence is not.
        if (speechBegan && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            val seconds = (SystemClock.elapsedRealtime() - listeningSince) / 1000
            val partial = if (lastPartial.isEmpty()) "" else ", last words \"$lastPartial\""
            Log.i(TAG, "Recognizer heard sound for $seconds s but no words" + (if (appSaying() != null) " (the app was talking)" else "") + partial)
        }
        speechBegan = false
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

    override fun onBeginningOfSpeech() {
        speechBegan = true
    }
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

private const val TAG = "Nungil"

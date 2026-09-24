package com.nungil.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.nungil.contract.Lang
import com.nungil.core.ui.ListenAction
import com.nungil.core.ui.Phrase
import com.nungil.core.ui.RecognizerPolicy

/**
 * The app's only microphone. Main thread only.
 *
 * Always-on mode ([start]/[stop]) restarts the recognizer after every result and error; [listenOnce]
 * hears one phrase for a screen while always-on is off. Every rule from build guide §10 lives here or
 * in [RecognizerPolicy].
 */
class VoiceInput(
    private val context: Context,
    private val language: () -> Lang,
    private val isSpeaking: () -> Boolean,
    private val stopSpeaking: () -> Unit,
    private val onHeard: (String) -> Unit,
    private val onProblem: (Phrase) -> Unit,
) : RecognitionListener {

    private val main = Handler(Looper.getMainLooper())
    private val policy = RecognizerPolicy()
    private var recognizer: SpeechRecognizer? = null
    private var alwaysOn = false
    private var oneShot: ((String) -> Unit)? = null
    private var lastPartial = ""
    private var waitedMs = 0L
    private val listenNow = Runnable { listen() }

    val isOn: Boolean get() = alwaysOn

    fun start() {
        alwaysOn = true
        schedule(RecognizerPolicy.DELAY_AFTER_ENABLE_MS)
    }

    fun stop() {
        alwaysOn = false
        oneShot = null
        main.removeCallbacks(listenNow)
        recognizer?.cancel()
    }

    /** One phrase for a screen (always-on is off). */
    fun listenOnce(onText: (String) -> Unit) {
        oneShot = onText
        if (!alwaysOn) schedule(0)
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
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
        // The app must not hear itself: wait until it stops talking (up to the limit), then silence it.
        if (policy.shouldWait(isSpeaking(), waitedMs)) {
            waitedMs += RecognizerPolicy.WAIT_POLL_MS
            schedule(RecognizerPolicy.WAIT_POLL_MS)
            return
        }
        waitedMs = 0
        val r = recognizer ?: create() ?: run {
            stop()
            onProblem(Phrase.VOICE_UNAVAILABLE)
            return
        }
        stopSpeaking()
        lastPartial = ""
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

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            .orEmpty()
            .ifEmpty { lastPartial }
        if (text.isEmpty()) {
            onError(SpeechRecognizer.ERROR_NO_MATCH)
            return
        }
        val delay = policy.afterResult()
        val once = oneShot
        if (once != null) {
            oneShot = null
            once(text)
        } else {
            onHeard(text)
        }
        if (alwaysOn) schedule(delay)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { lastPartial = it }
    }

    override fun onError(error: Int) {
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

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

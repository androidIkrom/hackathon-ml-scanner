package com.nungil.core.ui

import com.nungil.contract.Lang

enum class ListenAction { RETRY, STOP_NO_PERMISSION, FALL_BACK_TO_ENGLISH }

data class ListenDecision(val action: ListenAction, val delayMs: Long, val rebuild: Boolean)

/**
 * Keeps the always-on microphone alive the whole time the app is open: listen again almost at once
 * after every phrase and every silence (the app never waits for its own speech to end; barge-in and
 * echo filtering in [VoiceBargeIn] handle that), rebuild a stuck recognizer after 3 real errors in a
 * row, stop on a permission error, and fall back to English once when Korean recognition is missing.
 */
class RecognizerPolicy {
    private var errorsInARow = 0

    var englishFallback = false
        private set

    /** Delay before listening again after a phrase was heard. */
    fun afterResult(): Long {
        errorsInARow = 0
        return DELAY_AFTER_COMMAND_MS
    }

    fun afterError(code: Int, wanted: Lang): ListenDecision {
        if (code == ERROR_INSUFFICIENT_PERMISSIONS) return ListenDecision(ListenAction.STOP_NO_PERMISSION, 0, false)
        val languageMissing = code == ERROR_LANGUAGE_NOT_SUPPORTED || code == ERROR_LANGUAGE_UNAVAILABLE
        if (languageMissing && wanted == Lang.KO && !englishFallback) {
            englishFallback = true
            errorsInARow = 0
            return ListenDecision(ListenAction.FALL_BACK_TO_ENGLISH, DELAY_AFTER_ERROR_MS, rebuild = true)
        }
        // Silence is the normal state of an always-on microphone, not a fault.
        if (code == ERROR_NO_MATCH || code == ERROR_SPEECH_TIMEOUT) {
            return ListenDecision(ListenAction.RETRY, DELAY_AFTER_SILENCE_MS, rebuild = false)
        }
        errorsInARow++
        val rebuild = errorsInARow >= REBUILD_AFTER_ERRORS
        if (rebuild) errorsInARow = 0
        return ListenDecision(ListenAction.RETRY, DELAY_AFTER_ERROR_MS, rebuild)
    }

    /** Recognition language: what the user chose, unless Korean recognition turned out to be missing. */
    fun language(wanted: Lang): Lang = if (englishFallback) Lang.EN else wanted


    companion object {
        /** After the "microphone on" chime has played. */
        const val DELAY_AFTER_ENABLE_MS = 400L
        const val DELAY_AFTER_COMMAND_MS = 250L
        const val DELAY_AFTER_SILENCE_MS = 100L
        const val DELAY_AFTER_ERROR_MS = 700L
        const val REBUILD_AFTER_ERRORS = 3

        /** App sound held for the user's speech is released after this even if the recognizer never answers. */
        const val HOLD_SAFETY_MS = 10_000L

        /** The recognizer's own start/stop tones are muted from its start until this long after it is ready. */
        const val MUTE_TAIL_MS = 300L

        /** After a microphone button press, app sound stays off this long even if the user is still silent. */
        const val TALK_WINDOW_MS = 6_000L

        /** "I did not understand" is said at most once in this long (people nearby are heard too). */
        const val NOT_UNDERSTOOD_GAP_MS = 10_000L

        /** Streams are never kept muted longer than this. */
        const val MUTE_MAX_MS = 2_000L

        // android.speech.SpeechRecognizer error codes, copied so this stays pure Kotlin.
        const val ERROR_SPEECH_TIMEOUT = 6
        const val ERROR_NO_MATCH = 7
        const val ERROR_CLIENT = 5
        const val ERROR_RECOGNIZER_BUSY = 8
        const val ERROR_INSUFFICIENT_PERMISSIONS = 9
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}

enum class PermissionOutcome {
    GRANTED,
    DENIED,

    /** Denied with "don't ask again": only the app settings page can allow it now. */
    BLOCKED;

    companion object {
        fun of(granted: Boolean, showRationale: Boolean): PermissionOutcome = when {
            granted -> GRANTED
            showRationale -> DENIED
            else -> BLOCKED
        }
    }
}

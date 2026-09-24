package com.nungil.core.ui

import com.nungil.contract.Lang

enum class ListenAction { RETRY, STOP_NO_PERMISSION, FALL_BACK_TO_ENGLISH }

data class ListenDecision(val action: ListenAction, val delayMs: Long, val rebuild: Boolean)

/**
 * Keeps the always-on microphone alive (build guide §10.3): restart after every result and error with
 * the guide's delays, rebuild a stuck recognizer after 3 errors in a row, stop on a permission error,
 * and fall back to English once when Korean recognition is not installed.
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
        errorsInARow++
        val rebuild = errorsInARow >= REBUILD_AFTER_ERRORS
        if (rebuild) errorsInARow = 0
        return ListenDecision(ListenAction.RETRY, DELAY_AFTER_ERROR_MS, rebuild)
    }

    /** Recognition language: what the user chose, unless Korean recognition turned out to be missing. */
    fun language(wanted: Lang): Lang = if (englishFallback) Lang.EN else wanted

    /** Let the app finish speaking before opening the microphone, but never wait longer than the limit. */
    fun shouldWait(speaking: Boolean, waitedMs: Long): Boolean = speaking && waitedMs < MAX_WAIT_FOR_SPEECH_MS

    companion object {
        const val DELAY_AFTER_ENABLE_MS = 1_500L
        const val DELAY_AFTER_COMMAND_MS = 2_500L
        const val DELAY_AFTER_ERROR_MS = 700L
        const val REBUILD_AFTER_ERRORS = 3
        const val WAIT_POLL_MS = 500L
        const val MAX_WAIT_FOR_SPEECH_MS = SpeechQueue.SHUTDOWN_SAFETY_MS

        // android.speech.SpeechRecognizer error codes, copied so this stays pure Kotlin.
        const val ERROR_SPEECH_TIMEOUT = 6
        const val ERROR_NO_MATCH = 7
        const val ERROR_CLIENT = 5
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

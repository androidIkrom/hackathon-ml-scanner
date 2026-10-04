package com.nungil.core.ui

/**
 * "Stop" said alone while the app is talking stops the talking, nothing else: Go mode keeps guiding, a scan
 * keeps scanning. Said while the app is quiet, or with more to it ("stop navigation"), it is the screen's stop
 * as before.
 */
object SpeechStop {
    private val BARE = setOf(
        "stop", "quiet", "shh", "shush", "silence",
        "멈춰", "멈춰요", "그만", "그만해", "스톱", "쉿", "조용", "조용히", "조용히해",
    )

    fun isBare(text: String): Boolean {
        val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        return words.isNotEmpty() && (words.joinToString(" ") in BARE || words.joinToString("") in BARE)
    }
}

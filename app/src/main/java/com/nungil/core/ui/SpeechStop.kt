package com.nungil.core.ui

/**
 * "Stop" said alone while the app is talking stops the talking, nothing else: Go mode keeps guiding, a scan
 * keeps scanning. Said while the app is quiet, or with more to it ("stop navigation"), it is the screen's stop
 * as before. The usual ways to say it count too ("be quiet", "stop talking", "그만 말해"), with a "please" or
 * an "okay" around them.
 */
object SpeechStop {
    private val FILLERS = setOf("please", "ok", "okay", "hey")

    private val EN = setOf(
        "stop", "stop it", "stop now", "stop talking", "stop speaking", "quiet", "be quiet", "shh", "shush", "hush",
        "silence", "enough", "shut up",
    )
    private val KO = setOf(
        "멈춰", "멈춰요", "멈춰줘", "그만", "그만해", "그만해요", "그만말해", "그만말해요", "스톱", "스톱해", "쉿", "조용",
        "조용히", "조용히해", "조용히해줘", "말하지마", "말하지마요",
    )

    fun isBare(text: String): Boolean {
        val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            .dropWhile { it in FILLERS }.dropLastWhile { it in FILLERS }
        return words.isNotEmpty() && (words.joinToString(" ") in EN || words.joinToString("") in KO)
    }
}

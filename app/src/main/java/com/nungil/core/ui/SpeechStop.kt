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
        val said = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            .dropWhile { it in FILLERS }.dropLastWhile { it in FILLERS }
        // "Stop stop": said twice is said once (it stopped the route instead of the talking, the logs).
        val words = said.filterIndexed { i, w -> i == 0 || w != said[i - 1] }
        return words.isNotEmpty() && (words.joinToString(" ") in EN || words.joinToString("") in KO)
    }

    /** A bare stop within this long of the last one that silenced the talking stops the screen as well. */
    const val AGAIN_MS = 8_000L

    /**
     * Whether a bare stop said while the app talks stops the talking only. Said again soon after, it means the
     * screen: a live scan kept scanning through three "stop"s 2 and 3 s apart, each taken as "be quiet" (the logs).
     */
    fun talkingOnly(msSinceLastTalkStop: Long): Boolean = msSinceLastTalkStop >= AGAIN_MS
}

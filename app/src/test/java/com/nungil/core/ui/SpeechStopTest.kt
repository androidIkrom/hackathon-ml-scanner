package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechStopTest {
    private fun bare(vararg texts: String) = texts.map { SpeechStop.isBare(it) }

    @Test fun theWordAlone() {
        assertEquals(List(8) { true }, bare("Stop", "stop.", "Quiet", "shh", "멈춰", "그만", "스톱", "조용"))
        assertEquals(List(3) { true }, bare("Stop!", "  stop  ", "쉿"))
    }

    @Test fun otherWaysToSayIt() {
        assertEquals(
            List(10) { true },
            bare("stop it", "stop talking", "be quiet", "quiet please", "enough", "hush", "shut up", "okay stop", "please stop", "stop please"),
        )
        assertEquals(List(7) { true }, bare("그만해요", "그만 말해", "조용히 해", "조용히 해 줘", "말하지 마", "멈춰 줘", "스톱해"))
    }

    @Test fun saidTwice() {
        // "Stop stop" stopped the route instead of the talking (the logs).
        assertEquals(List(3) { true }, bare("Stop stop", "stop, stop, stop", "그만 그만"))
    }

    @Test fun notAStopWithMoreToIt() {
        // These keep their meaning: they stop what the screen does, or are not a stop at all.
        assertEquals(List(5) { false }, bare("stop navigation", "stop listening", "bus stop", "그만 가자", ""))
    }
}

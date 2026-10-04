package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechStopTest {
    private fun bare(vararg texts: String) = texts.map { SpeechStop.isBare(it) }

    @Test fun theWordAlone() {
        assertEquals(List(8) { true }, bare("Stop", "stop.", "Quiet", "shh", "멈춰", "그만", "스톱", "조용"))
        assertEquals(List(3) { true }, bare("Stop!", "  stop  ", "쉿"))
    }

    @Test fun notAStopWithMoreToIt() {
        // These keep their meaning: they stop what the screen does, or are not a stop at all.
        assertEquals(List(5) { false }, bare("stop navigation", "stop listening", "bus stop", "그만 가자", ""))
    }
}

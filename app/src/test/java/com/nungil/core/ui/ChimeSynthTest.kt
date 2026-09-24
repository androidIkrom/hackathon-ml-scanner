package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChimeSynthTest {
    private val rate = ChimeSynth.SAMPLE_RATE

    @Test fun lengthIsNotesPlusGaps() {
        val pcm = ChimeSynth.render(ChimeSynth.MIC_ON)
        val perNote = rate * ChimeSynth.NOTE_MS / 1000
        val gap = rate * ChimeSynth.GAP_MS / 1000
        assertEquals(2 * perNote + gap, pcm.size)
    }

    @Test fun startsAndEndsSilentSoItNeverClicks() {
        val pcm = ChimeSynth.render(ChimeSynth.MIC_OFF)
        assertEquals(0, pcm.first().toInt())
        assertTrue(abs(pcm.last().toInt()) < 200)
    }

    @Test fun staysSoft() {
        val peak = ChimeSynth.render(ChimeSynth.MIC_ON).maxOf { abs(it.toInt()) }
        assertTrue(peak <= (Short.MAX_VALUE * ChimeSynth.AMPLITUDE).toInt() + 1)
        assertTrue(peak > 1000)
    }

    @Test fun onRisesAndOffFalls() {
        assertTrue(ChimeSynth.MIC_ON[0] < ChimeSynth.MIC_ON[1])
        assertTrue(ChimeSynth.MIC_OFF[0] > ChimeSynth.MIC_OFF[1])
    }
}

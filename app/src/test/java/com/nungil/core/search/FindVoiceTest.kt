package com.nungil.core.search

import com.nungil.core.ui.Announcer
import com.nungil.core.ui.Notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindVoiceTest {
    private val left = Notice("find:LEFT", "Cup slightly left.", 0)
    private val farLeft = Notice("find:FAR_LEFT", "Cup on your left.", 0)
    private fun after(text: String, at: Long = 0) = at + Announcer.durationMs(text) + 1

    @Test fun aHeldSentenceIsSaidOnceQuiet() {
        val v = FindVoice()
        v.said(0, "Looking for Cup.")
        v.heard(left, 0)
        assertNull(v.next(100))
        assertEquals(left, v.next(after("Looking for Cup.")))
        assertNull(v.next(after("Looking for Cup.") + 100))
    }

    @Test fun aNewerSentenceReplacesTheHeldOne() {
        val v = FindVoice()
        v.said(0, "Looking for Cup.")
        v.heard(left, 0)
        v.heard(farLeft, 0)
        assertEquals(farLeft, v.next(after("Looking for Cup.")))
    }

    @Test fun pausingDropsTheHeldSentence() {
        // After "stop" and "start", the place said before the pause was said again, stale (review).
        val v = FindVoice()
        v.said(0, "Looking for Cup.")
        v.heard(left, 0)
        v.pause()
        v.said(5_000, "Looking for Cup.")
        assertNull(v.next(after("Looking for Cup.", 5_000)))
    }

    @Test fun aSentenceSaidOutsideIsNotTalkedOver() {
        val v = FindVoice()
        v.said(0, "Looking for Cup.")
        v.heard(left, 0)
        assertNull(v.next(500))
    }

    @Test fun theSameSentenceIsNotRepeatedWithinSixSeconds() {
        // In clock hours three zones of the frame are all "12 o'clock": it was said twice in 4 s (the logs).
        val v = FindVoice()
        v.heard(left, 0)
        assertEquals(left, v.next(0))
        v.heard(left, 4_000)
        assertNull(v.next(4_000))
    }

    @Test fun aHeldSentenceGoesStale() {
        // Held while the user talked, a place was said 5 s late (the logs): after 3 s it is dropped.
        val v = FindVoice()
        v.said(0, "Looking for My charger test one, the one on the desk by the window.")
        v.heard(left, 100)
        assertNull(v.next(FindVoice.STALE_MS + 200))
    }
}

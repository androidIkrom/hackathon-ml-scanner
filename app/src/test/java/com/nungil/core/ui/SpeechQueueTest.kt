package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechQueueTest {
    private var t = 0L
    private val q = SpeechQueue { t }

    @Test fun firstPhraseStartsAtOnce() {
        q.add("a chair")
        assertEquals("a chair", q.next())
        assertTrue(q.isSpeaking)
    }

    @Test fun quietOnlyWhenNothingIsSaidOrWaiting() {
        // A question has to be heard before the microphone opens for the answer.
        assertTrue(q.isQuiet)
        q.add("Seoul Station, 400 metres away. Say yes to go.")
        assertFalse(q.isQuiet)
        q.next()
        assertFalse(q.isQuiet)
        q.done()
        assertTrue(q.isQuiet)
    }

    @Test fun nothingNewWhileSpeaking() {
        q.add("one")
        q.add("two")
        q.next()
        assertNull(q.next())
    }

    @Test fun keepsAGapOfOneAndAHalfSeconds() {
        q.add("one")
        q.add("two")
        q.next()
        t = 1_000
        q.done()
        t = 2_000
        assertNull(q.next())
        t = 2_500
        assertEquals("two", q.next())
    }

    @Test fun dropsTheStalestWhenMoreThanThreeWait() {
        listOf("1", "2", "3", "4", "5").forEach(q::add)
        assertEquals(SpeechQueue.MAX_PENDING, q.pendingCount)
        assertEquals("3", q.next())
    }

    @Test fun nowClearsTheQueueAndSpeaksImmediately() {
        q.add("old")
        assertEquals("urgent", q.now("urgent"))
        assertEquals(0, q.pendingCount)
        assertEquals("urgent", q.lastSpoken)
    }

    @Test fun blankTextIsIgnored() {
        q.add("   ")
        assertNull(q.now(""))
        assertEquals(0, q.pendingCount)
        assertNull(q.next())
    }

    @Test fun finalBlocksTheQueueUntilItEnds() {
        q.add("before")
        assertEquals("summary", q.final("summary"))
        assertEquals(0, q.pendingCount)
        q.add("after")
        assertNull(q.next())
        assertFalse(q.finalFinished())
        t = 4_000
        q.done()
        assertTrue(q.finalFinished())
        assertFalse(q.finalFinished())
        t = 5_500
        assertEquals("after", q.next())
    }

    @Test fun finalGivesUpAfterTheSafetyLimit() {
        q.final("summary")
        t = SpeechQueue.SHUTDOWN_SAFETY_MS - 1
        assertFalse(q.finalFinished())
        t = SpeechQueue.SHUTDOWN_SAFETY_MS
        assertTrue(q.finalFinished())
        assertFalse(q.isSpeaking)
    }

    @Test fun aStuckEngineDoesNotBlockTheQueueForever() {
        q.add("one")
        q.add("two")
        q.next()
        t = SpeechQueue.SHUTDOWN_SAFETY_MS
        assertNull(q.next())
        t += SpeechQueue.GAP_MS
        assertEquals("two", q.next())
    }

    @Test fun remembersTheLastSentenceForRepeat() {
        assertNull(q.lastSpoken)
        q.add("a black laptop on your right")
        q.next()
        assertEquals("a black laptop on your right", q.lastSpoken)
    }

    @Test fun clearForgetsEverythingButTheLastSentence() {
        q.add("one")
        q.next()
        q.add("two")
        q.clear()
        assertFalse(q.isSpeaking)
        assertEquals(0, q.pendingCount)
        assertEquals("one", q.lastSpoken)
    }

    @Test fun doneWithoutSpeakingIsHarmless() {
        q.done()
        q.add("one")
        assertEquals("one", q.next())
    }

    @Test fun aTextReadAloudKeepsEveryLineAndShortPauses() {
        // The reader queued 31 lines and 3 were said: the queue kept only the last 3, with 1.5 s between them (2026-10-12).
        val lines = (1..31).map { "Line $it." }
        q.addLines(lines)
        val said = mutableListOf<String>()
        while (said.size < lines.size) {
            val next = q.next()
            if (next != null) {
                said += next
                q.done()
            }
            t += SpeechQueue.LINE_GAP_MS
        }
        assertEquals(lines, said)
    }

    @Test fun aVoiceInTheRoomPausesATextAndItGoesOnFromTheLineCut() {
        // Other voices held the app's sound, and the hold dropped the whole text: 27 lines queued, none said (2026-10-13).
        q.addLines(listOf("Line 1.", "Line 2.", "Line 3."))
        assertEquals("Line 1.", q.next())
        q.done()
        t += SpeechQueue.LINE_GAP_MS
        assertEquals("Line 2.", q.next())
        q.add("a chair ahead")
        assertTrue(q.reading)
        q.hold()
        assertFalse(q.isSpeaking)
        // The line cut is said again, then the rest; the announcement is stale.
        t += SpeechQueue.LINE_GAP_MS
        assertEquals("Line 2.", q.next())
        q.done()
        t += SpeechQueue.LINE_GAP_MS
        assertEquals("Line 3.", q.next())
        q.done()
        t += SpeechQueue.GAP_MS
        assertNull(q.next())
        assertFalse(q.reading)
    }

    @Test fun aStopRemembersTheLineItCut() {
        q.addLines(listOf("Line 1.", "Line 2.", "Line 3."))
        q.next()
        q.done()
        t += SpeechQueue.LINE_GAP_MS
        q.next()
        q.clear()
        assertEquals("Line 2.", q.cutLine)
        assertFalse(q.reading)
        // "Stopped." said after it does not forget where the text was.
        q.now("Stopped.")
        assertEquals("Line 2.", q.cutLine)
        // A new text starts afresh.
        q.addLines(listOf("Other."))
        assertNull(q.cutLine)
    }

    @Test fun aStopBetweenLinesRemembersTheNextOne() {
        q.addLines(listOf("Line 1.", "Line 2."))
        q.next()
        q.done()
        q.clear()
        assertEquals("Line 2.", q.cutLine)
    }

    @Test fun aHoldWithNoTextDropsAnnouncementsAsBefore() {
        q.add("a chair")
        q.add("a table")
        q.next()
        q.hold()
        assertEquals(0, q.pendingCount)
        assertNull(q.cutLine)
    }

    @Test fun anAnnouncementStillDropsTheStalest() {
        q.addLines(listOf("Line 1.", "Line 2."))
        repeat(SpeechQueue.MAX_PENDING + 2) { q.add("note $it") }
        assertEquals(2 + SpeechQueue.MAX_PENDING, q.pendingCount)
    }
}

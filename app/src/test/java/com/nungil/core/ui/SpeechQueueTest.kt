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
}

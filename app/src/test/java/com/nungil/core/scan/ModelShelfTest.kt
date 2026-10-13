package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.Closeable

class ModelShelfTest {
    private var now = 0L
    private val shelf = ModelShelf({ now }, keepMs = 30_000)
    private var built = 0

    private class Fake(val name: String) : Closeable {
        var closed = 0
        override fun close() {
            closed++
        }
    }

    private fun take(kind: String = "embedder", key: String = "") = shelf.take(kind, key) { Fake("$kind $key").also { built++ } }

    @Test fun aSecondScreenGetsTheSameModel() {
        // Live then Find: each built and closed its own embedder, a load next to running work (Task 7).
        val live = take()
        val find = take()
        assertSame(live.model, find.model)
        assertEquals(1, built)
    }

    @Test fun aModelGivenBackStaysForAWhileThenCloses() {
        val lease = take()
        lease.close()
        now += 29_000
        shelf.sweep()
        assertEquals(0, lease.model.closed)
        // Back within the keep time: no load.
        val again = take()
        assertSame(lease.model, again.model)
        again.close()
        now += 30_000
        shelf.sweep()
        assertEquals(1, lease.model.closed)
        assertEquals(1, built)
    }

    @Test fun aModelInUseIsNeverClosed() {
        val a = take()
        val b = take()
        a.close()
        now += 60_000
        shelf.sweep()
        assertEquals(0, b.model.closed)
    }

    @Test fun givingBackTwiceCountsOnce() {
        val a = take()
        val b = take()
        a.close()
        a.close()
        now += 60_000
        shelf.sweep()
        assertEquals(0, b.model.closed)
    }

    @Test fun anotherKeyOfTheSameKindReplacesAnIdleOne() {
        // Model or processor changed in Settings: the old detector goes before the new one loads.
        val gpu = take("detector", "lite2 GPU")
        gpu.close()
        val cpu = take("detector", "lite2 CPU")
        assertEquals(1, gpu.model.closed)
        assertNotSame(gpu.model, cpu.model)
    }

    @Test fun anotherKeyInUseIsLeftAlone() {
        // Walk's CPU detector while Live's is still closing.
        val live = take("detector", "lite2 GPU")
        take("detector", "walk CPU")
        assertEquals(0, live.model.closed)
    }

    @Test fun otherKindsAreNotTouched() {
        val embedder = take("embedder")
        embedder.close()
        take("segmenter")
        assertEquals(0, embedder.model.closed)
    }

    @Test fun closeAllClosesEveryModel() {
        val a = take("embedder")
        val b = take("segmenter")
        b.close()
        shelf.closeAll()
        assertEquals(1, a.model.closed)
        assertEquals(1, b.model.closed)
    }
}

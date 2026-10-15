package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FrameStatsTest {
    @Test fun aReportEveryFiveSeconds() {
        val stats = FrameStats()
        assertEquals(5_000L, FrameStats.EVERY_MS)
        // One frame a second, as on the emulator: no report before five seconds.
        for (t in 0L until 5_000L step 1_000L) assertNull(stats.add(t, 10, 150, 40))
        assertNotNull(stats.add(5_000, 10, 150, 40))
        assertNull(stats.add(6_000, 10, 150, 40))
    }

    @Test fun eachStepIsAveragedAndTheFramesASecondCounted() {
        val stats = FrameStats(everyMs = 500)
        stats.add(0, 10, 100, 20)
        stats.add(250, 20, 200, 40)
        val w = stats.add(500, 30, 300, 60)!!
        assertEquals(20L, w.convertMs)
        assertEquals(200L, w.detectMs)
        assertEquals(40L, w.afterMs)
        assertEquals(260L, w.totalMs)
        // 3 frames 250 ms apart: 4 a second.
        assertEquals(4f, w.fps, 0.01f)
    }

    @Test fun theFirstFrameIsReportedOnce() {
        val stats = FrameStats()
        stats.started(1_000)
        assertEquals(850L, stats.firstFrame(1_850))
        assertNull(stats.firstFrame(2_000))
    }
}

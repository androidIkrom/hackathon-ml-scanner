package com.nungil.core.ui

import com.nungil.contract.Buzz
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternsTest {
    @Test fun everyBuzzHasItsOwnPattern() {
        val shapes = Buzz.values().map { HapticPatterns.timings(it).toList() to HapticPatterns.amplitudes(it).toList() }
        assertEquals(Buzz.values().size, shapes.toSet().size)
    }

    @Test fun centredIsOneSixtyMillisecondPulse() =
        assertArrayEquals(longArrayOf(0, 60), HapticPatterns.timings(Buzz.CENTERED))

    @Test fun tapIsShort() = assertArrayEquals(longArrayOf(0, 20), HapticPatterns.timings(Buzz.TAP))

    @Test fun obstacleIsThreePulses() = assertEquals(3, HapticPatterns.pulses(Buzz.OBSTACLE))

    @Test fun foundIsTwoPulses() = assertEquals(2, HapticPatterns.pulses(Buzz.FOUND))

    @Test fun errorIsTheLongest() {
        val error = HapticPatterns.onMs(Buzz.ERROR)
        Buzz.values().filter { it != Buzz.ERROR }.forEach { assertTrue(it.name, HapticPatterns.onMs(it) < error) }
    }

    @Test fun doneRises() {
        val amps = HapticPatterns.amplitudes(Buzz.DONE).filter { it > 0 }
        assertEquals(amps.sorted(), amps)
        assertFalse(amps.toSet().size == 1)
    }

    @Test fun waveformsAreWellFormed() {
        for (kind in Buzz.values()) {
            val t = HapticPatterns.timings(kind)
            val a = HapticPatterns.amplitudes(kind)
            assertEquals(kind.name, t.size, a.size)
            assertEquals(kind.name, 0L, t[0])
            assertTrue(kind.name, t.size % 2 == 0)
            for (i in t.indices) {
                if (i % 2 == 1) {
                    assertTrue(kind.name, t[i] > 0 && a[i] in 1..255)
                } else {
                    assertEquals(kind.name, 0, a[i])
                }
            }
        }
    }

    @Test fun beeperRestartsAtOnceWhenStartingOrSpeedingUp() {
        assertTrue(BeepPolicy.restartNow(oldIntervalMs = 0, newIntervalMs = 800))
        assertTrue(BeepPolicy.restartNow(oldIntervalMs = 800, newIntervalMs = 400))
        assertFalse(BeepPolicy.restartNow(oldIntervalMs = 400, newIntervalMs = 800))
        assertFalse(BeepPolicy.restartNow(oldIntervalMs = 400, newIntervalMs = 400))
    }

    @Test fun beeperNeverGoesFasterThanItsOwnLength() {
        assertEquals(BeepPolicy.MIN_INTERVAL_MS, BeepPolicy.clamp(10))
        assertEquals(1_000L, BeepPolicy.clamp(1_000))
        assertTrue(BeepPolicy.MIN_INTERVAL_MS > BeepPolicy.BEEP_MS)
    }
}

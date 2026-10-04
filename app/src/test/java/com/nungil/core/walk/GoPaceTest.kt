package com.nungil.core.walk

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class GoPaceTest {
    private fun walk(p: GoPace, fromS: Int, toS: Int, mps: Float, startM: Float = 0f) {
        for (s in fromS..toS) p.add(s * 1000L, startM + (s - fromS) * mps)
    }

    @Test fun oneMetreASecondUntilThereIsEnoughWalking() {
        val p = GoPace()
        walk(p, 0, 10, 1.4f)
        assertEquals(1.0f, p.speedMps, 0.001f)
        assertEquals(300f, p.secondsFor(300f), 0.5f)
    }

    @Test fun theWalkersOwnSpeed() {
        val p = GoPace()
        walk(p, 0, 60, 1.4f)
        assertEquals(1.4f, p.speedMps, 0.05f)
    }

    @Test fun stopsDoNotLowerIt() {
        val p = GoPace()
        walk(p, 0, 40, 1.2f)
        for (s in 41..100) p.add(s * 1000L, 48f) // waiting at a crossing
        assertEquals(1.2f, p.speedMps, 0.05f)
    }

    @Test fun standingJitterDoesNotCountAsWalking() {
        val p = GoPace()
        // 8 m forward and back every second is not 10 m in 20 s of walking.
        for (s in 0..100) p.add(s * 1000L, if (s % 2 == 0) 0f else 8f)
        assertEquals(1.0f, p.speedMps, 0.001f)
    }

    @Test fun standingWithinTheGpsAccuracyDoesNotSetTheSpeed() {
        // Fixes good to 8 m wander up to 14 m apart while the walker stands still.
        val wander = listOf(0f, 6f, -5f, 7f, -4f, 5f, -6f)
        val p = GoPace()
        for (s in 0..119) p.add(s * 1000L, wander[s % wander.size], accuracyM = 8f)
        assertEquals(1.0f, p.speedMps, 0.001f)
    }

    @Test fun walkingIsMeasuredWithPoorFixesToo() {
        val p = GoPace()
        for (s in 0..60) p.add(s * 1000L, s * 1.4f, accuracyM = 8f)
        assertEquals(1.4f, p.speedMps, 0.05f)
    }

    @Test fun keptBetweenHalfAndTwoMetresASecond() {
        val slow = GoPace().also { walk(it, 0, 200, 0.35f) }
        assertEquals(0.5f, slow.speedMps, 0.001f)
        val fast = GoPace().also { walk(it, 0, 60, 3f) }
        assertEquals(2.0f, fast.speedMps, 0.001f)
    }

    @Test fun aNewRouteKeepsTheSpeed() {
        val p = GoPace()
        walk(p, 0, 60, 1.4f)
        p.restart()
        p.add(61_000, 0f)
        assertEquals(1.4f, p.speedMps, 0.05f)
    }

    @Test fun timeAndProgressSentences() {
        assertEquals("less than a minute", RoutePhrases.timeLeft(44f, Lang.EN))
        assertEquals("about 1 minute", RoutePhrases.timeLeft(80f, Lang.EN))
        assertEquals("about 6 minutes", RoutePhrases.timeLeft(350f, Lang.EN))
        assertEquals("about 1 hour 20 minutes", RoutePhrases.timeLeft(4800f, Lang.EN))
        assertEquals("1분도 안 걸려요", RoutePhrases.timeLeft(30f, Lang.KO))
        assertEquals("약 6분", RoutePhrases.timeLeft(350f, Lang.KO))
        assertEquals("약 1시간 20분", RoutePhrases.timeLeft(4800f, Lang.KO))
        assertEquals("350 metres left, about 6 minutes.", RoutePhrases.progress(350f, 350f, false, Lang.EN))
        assertEquals("350 metres in a straight line, about 6 minutes.", RoutePhrases.progress(350f, 350f, true, Lang.EN))
        assertEquals("350미터 남았어요, 약 6분.", RoutePhrases.progress(350f, 350f, false, Lang.KO))
        assertEquals("직선으로 350미터, 약 6분.", RoutePhrases.progress(350f, 350f, true, Lang.KO))
    }
}

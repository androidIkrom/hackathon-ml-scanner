package com.nungil.core.scan

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionTest {
    private val chair = ScanSession.Seen("chair", 0.5f, null)
    private fun ScanSession.frame(t: Long, heading: Float?, vararg seen: ScanSession.Seen) =
        onFrame(t, heading, 60f, Facing.BACK, seen.toList())

    @Test fun tunedNumbers() {
        assertEquals(60_000L, ScanSession.TIMEOUT_MS)
        assertEquals(60f, SpinGuard.MAX_DEG_PER_SEC)
        assertEquals(5_000L, SpinGuard.REPEAT_MS)
    }

    @Test fun aFullTurnFinishesTheScan() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        for (i in 0..34) assertFalse(s.frame(i * 200L, i * 10f).done)
        val last = s.frame(35 * 200L, 350f)
        assertTrue(last.done)
        assertEquals(100, last.coveragePercent)
        assertTrue(last.phrases.isEmpty())
    }

    @Test fun fullScanStopsAfterSixtySeconds() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        assertFalse(s.frame(0L, 0f).done)
        assertFalse(s.frame(59_999L, 0f).done)
        assertTrue(s.frame(60_000L, 0f).done)
    }

    @Test fun turningTooFastSaysSlowDown() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.KO)
        s.frame(0L, 0f)
        assertEquals(listOf("천천히 돌아 주세요."), s.frame(200L, 30f).phrases)
    }

    @Test fun slowDownIsNotRepeatedWithinFiveSeconds() {
        val g = SpinGuard()
        g.update(0L, 0f)
        assertTrue(g.update(200L, 30f))
        assertFalse(g.update(400L, 60f))
        assertFalse(g.update(5_000L, 60f))
        assertTrue(g.update(5_200L, 90f))
    }

    @Test fun turningSlowlyIsFine() {
        val g = SpinGuard()
        g.update(0L, 0f)
        assertFalse(g.update(100L, 5f))
    }

    @Test fun missingCompassSwitchesAFullScanToLive() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        assertTrue(s.frame(0L, null, chair).phrases.isEmpty())
        assertTrue(s.frame(1_400L, null, chair).phrases.isEmpty())
        val step = s.frame(1_500L, null, chair)
        assertEquals(listOf("Compass not available. Switching to live scan."), step.phrases)
        assertEquals(listOf("a chair ahead"), step.inView.map { it.text })
        assertTrue(s.announcesLive)
        val later = s.frame(90_000L, null, chair)
        assertTrue(later.phrases.isEmpty())
        assertFalse(later.done)
        assertEquals(100, s.finish().coveragePercent)
    }

    @Test fun liveScanAnnouncesEachObjectOnce() {
        val s = ScanSession(ScanMode.LIVE, 0L, Lang.EN)
        assertTrue(s.frame(0L, 0f, chair).inView.isEmpty())
        assertTrue(s.frame(200L, 0f, chair).inView.isEmpty())
        assertEquals(listOf("a chair ahead"), s.frame(400L, 0f, chair).inView.map { it.text })
    }

    @Test fun fullScanDoesNotAnnounceLive() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        repeat(4) {
            val step = s.frame(it * 200L, 0f, chair)
            assertTrue(step.phrases.isEmpty() && step.inView.isEmpty())
        }
    }

    @Test fun summaryIsRelativeToWhereTheUserFacesAtTheEnd() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        s.frame(0L, 100f, chair)
        s.frame(200L, 100f, chair)
        s.frame(400L, 100f, chair)
        s.frame(3_000L, 190f)
        val result = s.finish()
        assertEquals("I scanned 5 percent of the room. Around you: a chair on your left.", result.summary)
        assertEquals(5, result.coveragePercent)
        assertEquals(270f, result.objects.single().angle, 0.01f)
    }

    @Test fun koreanSummaryFromFrames() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.KO)
        val chairs = arrayOf(
            ScanSession.Seen("chair", 0.45f, ColorName.BLUE),
            ScanSession.Seen("chair", 0.5f, ColorName.BLUE),
            ScanSession.Seen("chair", 0.55f, ColorName.BLUE),
        )
        for (i in 0..35) {
            val heading = i * 10f
            if (heading == 0f || heading == 10f || heading == 20f) {
                s.frame(i * 200L, heading, *chairs)
            } else if (heading == 90f || heading == 100f || heading == 110f) {
                s.frame(i * 200L, heading, ScanSession.Seen("laptop", 0.5f, ColorName.BLACK))
            } else {
                s.frame(i * 200L, heading)
            }
        }
        s.frame(36 * 200L, 0f)
        assertEquals("앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요.", s.finish().summary)
    }

    @Test fun aRecognisedPersonHidesThePlainPersonBesideIt() {
        val s = ScanSession(ScanMode.LIVE, 0L, Lang.EN)
        repeat(3) {
            s.frame(it * 200L, 0f, ScanSession.Seen("Ali", 0.5f, null, isName = true, wasPerson = true), ScanSession.Seen("person", 0.55f, null))
        }
        assertEquals("Around you: Ali ahead.", s.finish().summary)
    }

    @Test fun logKeepsTheLast200Lines() {
        val log = ScanLogState()
        repeat(205) { log.add("line $it") }
        log.add("  ")
        assertEquals(200, log.lines().size)
        assertEquals("line 5", log.lines().first())
        assertTrue(log.text().endsWith("line 204"))
    }

    @Test fun confirmedObjectsInViewAreReportedEveryFrame() {
        // The announcer decides what is said; the scan reports what is in view, every frame, with a stable key.
        val s = ScanSession(ScanMode.LIVE, 0L, Lang.EN)
        repeat(3) { s.frame(it * 200L, 0f, chair) }
        val ahead = s.frame(600L, 0f, chair).inView.single()
        assertEquals("a chair ahead", ahead.text)
        assertEquals(0f, ahead.bearing, 1f)
        // Turned 30° to the right: the same chair, now on the left.
        val turned = s.frame(800L, 30f, ScanSession.Seen("chair", 0.0f, null)).inView.single()
        assertEquals(ahead.key, turned.key)
        assertEquals(-30f, turned.bearing, 3f)
        assertEquals("a chair on your left", turned.text)
        assertTrue(s.frame(1_000L, 30f).inView.isEmpty())
    }
}

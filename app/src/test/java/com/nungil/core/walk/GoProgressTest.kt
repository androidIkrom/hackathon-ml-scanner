package com.nungil.core.walk

import org.junit.Assert.assertEquals
import org.junit.Test

class GoProgressTest {
    private val never = Long.MIN_VALUE / 2

    @Test fun everyMinuteAfterTheStart() {
        val g = GoProgress().also { it.start(0) }
        assertEquals(GoProgress.Verdict.NOT_YET, g.check(59_999, null, never, false))
        assertEquals(GoProgress.Verdict.SAY, g.check(60_000, null, never, false))
        g.said(60_000)
        assertEquals(GoProgress.Verdict.NOT_YET, g.check(119_000, null, never, false))
        assertEquals(GoProgress.Verdict.SAY, g.check(120_000, null, never, false))
    }

    @Test fun putOffNotDropped() {
        val g = GoProgress().also { it.start(0) }
        assertEquals(GoProgress.Verdict.TURN_NEAR, g.check(60_000, 25f, never, false))
        assertEquals(GoProgress.Verdict.JUST_SPOKE, g.check(61_000, 30f, 50_000, false))
        assertEquals(GoProgress.Verdict.REROUTING, g.check(66_000, 30f, never, true))
        assertEquals(GoProgress.Verdict.SAY, g.check(66_000, 30f, 50_000, false))
    }

    @Test fun anAnswerToHowFarCountsAsAnUpdate() {
        val g = GoProgress().also { it.start(0) }
        g.said(40_000)
        assertEquals(GoProgress.Verdict.NOT_YET, g.check(60_000, null, never, false))
        assertEquals(GoProgress.Verdict.SAY, g.check(100_000, null, never, false))
    }

    @Test fun quietAndOff() {
        assertEquals(GoProgress.Verdict.OFF, GoProgress().check(60_000, null, never, false))
        val g = GoProgress(quiet = true).also { it.start(0) }
        assertEquals(GoProgress.Verdict.QUIET, g.check(60_000, null, never, false))
        g.quiet = false
        assertEquals(GoProgress.Verdict.SAY, g.check(60_000, null, never, false))
        g.stop()
        assertEquals(GoProgress.Verdict.OFF, g.check(120_000, null, never, false))
    }
}

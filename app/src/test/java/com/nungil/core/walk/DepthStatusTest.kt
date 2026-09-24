package com.nungil.core.walk

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepthStatusTest {
    private fun run(s: DepthStatus, from: Long, to: Long, working: Boolean, dark: Boolean = false): List<Alert?> =
        (from until to step 100L).map { s.update(it, working, dark, Lang.EN) }

    @Test fun quietWhileDepthWorksOrIsBrieflyMissing() {
        val s = DepthStatus()
        assertEquals(listOf<Alert?>(null), run(s, 0, 5_000, working = true).distinct())
        assertEquals(listOf<Alert?>(null), run(s, 5_000, 7_900, working = false).distinct())
    }

    @Test fun tooDarkIsSaidAndStaysACandidate() {
        val s = DepthStatus()
        val alerts = run(s, 0, 10_000, working = false, dark = true)
        assertNull(alerts[29])
        val dark = alerts.drop(30).distinct()
        assertEquals(1, dark.size)
        assertEquals(AlertKind.DEPTH, dark[0]!!.kind)
        assertEquals(WalkPhrases.tooDark(Lang.EN), dark[0]!!.text)
    }

    @Test fun remindsEveryMinute() {
        val s = DepthStatus()
        val keys = run(s, 0, 130_000, working = false, dark = true).mapNotNull { it?.key }.distinct()
        assertEquals(listOf("depth:dark:0", "depth:dark:1", "depth:dark:2"), keys)
    }

    @Test fun darkStaysDarkWhenTheReasonFlickers() {
        val s = DepthStatus()
        run(s, 0, 1_000, working = false, dark = true)
        val later = run(s, 1_000, 5_000, working = false, dark = false).filterNotNull().map { it.text }.distinct()
        assertEquals(listOf(WalkPhrases.tooDark(Lang.EN)), later)
    }

    @Test fun measuringAgainIsSaidOnceDepthIsSteady() {
        val s = DepthStatus()
        run(s, 0, 9_000, working = false)
        // one frame of depth is not enough
        assertNull(s.update(9_000, true, false, Lang.EN))
        run(s, 9_100, 9_500, working = false)
        val back = run(s, 10_000, 16_000, working = true)
        assertNull(back[14])
        assertEquals("depth:back", back[15]?.key)
        assertNull(back.last())
        // not said again without a new loss
        assertEquals(listOf<Alert?>(null), run(s, 16_000, 24_000, working = true).distinct())
    }

    @Test fun noReasonWaitsLongerThanTooDark() {
        val s = DepthStatus()
        val alerts = run(s, 0, 9_000, working = false)
        assertNull(alerts[79])
        assertEquals("depth:lost:0", alerts[80]?.key)
        assertEquals(WalkPhrases.depthLost(Lang.EN), alerts[80]?.text)
    }

    @Test fun noMeasuringAgainWithoutALossFirst() {
        val s = DepthStatus()
        run(s, 0, 2_000, working = false)
        assertEquals(listOf<Alert?>(null), run(s, 2_000, 10_000, working = true).distinct())
    }
}

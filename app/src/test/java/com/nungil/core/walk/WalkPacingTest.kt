package com.nungil.core.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkPacingTest {
    private val wall3 = Alert(AlertKind.HAZARD, "wall:ahead:3", "Wall ahead, 3 steps.", ahead = true)
    private val wall1 = Alert(AlertKind.HAZARD, "wall:ahead:1", "Wall ahead, 1 step.", urgent = true, ahead = true)
    private val right = Alert(AlertKind.HAZARD, "wall:RIGHT", "Obstacle on your right, 2 steps.")
    private val clear = Alert(AlertKind.CLEAR, "clear", "Nothing close ahead.")

    @Test fun whatIsAheadIsSaidBeforeTheSides() {
        val alerts = WalkAlerts()
        assertEquals(wall3, alerts.choose(0, listOf(right, wall3)))
        assertEquals(right, alerts.choose(100, listOf(right, wall3)))
    }

    @Test fun aSideIsNotSaidWhileSomethingIsBeingSaidAndIsNotLostEither() {
        // The logs: "Obstacle on your right" was heard 6 s late, behind three queued sentences.
        val pacing = WalkPacing()
        val alerts = WalkAlerts()
        val first = alerts.choose(0, listOf(wall3, right)) { pacing.allows(0, it) }!!
        pacing.said(0, first)
        assertNull(alerts.choose(500, listOf(wall3, right)) { pacing.allows(500, it) })
        val later = WalkPacing.durationMs(wall3.text) + 1
        assertEquals(right, alerts.choose(later, listOf(wall3, right)) { pacing.allows(later, it) })
    }

    @Test fun theNewestAboutAheadCutsInAtOnce() {
        val pacing = WalkPacing()
        pacing.said(0, wall3)
        assertTrue(pacing.allows(100, wall1))
        assertTrue(pacing.allows(100, clear))
        assertFalse(pacing.allows(100, right))
        pacing.said(100, clear)
        assertTrue(pacing.allows(200, wall3))
        assertFalse(pacing.allows(200, right))
        assertTrue(pacing.allows(100 + WalkPacing.durationMs(clear.text), right))
    }

    @Test fun koreanTakesLongerPerCharacter() {
        assertTrue(WalkPacing.durationMs("앞에 벽이 있어요, 두 걸음.") > WalkPacing.durationMs("Wall ahead, 2 st."))
    }
}

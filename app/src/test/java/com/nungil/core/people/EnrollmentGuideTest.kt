package com.nungil.core.people

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentGuideTest {
    private val g = EnrollmentGuide()

    private fun fill(yaw: Float, pitch: Float) {
        repeat(g.samplesPerPose) {
            assertTrue("pose ${g.pose} should accept yaw=$yaw pitch=$pitch", g.accepts(yaw, pitch))
            g.add(yaw)
        }
    }

    @Test fun twentySamplesByDefault() {
        assertEquals(20, g.total)
        assertEquals(Pose.STRAIGHT, g.pose)
        assertEquals(0, g.percent())
    }

    @Test fun straightGate() {
        assertTrue(g.accepts(9f, -9f))
        assertFalse(g.accepts(10f, 0f))
        assertFalse(g.accepts(0f, -10f))
    }

    @Test fun addReportsPoseChange() {
        repeat(3) { assertFalse(g.add(0f)) }
        assertTrue(g.add(0f))
        assertEquals(Pose.LEFT, g.pose)
        assertEquals(20, g.percent())
    }

    @Test fun sidesMustBeOpposite() {
        fill(0f, 0f)
        assertFalse(g.accepts(20f, 0f)) // must be more than 20
        fill(-25f, 0f)                  // first side learned as negative yaw
        assertEquals(Pose.RIGHT, g.pose)
        assertFalse(g.accepts(-30f, 0f))
        assertTrue(g.accepts(30f, 0f))
    }

    @Test fun sidesWorkTheOtherWayRound() {
        fill(0f, 0f)
        fill(25f, 0f)
        assertFalse(g.accepts(25f, 0f))
        assertTrue(g.accepts(-25f, 0f))
    }

    @Test fun upAndDown() {
        fill(0f, 0f)
        fill(25f, 0f)
        fill(-25f, 0f)
        assertEquals(Pose.UP, g.pose)
        assertFalse(g.accepts(0f, 12f))
        assertTrue(g.accepts(0f, 13f))
        fill(0f, 15f)
        assertEquals(Pose.DOWN, g.pose)
        assertFalse(g.accepts(0f, 15f))
        fill(0f, -15f)
        assertTrue(g.isDone)
        assertNull(g.pose)
        assertEquals(100, g.percent())
        assertFalse(g.accepts(0f, 0f))
        assertFalse(g.add(0f))
    }

    @Test fun phrases() {
        assertEquals("Look straight at the phone.", EnrollPhrases.prompt(Pose.STRAIGHT, Lang.EN))
        assertEquals("이제 반대쪽으로 돌려 주세요.", EnrollPhrases.prompt(Pose.RIGHT, Lang.KO))
        assertEquals("40퍼센트", EnrollPhrases.percent(40, Lang.KO))
        assertEquals("All done. I will remember Ali.", EnrollPhrases.done("Ali", Lang.EN))
        assertEquals("다 됐어요. 민준을 기억할게요.", EnrollPhrases.done("민준", Lang.KO))
        assertEquals("다 됐어요. 지아를 기억할게요.", EnrollPhrases.done("지아", Lang.KO))
    }
}

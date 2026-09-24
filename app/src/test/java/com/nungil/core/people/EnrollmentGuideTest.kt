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
        val p = g.pose
        while (g.pose == p) {
            assertTrue("pose ${g.pose} should accept yaw=$yaw pitch=$pitch", g.accepts(yaw, pitch))
            g.add(yaw, pitch)
        }
    }

    @Test fun sevenSamplesInOneSweep() {
        assertEquals(7, g.total)
        assertEquals(Pose.STRAIGHT, g.pose)
        assertEquals(0, g.percent())
    }

    @Test fun straightGate() {
        assertTrue(g.accepts(9f, -9f))
        assertFalse(g.accepts(10f, 0f))
        assertFalse(g.accepts(0f, -10f))
    }

    @Test fun threeStraightThenOneEach() {
        repeat(2) { assertEquals(Pose.STRAIGHT, g.add(0f, 0f)) }
        assertEquals(Pose.STRAIGHT, g.pose)
        assertEquals(Pose.STRAIGHT, g.add(0f, 0f))
        assertEquals(Pose.LEFT, g.pose)
        assertNull(g.add(0f, 0f)) // straight is full
        assertEquals(Pose.LEFT, g.add(25f, 0f))
        assertEquals(Pose.RIGHT, g.pose)
    }

    @Test fun oneSweepInAnyOrderFinishes() {
        // Straight, then right, left, up, down in one movement; the first side seen becomes LEFT.
        listOf(0f to 0f, 0f to 0f, 0f to 0f, -30f to 0f, 30f to 0f, 0f to 15f, 0f to -15f).forEach { (yaw, pitch) ->
            assertTrue(g.add(yaw, pitch) != null)
        }
        assertTrue(g.isDone)
        assertEquals(100, g.percent())
        assertFalse(g.accepts(0f, 0f))
        assertNull(g.add(0f, 0f))
    }

    @Test fun posesReachedEarlyAreKept() {
        // Head goes up before the sides are done: UP is still taken.
        repeat(3) { g.add(0f, 0f) }
        assertEquals(Pose.UP, g.add(0f, 15f))
        assertEquals(Pose.LEFT, g.pose)
        assertNull(g.add(0f, 15f)) // up is full
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
    }

    @Test fun phrases() {
        assertEquals("Look straight at the phone.", EnrollPhrases.prompt(Pose.STRAIGHT, Lang.EN))
        assertEquals("이제 반대쪽으로 돌려 주세요.", EnrollPhrases.prompt(Pose.RIGHT, Lang.KO))
        assertEquals("40퍼센트", EnrollPhrases.percent(40, Lang.KO))
        assertEquals("휴대폰을 똑바로 본 다음, 고개를 오른쪽, 왼쪽, 위, 아래로 천천히 돌려 주세요.", EnrollPhrases.sweep(Lang.KO))
        assertEquals("민준 님 얼굴을 등록할게요.", EnrollPhrases.start("민준", Lang.KO))
        assertEquals("All done. I will remember Ali.", EnrollPhrases.done("Ali", Lang.EN))
        assertEquals("다 됐어요. 민준을 기억할게요.", EnrollPhrases.done("민준", Lang.KO))
        assertEquals("다 됐어요. 지아를 기억할게요.", EnrollPhrases.done("지아", Lang.KO))
    }

    @Test fun hintsSayWhatToChange() {
        assertNull(g.hint(100, 0f, 0f))
        assertEquals(PoseHint.CLOSER, g.hint(40, 0f, 0f))
        assertEquals(PoseHint.REPEAT, g.hint(100, 15f, 0f))
        fill(0f, 0f)
        // One side: 20 < |yaw| <= 35 is taken.
        assertEquals(PoseHint.MORE, g.hint(100, 15f, 0f))
        assertEquals(PoseHint.LESS, g.hint(100, -50f, 0f))
        assertNull(g.hint(100, -30f, 0f))
        fill(-30f, 0f)
        // The other side must have the opposite sign.
        assertEquals(PoseHint.OTHER_SIDE, g.hint(100, -30f, 0f))
        assertEquals(PoseHint.LESS, g.hint(100, 45f, 0f))
        assertNull(g.hint(100, 30f, 0f))
        fill(30f, 0f)
        // Up: 12 < pitch <= 25.
        assertEquals(PoseHint.MORE, g.hint(100, 0f, 5f))
        assertEquals(PoseHint.LESS, g.hint(100, 0f, 30f))
        assertNull(g.hint(100, 0f, -20f)) // down is missing too, so it is taken early
        fill(0f, 20f)
        // Down: -25 <= pitch < -12.
        assertEquals(PoseHint.MORE, g.hint(100, 0f, -5f))
        assertEquals(PoseHint.LESS, g.hint(100, 0f, -30f))
        assertNull(g.hint(100, 0f, -20f))
    }

    @Test fun hintPhrases() {
        assertEquals("Turn a little more.", EnrollPhrases.hint(PoseHint.MORE, Pose.LEFT, Lang.EN))
        assertEquals("Too far. Tilt back a little.", EnrollPhrases.hint(PoseHint.LESS, Pose.UP, Lang.EN))
        assertEquals("너무 많이 돌렸어요. 조금만 돌아와 주세요.", EnrollPhrases.hint(PoseHint.LESS, Pose.RIGHT, Lang.KO))
        assertEquals(EnrollPhrases.prompt(Pose.DOWN, Lang.KO), EnrollPhrases.hint(PoseHint.REPEAT, Pose.DOWN, Lang.KO))
    }
}

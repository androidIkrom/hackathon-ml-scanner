package com.nungil.core.items

import com.nungil.contract.Box
import com.nungil.contract.Detection
import com.nungil.contract.Lang
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ItemEnrollmentTest {
    private fun det(label: String, l: Float, t: Float, r: Float, b: Float) = Detection(label, 0.9f, Box(l, t, r, b))

    private fun at(deg: Double) = floatArrayOf(cos(Math.toRadians(deg)).toFloat(), sin(Math.toRadians(deg)).toFloat())

    /** The thing seen at [deg] on the embedding circle, with its middle at ([x], [y]) and [area] of the frame. */
    private fun view(deg: Double, x: Float = 0.5f, y: Float = 0.5f, area: Float = 0.1f) =
        ItemEnrollmentGuide.View(at(deg), x, y, area)

    @Test fun everySampleIsKeptAsItsSquareAndAsTheItemAlone() {
        val g = ItemEnrollmentGuide()
        fun both(deg: Double, x: Float = 0.5f, y: Float = 0.5f) = ItemEnrollmentGuide.View(at(deg), x, y, 0.1f, alone = at(deg + 90))
        repeat(3) { g.add(both(0.0)) }
        repeat(3) { g.add(both(20.0, x = 0.6f)) }
        repeat(3) { g.add(both(-20.0, x = 0.4f)) }
        repeat(3) { g.add(both(10.0, y = 0.6f)) }
        assertTrue(g.isDone)
        assertEquals(12, g.taken)
        assertEquals(24, g.samples.size)
        assertArrayEquals(at(0.0), g.samples[0], 1e-6f)     // the squares first
        assertArrayEquals(at(90.0), g.samples[12], 1e-6f)   // then the item alone
    }

    @Test fun aSampleWithoutItsItemAlonePicture() {
        val g = ItemEnrollmentGuide()
        g.add(ItemEnrollmentGuide.View(at(0.0), 0.5f, 0.5f, 0.1f, alone = at(90.0)))
        g.add(view(0.0))
        assertEquals(2, g.taken)
        assertEquals(3, g.samples.size)
    }

    @Test fun twelveSamplesInFourSteps() {
        val g = ItemEnrollmentGuide()
        assertEquals(12, g.total)
        assertEquals(ItemStep.STILL, g.step)
        repeat(2) { assertFalse(g.add(view(0.0))) }
        assertTrue(g.add(view(0.0)))
        assertEquals(ItemStep.LEFT, g.step)
        repeat(3) { g.add(view(20.0, x = 0.6f)) }   // phone moved left: the thing is to the right
        assertEquals(ItemStep.RIGHT, g.step)
        repeat(3) { g.add(view(-20.0, x = 0.4f)) }  // phone moved right: the thing is to the left
        assertEquals(ItemStep.UP, g.step)
        repeat(3) { g.add(view(10.0, y = 0.6f)) }   // phone lifted: the thing is lower
        assertTrue(g.isDone)
        assertNull(g.step)
        assertEquals(100, g.percent())
        assertEquals(12, g.samples.size)
        assertFalse(g.add(view(0.0)))
    }

    @Test fun standingStillDoesNotFinishTheMovedSteps() {
        val g = ItemEnrollmentGuide()
        repeat(3) { g.add(view(0.0)) }
        repeat(10) { assertFalse(g.add(view(0.0, x = 0.55f))) } // within 0.08 of where it was held
        assertEquals(ItemStep.LEFT, g.step)
        assertTrue(g.isTheItem(view(0.0, x = 0.55f)))
        assertFalse(g.hasMoved(view(0.0, x = 0.55f)))
    }

    @Test fun comingBackToTheStartIsNotRight() {
        // The logs: after LEFT, the phone going back to where it started counted as RIGHT.
        val g = ItemEnrollmentGuide()
        repeat(3) { g.add(view(0.0, x = 0.5f)) }
        repeat(3) { g.add(view(0.0, x = 0.62f)) }
        assertEquals(ItemStep.RIGHT, g.step)
        assertFalse(g.accepts(view(0.0, x = 0.5f)))
        assertFalse(g.accepts(view(0.0, x = 0.45f)))
        assertTrue(g.accepts(view(0.0, x = 0.42f)))
        assertEquals(0.08f, ItemEnrollmentGuide.MIN_SHIFT, 0f)
    }

    @Test fun upMeansTheThingIsLowerInTheFrame() {
        val g = ItemEnrollmentGuide()
        repeat(3) { g.add(view(0.0)) }
        repeat(3) { g.add(view(0.0, x = 0.6f)) }
        repeat(3) { g.add(view(0.0, x = 0.4f)) }
        assertEquals(ItemStep.UP, g.step)
        assertFalse(g.accepts(view(0.0, y = 0.45f)))
        assertTrue(g.accepts(view(0.0, y = 0.6f)))
    }

    @Test fun somethingElseIsNotASample() {
        val g = ItemEnrollmentGuide()
        assertTrue(g.accepts(view(0.0))) // the first view is the item
        repeat(3) { g.add(view(0.0)) }
        assertTrue(g.isTheItem(view(60.0, x = 0.6f)))   // cos 60 = 0.5: the item, moved
        assertFalse(g.isTheItem(view(70.0, x = 0.6f)))  // cos 70 = 0.34: the table, a jar
        // Looks alike but is two and a half times the size, or under 0.4 of it: the bottle behind, a corner of it.
        assertFalse(g.isTheItem(view(0.0, x = 0.6f, area = 0.26f)))
        assertTrue(g.isTheItem(view(0.0, x = 0.6f, area = 0.24f)))
        assertFalse(g.isTheItem(view(0.0, x = 0.6f, area = 0.03f)))
        assertFalse(g.add(view(70.0, x = 0.6f)))
        assertEquals(3, g.taken)
        assertEquals(0.4f, ItemEnrollmentGuide.SAME_MIN, 0f)
    }

    @Test fun laterSamplesAreComparedWithTheOnesHeldStill() {
        val g = ItemEnrollmentGuide()
        repeat(3) { g.add(view(0.0)) }
        repeat(3) { g.add(view(60.0, x = 0.6f)) }
        // 60 degrees from the last sample but 120 from the ones held still: drifted away.
        assertFalse(g.isTheItem(view(120.0, x = 0.4f)))
        assertTrue(g.isTheItem(view(-60.0, x = 0.4f)))
    }

    @Test fun restartForgetsEverything() {
        val g = ItemEnrollmentGuide()
        repeat(2) { g.add(view(0.0)) }
        assertFalse(g.accepts(view(90.0)))
        g.restart()
        assertEquals(0, g.taken)
        assertTrue(g.accepts(view(90.0)))
    }

    @Test fun cropRects() {
        assertArrayEquals(intArrayOf(64, 48, 320, 240), ItemCrop.rect(Box(0.1f, 0.1f, 0.5f, 0.5f), 640, 480))
        assertNull(ItemCrop.rect(Box(0.1f, 0.1f, 0.12f, 0.5f), 640, 480)) // 13 px wide
        assertArrayEquals(intArrayOf(0, 0, 640, 480), ItemCrop.rect(Box(-0.2f, -0.1f, 1.3f, 1.2f), 640, 480))
    }

    @Test fun atMostThreeCandidatesPreferredLabelFirst() {
        val dets = listOf(
            det("person", 0f, 0f, 1f, 1f),
            det("cup", 0.1f, 0.1f, 0.2f, 0.2f),
            det("chair", 0.0f, 0.0f, 0.6f, 0.6f),
            det("tv", 0.0f, 0.0f, 0.5f, 0.5f),
            det("book", 0.0f, 0.0f, 0.3f, 0.3f),
        )
        assertEquals(listOf(2, 3, 4), ItemCrop.candidates(dets, 640, 480))
        assertEquals(listOf(1, 2, 3), ItemCrop.candidates(dets, 640, 480, preferLabel = "cup"))
    }

    @Test fun phrases() {
        assertEquals("Hold the phone still.", ItemPhrases.prompt(ItemStep.STILL, Lang.EN))
        assertEquals("Now to the right, past where you started.", ItemPhrases.prompt(ItemStep.RIGHT, Lang.EN))
        assertEquals("Now lift the phone a little.", ItemPhrases.prompt(ItemStep.UP, Lang.EN))
        assertEquals("휴대폰을 왼쪽으로 조금 옮겨 주세요.", ItemPhrases.prompt(ItemStep.LEFT, Lang.KO))
        assertEquals("내 가방을 등록할게요. 물건을 향해 비추고 가만히 들어 주세요.", ItemPhrases.start("내 가방", Lang.KO))
        assertEquals("Learning my bag. Point the camera at it and hold still.", ItemPhrases.start("my bag", Lang.EN))
        assertEquals("다 됐어요. 열쇠를 기억할게요.", ItemPhrases.done("열쇠", Lang.KO))
        assertEquals("Great! Hold the phone still.", ItemPhrases.confirmed(Lang.EN))
        assertEquals("I lost it. Point the camera at it again.", ItemPhrases.lost(Lang.EN))
        assertEquals("맞는 물건을 향해 비추고 가만히 들어 주세요.", ItemPhrases.notThat(Lang.KO))
    }

    @Test fun aThingThatFillsTheViewIsTooNearToLearn() {
        // The logs: a towel at 53 to 93% of the frame took 155 s, a bottle and a box at 11 to 33% under a minute.
        assertFalse(ItemEnrollmentGuide.fillsView(0.33f))
        assertFalse(ItemEnrollmentGuide.fillsView(0.45f))
        assertTrue(ItemEnrollmentGuide.fillsView(0.53f))
    }
}

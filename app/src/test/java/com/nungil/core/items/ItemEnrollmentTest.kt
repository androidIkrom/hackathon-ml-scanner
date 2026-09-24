package com.nungil.core.items

import com.nungil.contract.Box
import com.nungil.contract.Detection
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemEnrollmentTest {
    private fun det(label: String, l: Float, t: Float, r: Float, b: Float) = Detection(label, 0.9f, Box(l, t, r, b))

    @Test fun twelveSamplesInThreeSteps() {
        val g = ItemEnrollmentGuide()
        assertEquals(12, g.total)
        assertEquals(ItemStep.STILL, g.step)
        repeat(3) { assertFalse(g.add(0.5f)) }
        assertTrue(g.add(0.5f))
        assertEquals(ItemStep.LEFT, g.step)
        repeat(4) { g.add(0.6f) }
        assertEquals(ItemStep.RIGHT, g.step)
        repeat(4) { g.add(0.4f) }
        assertTrue(g.isDone)
        assertNull(g.step)
        assertEquals(100, g.percent())
        assertFalse(g.add(0.5f))
    }

    @Test fun standingStillNeverFinishes() {
        val g = ItemEnrollmentGuide()
        repeat(4) { g.add(0.5f) }
        repeat(20) { assertFalse(g.add(0.53f)) } // within 0.08 of where it was held
        assertEquals(ItemStep.LEFT, g.step)
        assertEquals(4, g.taken)
    }

    @Test fun leftAndRightNeedTheItemToShiftTheRightWay() {
        val g = ItemEnrollmentGuide()
        g.add(0.4f); g.add(0.5f); g.add(0.5f); g.add(0.6f)
        assertEquals(0.5f, g.stillCenterX!!, 1e-6f)
        // Phone moved left: the item moves right in the image.
        assertFalse(g.accepts(0.42f))
        assertFalse(g.accepts(0.57f))
        assertTrue(g.accepts(0.59f))
        repeat(4) { assertTrue(g.accepts(0.6f)); g.add(0.6f) }
        // Phone moved right: the item moves left.
        assertEquals(ItemStep.RIGHT, g.step)
        assertFalse(g.accepts(0.6f))
        assertTrue(g.accepts(0.41f))
        assertEquals(0.08f, ItemEnrollmentGuide.MIN_SHIFT, 0f)
    }

    @Test fun centerPickPrefersTheMiddleAndIgnoresTinyBoxes() {
        val side = det("cup", 0.0f, 0.0f, 0.4f, 0.4f)         // area 0.16, centre far from the middle
        val middle = det("bottle", 0.4f, 0.4f, 0.6f, 0.6f)    // area 0.04 < 5%: ignored
        val bigMiddle = det("backpack", 0.3f, 0.3f, 0.7f, 0.7f)
        assertEquals(2, CenterPick.pick(listOf(side, middle, bigMiddle)))
        assertEquals(0, CenterPick.pick(listOf(side, middle)))
        assertEquals(-1, CenterPick.pick(listOf(middle)))
        assertEquals(-1, CenterPick.pick(emptyList()))
    }

    @Test fun kinds() {
        assertTrue(ItemKinds.allows(ItemKind.CAR, "truck"))
        assertFalse(ItemKinds.allows(ItemKind.CAR, "backpack"))
        assertTrue(ItemKinds.allows(ItemKind.OBJECT, "backpack"))
        assertFalse(ItemKinds.allows(ItemKind.OBJECT, "person"))
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
        assertEquals("Hold the phone still, pointing at it.", ItemPhrases.prompt(ItemStep.STILL, Lang.EN))
        assertEquals("휴대폰을 왼쪽으로 조금 옮겨 주세요.", ItemPhrases.prompt(ItemStep.LEFT, Lang.KO))
        assertEquals("내 가방을 등록할게요. 카메라로 비춰 주세요.", ItemPhrases.start("내 가방", Lang.KO))
        assertEquals("다 됐어요. 열쇠를 기억할게요.", ItemPhrases.done("열쇠", Lang.KO))
    }
}

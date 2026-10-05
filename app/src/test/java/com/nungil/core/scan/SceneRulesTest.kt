package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Detection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneRulesTest {
    @Test fun tunedNumbers() {
        assertEquals(0.35f, SceneRules.MIN_SCORE)
        assertEquals(0.5f, SceneRules.CENTER_SHARE)
    }

    @Test fun centreCropIsTheMiddleHalf() =
        assertArrayEquals(intArrayOf(120, 160, 240, 320), SceneRules.centerCrop(480, 640))

    @Test fun centreDetectionPrefersTheBestScoreAtTheCentre() {
        val chair = Detection("chair", 0.8f, Box(0.3f, 0.3f, 0.7f, 0.7f))
        val table = Detection("dining table", 0.9f, Box(0.1f, 0.4f, 0.9f, 0.9f))
        val side = Detection("cup", 0.99f, Box(0f, 0f, 0.2f, 0.2f))
        assertEquals("dining table", SceneRules.centerDetection(listOf(chair, table, side))?.label)
    }

    @Test fun noBoxAtTheCentre() =
        assertNull(SceneRules.centerDetection(listOf(Detection("cup", 0.9f, Box(0f, 0f, 0.2f, 0.2f)))))

    @Test fun nearestToCenter() {
        val list = listOf(
            Detection("person", 0.9f, Box(0f, 0f, 0.2f, 1f)),
            Detection("person", 0.9f, Box(0.4f, 0f, 0.62f, 1f)),
            Detection("chair", 0.9f, Box(0.45f, 0f, 0.55f, 1f)),
        )
        assertEquals(1, SceneRules.nearestToCenter(list, listOf(0, 1)))
        assertNull(SceneRules.nearestToCenter(list, emptyList()))
    }

    @Test fun aThingHalfOutOfTheFrameIsNotWhatIsInTheMiddle() {
        // Scan's "what is this" left out boxes cut by one side of the frame; FrameAnswers must too (review).
        val halfSeen = Detection("dining table", 0.9f, Box(0f, 0.2f, 0.6f, 0.9f))
        val cup = Detection("cup", 0.6f, Box(0.4f, 0.4f, 0.6f, 0.6f))
        assertEquals(cup, SceneRules.centerThing(listOf(halfSeen, cup)))
        assertEquals(null, SceneRules.centerThing(listOf(halfSeen)))
    }
}

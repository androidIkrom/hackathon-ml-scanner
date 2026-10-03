package com.nungil.search

import com.nungil.contract.Box
import com.nungil.contract.Detection
import com.nungil.contract.Facing
import com.nungil.contract.app.VisionFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LabelMatcherTest {
    private fun frame(vararg d: Detection) =
        VisionFrame(d.toList(), null, 640, 480, Facing.BACK, 0f, 65f, 0L, 10L)

    private fun det(label: String, score: Float, left: Float = 0.1f) = Detection(label, score, Box(left, 0.1f, left + 0.2f, 0.3f))

    @Test fun picksTheBestScoringDetectionWithTheLabel() {
        val m = LabelMatcher("cup")
        val best = det("cup", 0.8f, left = 0.6f)
        assertEquals(best.box, m.locate(frame(det("chair", 0.9f), det("cup", 0.5f), best)))
    }

    @Test fun missingTargetIsNull() = assertNull(LabelMatcher("cup").locate(frame(det("chair", 0.9f))))

    @Test fun emptyFrame() = assertNull(LabelMatcher("cup").locate(frame()))

    @Test fun labelMatchingIsFast() = assertFalse(LabelMatcher("cup").slow)
}

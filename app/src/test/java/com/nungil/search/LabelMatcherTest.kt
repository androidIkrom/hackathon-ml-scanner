package com.nungil.search

import com.nungil.contract.Box
import com.nungil.contract.Detection
import com.nungil.contract.Facing
import com.nungil.contract.app.VisionFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LabelMatcherTest {
    private fun frame(vararg d: Detection) =
        VisionFrame(d.toList(), null, 640, 480, Facing.BACK, 0f, 65f, 0L, 10L)

    private fun det(label: String, score: Float) = Detection(label, score, Box(0.1f, 0.1f, 0.3f, 0.3f))

    @Test fun picksTheBestScoringDetectionWithTheLabel() {
        val m = LabelMatcher("cup")
        assertEquals(2, m.find(frame(det("chair", 0.9f), det("cup", 0.5f), det("cup", 0.8f))))
    }

    @Test fun missingTargetIsMinusOne() = assertEquals(-1, LabelMatcher("cup").find(frame(det("chair", 0.9f))))

    @Test fun emptyFrame() = assertEquals(-1, LabelMatcher("cup").find(frame()))

    @Test fun labelMatchingIsFast() = assertFalse(LabelMatcher("cup").slow)
}

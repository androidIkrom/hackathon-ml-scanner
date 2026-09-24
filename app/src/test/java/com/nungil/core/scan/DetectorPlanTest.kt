package com.nungil.core.scan

import com.nungil.contract.Compute
import com.nungil.contract.ModelChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorPlanTest {
    @Test fun modelFiles() {
        assertEquals("ssd-mobilenet-v2.tflite", DetectorPlan.modelFile(ModelChoice.LIGHT))
        assertEquals("efficientdet-lite0.tflite", DetectorPlan.modelFile(ModelChoice.FAST))
        assertEquals("efficientdet-lite2.tflite", DetectorPlan.modelFile(ModelChoice.ACCURATE))
    }

    @Test fun gpuFallsBackToCpu() =
        assertEquals(listOf(Compute.GPU, Compute.CPU), DetectorPlan.delegateOrder(Compute.GPU, "efficientdet-lite2.tflite"))

    @Test fun cpuStaysOnCpu() =
        assertEquals(listOf(Compute.CPU), DetectorPlan.delegateOrder(Compute.CPU, "efficientdet-lite2.tflite"))

    @Test fun int8ModelNeverRunsOnTheGpu() =
        assertEquals(listOf(Compute.CPU), DetectorPlan.delegateOrder(Compute.GPU, DetectorPlan.WALK_MODEL_FILE))

    @Test fun tenResultsAndA64PixelTrial() {
        assertEquals(10, DetectorPlan.MAX_RESULTS)
        assertEquals(64, DetectorPlan.TRIAL_SIZE_PX)
    }

    @Test fun inferenceIsReportedEveryThirtyFrames() {
        val stats = InferenceStats()
        assertEquals(30, InferenceStats.EVERY_FRAMES)
        repeat(29) { assertNull(stats.add(10)) }
        assertEquals(20L, stats.add(310))
        assertNull(stats.add(10))
    }
}

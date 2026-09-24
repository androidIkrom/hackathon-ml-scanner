package com.nungil.core.scan

import com.nungil.contract.Compute
import com.nungil.contract.ModelChoice

/** Which model file to load and in which order to try the delegates. */
object DetectorPlan {
    const val MAX_RESULTS = 10

    /** Size of the throwaway image used to prove a delegate before trusting it. */
    const val TRIAL_SIZE_PX = 64

    /**
     * Walking model. Its input tensor is UINT8 while MediaPipe feeds the GPU FLOAT32, so on the GPU it
     * fails on the first frame ("ToTensorConverter: input data size does not match"). CPU only.
     */
    const val WALK_MODEL_FILE = "efficientdet-lite0-int8.tflite"

    fun modelFile(model: ModelChoice): String = when (model) {
        ModelChoice.LIGHT -> "ssd-mobilenet-v2.tflite"
        ModelChoice.FAST -> "efficientdet-lite0.tflite"
        ModelChoice.ACCURATE -> "efficientdet-lite2.tflite"
    }

    /** GPU is always followed by CPU as the fallback; int8 models never touch the GPU. */
    fun delegateOrder(compute: Compute, modelFile: String): List<Compute> = when {
        modelFile.contains("int8") -> listOf(Compute.CPU)
        compute == Compute.GPU -> listOf(Compute.GPU, Compute.CPU)
        else -> listOf(Compute.CPU)
    }
}

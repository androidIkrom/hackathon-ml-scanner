package com.nungil.contract

enum class Compute { CPU, GPU }

/** LIGHT = SSD MobileNet V2, FAST = EfficientDet-Lite0, ACCURATE = EfficientDet-Lite2, YOLO = Ultralytics YOLO26n. */
enum class ModelChoice { LIGHT, FAST, ACCURATE, YOLO }

/** Scan settings. Persisted by com.nungil.data.SettingsStore (A); edited by the Settings screen (I). */
data class ScanSettings(
    val facing: Facing = Facing.BACK,
    val compute: Compute = Compute.GPU,
    /**
     * YOLO26 by default (the user, 2026-10-13): on 42 desk frames it was surer than EfficientDet-Lite2 (0.93 against
     * 0.83 for the same things) and found more, at 155 ms a frame on the phone's GPU against 135 ms.
     */
    val model: ModelChoice = ModelChoice.YOLO,
    val minScore: Float = DEFAULT_MIN_SCORE,
    val speechOn: Boolean = true,
    val colorsOn: Boolean = true,
) {
    fun normalized(): ScanSettings = copy(minScore = minScore.coerceIn(MIN_SCORE_LOW, MIN_SCORE_HIGH))

    companion object {
        const val MIN_SCORE_LOW = 0.3f
        const val MIN_SCORE_HIGH = 0.7f

        /** At 0.5 the detector invents chairs and books. */
        const val DEFAULT_MIN_SCORE = 0.7f
    }
}

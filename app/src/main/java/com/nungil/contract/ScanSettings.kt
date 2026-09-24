package com.nungil.contract

enum class Compute { CPU, GPU }

/** LIGHT = SSD MobileNet V2, FAST = EfficientDet-Lite0, ACCURATE = EfficientDet-Lite2. */
enum class ModelChoice { LIGHT, FAST, ACCURATE }

/** Scan settings. Persisted by com.nungil.data.SettingsStore (A); edited by the Settings screen (I). */
data class ScanSettings(
    val facing: Facing = Facing.BACK,
    val compute: Compute = Compute.GPU,
    val model: ModelChoice = ModelChoice.ACCURATE,
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

package com.nungil.core.items

import com.nungil.contract.Detection

/** Chooses the box the user is pointing at while enrolling an item. */
object CenterPick {
    const val MIN_AREA = 0.05f

    /** Index of the box nearest the centre among those covering at least [minArea] of the frame; ties go to the bigger box. */
    fun pick(candidates: List<Detection>, minArea: Float = MIN_AREA): Int =
        candidates.indices
            .filter { candidates[it].box.area >= minArea }
            .minWithOrNull(
                compareBy<Int> { distanceToCentre(candidates[it]) }
                    .thenByDescending { candidates[it].box.area },
            ) ?: -1

    private fun distanceToCentre(d: Detection): Float {
        val dx = d.box.centerX - 0.5f
        val dy = d.box.centerY - 0.5f
        return dx * dx + dy * dy
    }
}

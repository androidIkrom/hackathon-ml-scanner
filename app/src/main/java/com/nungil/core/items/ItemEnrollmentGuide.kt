package com.nungil.core.items

/** The three steps of item enrolment. */
enum class ItemStep { STILL, LEFT, RIGHT }

/**
 * [samplesPerStep] samples while holding still, moved left and moved right (12 by default).
 * A LEFT or RIGHT sample counts only once the item's box centre has shifted at least [MIN_SHIFT] of the frame
 * width from where it was held still, so standing still cannot finish the enrolment. Moving the phone left
 * shifts the item right in the upright back-camera image, and the other way round.
 */
class ItemEnrollmentGuide(val samplesPerStep: Int = SAMPLES_PER_STEP) {
    private val counts = IntArray(ItemStep.entries.size)
    private var stillSum = 0f

    val total: Int = samplesPerStep * ItemStep.entries.size
    val taken: Int get() = counts.sum()

    /** The step being collected; null when done. */
    val step: ItemStep? get() = ItemStep.entries.firstOrNull { counts[it.ordinal] < samplesPerStep }
    val isDone: Boolean get() = step == null

    /** Where the item's centre was while held still (0..1 of the frame width); null before the first sample. */
    val stillCenterX: Float? get() = counts[ItemStep.STILL.ordinal].takeIf { it > 0 }?.let { stillSum / it }

    /** Whether a sample with the item's box centre at [centerX] (0..1) fits the current step. */
    fun accepts(centerX: Float): Boolean {
        val ref = stillCenterX
        return when (step) {
            null -> false
            ItemStep.STILL -> true
            ItemStep.LEFT -> ref != null && centerX - ref >= MIN_SHIFT
            ItemStep.RIGHT -> ref != null && ref - centerX >= MIN_SHIFT
        }
    }

    /** Records one sample taken at [centerX]. Returns true when it finished a step (or the whole enrolment). */
    fun add(centerX: Float): Boolean {
        val current = step ?: return false
        if (!accepts(centerX)) return false
        if (current == ItemStep.STILL) stillSum += centerX
        counts[current.ordinal]++
        return step != current
    }

    fun percent(): Int = taken * 100 / total

    companion object {
        const val SAMPLES_PER_STEP = 4

        /** How far (fraction of the frame width) the item must move for a LEFT or RIGHT sample. */
        const val MIN_SHIFT = 0.08f
    }
}

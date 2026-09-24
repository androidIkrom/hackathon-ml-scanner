package com.nungil.core.items

/** The three steps of item enrolment. */
enum class ItemStep { STILL, LEFT, RIGHT }

/** [samplesPerStep] samples while holding still, moved left and moved right (12 by default). */
class ItemEnrollmentGuide(val samplesPerStep: Int = SAMPLES_PER_STEP) {
    private val counts = IntArray(ItemStep.entries.size)

    val total: Int = samplesPerStep * ItemStep.entries.size
    val taken: Int get() = counts.sum()

    /** The step being collected; null when done. */
    val step: ItemStep? get() = ItemStep.entries.firstOrNull { counts[it.ordinal] < samplesPerStep }
    val isDone: Boolean get() = step == null

    /** Records one sample. Returns true when it finished a step (or the whole enrolment). */
    fun add(): Boolean {
        val current = step ?: return false
        counts[current.ordinal]++
        return step != current
    }

    fun percent(): Int = taken * 100 / total

    companion object {
        const val SAMPLES_PER_STEP = 4
    }
}

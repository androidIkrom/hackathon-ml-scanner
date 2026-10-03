package com.nungil.core.items

import com.nungil.core.people.FaceMatcher

/** The four steps of item enrolment: held still, then the phone moved left, right and up. */
enum class ItemStep { STILL, LEFT, RIGHT, UP }

/**
 * [samplesPerStep] samples held still, then with the phone moved left, right and up (12 by default).
 *
 * Every sample must still be the thing that was confirmed: it looks like the samples held still (cosine of at
 * least [SAME_MIN]) and is about their size ([SIZE_MIN]..[SIZE_MAX] of their area), so the bottle behind it or
 * the table is never learned as the item. Moving the phone left shifts the thing right in the frame, and so on;
 * a LEFT, RIGHT or UP sample counts only once the thing has shifted at least [MIN_SHIFT] of the frame that way
 * from where it was held still. Coming back to where it was after LEFT is not RIGHT (the logs).
 */
class ItemEnrollmentGuide(val samplesPerStep: Int = SAMPLES_PER_STEP) {
    /** The thing as one frame shows it: its embedding, the middle of its outline (0..1) and its area (0..1 of the frame). */
    class View(val vector: FloatArray, val centerX: Float, val centerY: Float, val area: Float)

    private val kept = mutableListOf<View>()

    val total: Int = samplesPerStep * ItemStep.entries.size
    val taken: Int get() = kept.size
    val samples: List<FloatArray> get() = kept.map { it.vector }

    /** The step being collected; null when done. */
    val step: ItemStep? get() = ItemStep.entries.getOrNull(kept.size / samplesPerStep)
    val isDone: Boolean get() = step == null

    private val still: List<View> get() = kept.take(samplesPerStep)

    /** Where the thing was while held still (0..1); null before the first sample. */
    val stillCenterX: Float? get() = still.takeIf { it.isNotEmpty() }?.map { it.centerX }?.average()?.toFloat()
    val stillCenterY: Float? get() = still.takeIf { it.isNotEmpty() }?.map { it.centerY }?.average()?.toFloat()
    private val stillArea: Float? get() = still.takeIf { it.isNotEmpty() }?.map { it.area }?.average()?.toFloat()

    /** Whether [view] is still the thing being learned: the first view always. */
    fun isTheItem(view: View): Boolean {
        if (kept.isEmpty()) return true
        val area = stillArea ?: return true
        val sized = view.area >= area * SIZE_MIN && view.area <= area * SIZE_MAX
        return sized && still.any { FaceMatcher.cosine(view.vector, it.vector) >= SAME_MIN }
    }

    /** Whether [view] has moved the way the current step asks (always true while held still). */
    fun hasMoved(view: View): Boolean {
        val x = stillCenterX ?: return step == ItemStep.STILL
        val y = stillCenterY ?: return step == ItemStep.STILL
        return when (step) {
            null -> false
            ItemStep.STILL -> true
            ItemStep.LEFT -> view.centerX - x >= MIN_SHIFT
            ItemStep.RIGHT -> x - view.centerX >= MIN_SHIFT
            ItemStep.UP -> view.centerY - y >= MIN_SHIFT
        }
    }

    /** Whether [view] can be the next sample. */
    fun accepts(view: View): Boolean = !isDone && isTheItem(view) && hasMoved(view)

    /** Records one sample. Returns true when it finished a step (or the whole enrolment). */
    fun add(view: View): Boolean {
        val current = step ?: return false
        if (!accepts(view)) return false
        kept += view
        return step != current
    }

    /** Start again from nothing. */
    fun restart() = kept.clear()

    fun percent(): Int = taken * 100 / total

    companion object {
        const val SAMPLES_PER_STEP = 3

        /**
         * The least a view must look like the samples held still. The same item moved in the frame scored
         * 0.5 to 0.9, the table and a jar next to it 0.25 and less (MobileNetV3-Small, the logs).
         */
        const val SAME_MIN = 0.4f

        /** How far (fraction of the frame) the thing must have shifted for a LEFT, RIGHT or UP sample. */
        const val MIN_SHIFT = 0.08f

        /** The thing's area, as a part of what it was while held still, that is still the same thing. */
        const val SIZE_MIN = 0.4f
        const val SIZE_MAX = 2.5f
    }
}

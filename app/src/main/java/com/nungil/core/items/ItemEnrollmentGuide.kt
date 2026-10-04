package com.nungil.core.items

import com.nungil.core.people.FaceMatcher

/** The four steps of item enrolment: held still, then the phone moved left, right and up. */
enum class ItemStep { STILL, LEFT, RIGHT, UP }

/**
 * [samplesPerStep] samples held still, then with the phone moved left, right and up (12 by default).
 *
 * Every sample must still be the thing that was confirmed: it looks like the samples held still (cosine of at
 * least [SAME_MIN] for its square and [ALONE_SAME_MIN] for the thing alone) and is about their size ([SIZE_MIN]..[SIZE_MAX] of their area), so the bottle behind it or
 * the table is never learned as the item. Moving the phone left shifts the thing right in the frame, and so on;
 * a LEFT, RIGHT or UP sample counts only once the thing has shifted at least [MIN_SHIFT] of the frame that way
 * from where it was held still. Coming back to where it was after LEFT is not RIGHT (the logs).
 *
 * Each sample is kept twice: the square around the thing, and the same square with everything that is not the
 * thing painted over (ItemMask.alone). The square is 19 to 41% item, and the same item on another background
 * scored 0.50 against it (measured); the thing alone does not know where it stood.
 */
class ItemEnrollmentGuide(val samplesPerStep: Int = SAMPLES_PER_STEP) {
    /**
     * The thing as one frame shows it: the embedding of its square, the middle of its outline (0..1), its area
     * (0..1 of the frame) and the embedding of the thing [alone], when that picture could be made.
     */
    class View(val vector: FloatArray, val centerX: Float, val centerY: Float, val area: Float, val alone: FloatArray? = null)

    private val kept = mutableListOf<View>()

    val total: Int = samplesPerStep * ItemStep.entries.size
    val taken: Int get() = kept.size

    /** What is saved: the square of every sample, then the thing alone of every sample that has it. */
    val samples: List<FloatArray> get() = kept.map { it.vector } + kept.mapNotNull { it.alone }

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
        if (!sized || still.none { FaceMatcher.cosine(view.vector, it.vector) >= SAME_MIN }) return false
        // The square holds the table too, and the table is the same under another thing.
        val alone = view.alone ?: return true
        val stills = still.mapNotNull { it.alone }
        return stills.isEmpty() || stills.any { FaceMatcher.cosine(alone, it) >= ALONE_SAME_MIN }
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

        /**
         * The least the thing alone must look like the thing alone while it was held still. Three of the twelve
         * samples of a bottle scored 0.47 to 0.54 as squares and 0.02 to 0.04 alone: something else on the same
         * table. The real samples of four items scored 0.43 to 0.89 alone (the saved vectors).
         */
        const val ALONE_SAME_MIN = 0.35f

        /** How far (fraction of the frame) the thing must have shifted for a LEFT, RIGHT or UP sample. */
        const val MIN_SHIFT = 0.08f

        /**
         * A thing that covers more of the frame than this is too near to learn: the frame cuts its outline, so
         * its middle hardly moves when the phone does, and a little nearer it is the whole view and is lost.
         * A towel at 53 to 93% took 155 s, 98 of them for LEFT; a bottle and a box at 11 to 33% under a minute
         * (the logs).
         */
        const val FILLS_VIEW = 0.45f

        fun fillsView(cover: Float): Boolean = cover > FILLS_VIEW

        /** The thing's area, as a part of what it was while held still, that is still the same thing. */
        const val SIZE_MIN = 0.4f
        const val SIZE_MAX = 2.5f
    }
}

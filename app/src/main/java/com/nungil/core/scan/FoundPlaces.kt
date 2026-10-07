package com.nungil.core.scan

import com.nungil.contract.Box

/**
 * Saved items found by their look where the detector drew no box (a towel, a charger, a box: things it does not
 * know). Such an item is shown and announced only after it was found twice in a row, each find at most
 * [KEEP_MS] after the last, and only while it keeps being found; a find is about once a second.
 * A place keeps the thing's own [Place.outline] (the segmenter's, ItemSearch) while the squares that find it still
 * hold its middle; that outline, not the square, is drawn.
 * Not thread-safe: the scan screen calls it from its analysis thread.
 */
class FoundPlaces {
    /** [box]: where it was found (the square); [outline]: the thing's own outline, null when there is none. */
    data class Place(val box: Box, val name: String, val outline: Box? = null)

    private class Track(var box: Box, var hits: Int, var atMs: Long, var outline: Box?)

    private val tracks = LinkedHashMap<String, Track>()

    /**
     * [name] was found at [box], with the thing's own [outline] when it was cut out: the phone turns, so the same
     * name somewhere else is the same item. Without a new outline the last one stays while [box] holds its middle.
     */
    fun found(box: Box, name: String, nowMs: Long, outline: Box? = null) {
        val track = tracks[name]
        if (track != null && nowMs - track.atMs <= KEEP_MS) {
            track.box = box
            track.hits++
            track.atMs = nowMs
            track.outline = outline ?: track.outline?.takeIf { box.holds(it.centerX, it.centerY) }
        } else {
            tracks[name] = Track(box, 1, nowMs, outline)
        }
    }

    private fun Box.holds(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    /** The items to treat as seen in this frame. */
    fun current(nowMs: Long): List<Place> {
        tracks.values.removeAll { nowMs - it.atMs > KEEP_MS }
        return tracks.filterValues { it.hits >= CONFIRM_HITS }.map { (name, t) -> Place(t.box, name, t.outline) }
    }

    companion object {
        const val CONFIRM_HITS = 2

        /**
         * How long a find holds. Longer than one search of the frame takes, short enough that a place the phone
         * turned away from is not announced on the wrong side.
         */
        const val KEEP_MS = 2_500L
    }
}

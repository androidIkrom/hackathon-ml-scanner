package com.nungil.core.scan

import com.nungil.contract.Box

/**
 * Saved items found by their look where the detector drew no box (a towel, a charger, a box: things it does not
 * know). Such an item is shown and announced only after it was found twice in a row, each find at most
 * [KEEP_MS] after the last, and only while it keeps being found; a find is about once a second.
 * Not thread-safe: the scan screen calls it from its analysis thread.
 */
class FoundPlaces {
    data class Place(val box: Box, val name: String)

    private class Track(var box: Box, var hits: Int, var atMs: Long)

    private val tracks = LinkedHashMap<String, Track>()

    /** [name] was found at [box]: the phone turns, so the same name somewhere else is the same item. */
    fun found(box: Box, name: String, nowMs: Long) {
        val track = tracks[name]
        if (track != null && nowMs - track.atMs <= KEEP_MS) {
            track.box = box
            track.hits++
            track.atMs = nowMs
        } else {
            tracks[name] = Track(box, 1, nowMs)
        }
    }

    /** The items to treat as seen in this frame. */
    fun current(nowMs: Long): List<Place> {
        tracks.values.removeAll { nowMs - it.atMs > KEEP_MS }
        return tracks.filterValues { it.hits >= CONFIRM_HITS }.map { (name, t) -> Place(t.box, name) }
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

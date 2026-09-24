package com.nungil.core.people

import com.nungil.contract.Detection

/** A recognised face: centre normalised to the frame (0..1) and the saved person it matched. */
data class FaceHit(val centerX: Float, val centerY: Float, val personId: Long, val name: String, val score: Float)

/** Puts recognised faces onto the detector's person boxes. */
object FaceBoxes {
    const val PERSON = "person"

    /** Index of the smallest person box that contains (x, y), or -1. */
    fun personBoxFor(x: Float, y: Float, detections: List<Detection>): Int {
        var best = -1
        var bestArea = Float.MAX_VALUE
        detections.forEachIndexed { i, d ->
            val b = d.box
            if (d.label == PERSON && x >= b.left && x <= b.right && y >= b.top && y <= b.bottom && b.area < bestArea) {
                best = i
                bestArea = b.area
            }
        }
        return best
    }

    /** Detection index to face, best score first: one name per box and one box per person. */
    fun assign(hits: List<FaceHit>, detections: List<Detection>): Map<Int, FaceHit> {
        val out = LinkedHashMap<Int, FaceHit>()
        val usedPeople = mutableSetOf<Long>()
        for (hit in hits.sortedByDescending { it.score }) {
            if (hit.personId in usedPeople) continue
            val index = personBoxFor(hit.centerX, hit.centerY, detections)
            if (index < 0 || index in out) continue
            out[index] = hit
            usedPeople += hit.personId
        }
        return out
    }
}

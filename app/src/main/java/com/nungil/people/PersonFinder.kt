package com.nungil.people

import android.content.Context
import com.nungil.contract.app.Found
import com.nungil.contract.app.SavedFinder
import com.nungil.contract.app.TagKind
import com.nungil.contract.app.VisionFrame
import com.nungil.core.people.FaceBoxes

/**
 * Saved people for every camera screen: faces that match a saved person, put on the detector's person boxes. Only
 * frames with a person box are looked at. Loads the model and the people on creation; worker thread only.
 */
class PersonFinder(context: Context) : SavedFinder {
    private val faces = FaceIdentifier(context)

    override fun find(frame: VisionFrame, only: Long?): List<Found> {
        val bitmap = frame.bitmap ?: return emptyList()
        val detections = frame.detections
        if (detections.none { it.label == FaceBoxes.PERSON }) return emptyList()
        val hits = faces.identify(bitmap)
        if (only != null) {
            val hit = hits.filter { it.personId == only }.maxByOrNull { it.score } ?: return emptyList()
            val index = FaceBoxes.personBoxFor(hit.centerX, hit.centerY, detections)
            val d = detections.getOrNull(index) ?: return emptyList()
            return listOf(Found(TagKind.PERSON, hit.personId, hit.name, d.box, index, hit.score))
        }
        return FaceBoxes.assign(hits, detections).map { (index, hit) ->
            Found(TagKind.PERSON, hit.personId, hit.name, detections[index].box, index, hit.score)
        }
    }

    override fun close() = faces.close()
}

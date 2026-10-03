package com.nungil.people

import android.content.Context
import com.nungil.contract.Box
import com.nungil.contract.app.VisionFrame
import com.nungil.core.people.FaceBoxes
import com.nungil.search.TargetMatcher

/** Search camera target "a saved person": the person box whose face matches [personId] against everyone saved. */
class PersonTargetMatcher(context: Context, private val personId: Long) : TargetMatcher {
    private val faces = FaceIdentifier(context)

    override val slow: Boolean = true

    override fun locate(frame: VisionFrame): Box? {
        val bitmap = frame.bitmap ?: return null
        if (frame.detections.none { it.label == FaceBoxes.PERSON }) return null
        val hit = faces.identify(bitmap).filter { it.personId == personId }.maxByOrNull { it.score } ?: return null
        return frame.detections.getOrNull(FaceBoxes.personBoxFor(hit.centerX, hit.centerY, frame.detections))?.box
    }

    override fun close() = faces.close()
}

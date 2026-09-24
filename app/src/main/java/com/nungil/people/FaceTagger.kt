package com.nungil.people

import android.content.Context
import com.nungil.contract.app.NameTag
import com.nungil.contract.app.NameTagger
import com.nungil.contract.app.TagKind
import com.nungil.contract.app.VisionFrame
import com.nungil.core.people.EveryNth
import com.nungil.core.people.FaceBoxes

/**
 * Names saved people inside A's scan. Runs face recognition on every 3rd frame that has a person box; a face
 * inside a person box renames that box. A keeps the name between frames (StickyNames).
 */
class FaceTagger(context: Context) : NameTagger {
    private val faces = FaceIdentifier(context)
    private val throttle = EveryNth(EVERY_NTH_PERSON_FRAME)

    override fun tag(frame: VisionFrame): List<NameTag> {
        val bitmap = frame.bitmap ?: return emptyList()
        if (frame.detections.none { it.label == FaceBoxes.PERSON }) return emptyList()
        if (!throttle.take()) return emptyList()
        return FaceBoxes.assign(faces.identify(bitmap), frame.detections)
            .map { (index, hit) -> NameTag(index, hit.name, TagKind.PERSON) }
    }

    override fun close() = faces.close()

    private companion object {
        const val EVERY_NTH_PERSON_FRAME = 3
    }
}

package com.nungil.contract.app

import com.nungil.contract.Box
import java.io.Closeable

enum class TagKind { PERSON, ITEM }

/**
 * A saved name found on detection number [detectionIndex] of the frame. A saved item found by its look where the
 * detector drew no box has detectionIndex -1 and its place in [box].
 */
data class NameTag(val detectionIndex: Int, val name: String, val kind: TagKind, val box: Box? = null)

/**
 * Recognises saved people and items inside a frame. Implemented by Y, called by the scan screen (A)
 * on its extras thread, one call at a time. Needs VisionFrame.bitmap; returns an empty list without it.
 */
interface NameTagger : Closeable {
    fun tag(frame: VisionFrame): List<NameTag>
}

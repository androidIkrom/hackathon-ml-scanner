package com.nungil.contract.app

import com.nungil.contract.Box
import java.io.Closeable

/**
 * Finds saved things (people, items) in a frame. One finder per kind serves every camera screen, so a change to
 * how saved things are recognised reaches all of them (spec 2026-10-05-shared-recognition). Called on one worker
 * thread at a time; needs VisionFrame.bitmap and returns nothing without it.
 */
interface SavedFinder : Closeable {
    /** The saved things in [frame]. [only]: one saved id to look for (Find); null: all saved things. */
    fun find(frame: VisionFrame, only: Long? = null): List<Found>
}

data class Found(
    val kind: TagKind,
    val id: Long,
    val name: String,
    /** Where it is: the thing's outline when one was cut out, else the square or the detector box. */
    val box: Box,
    /** The detector box that is this thing, if there is one. */
    val detectionIndex: Int?,
    val score: Float,
)

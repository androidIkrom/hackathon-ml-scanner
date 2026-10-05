package com.nungil.search

import android.content.Context
import com.nungil.contract.Box
import com.nungil.contract.app.SavedFinder
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.Reach
import com.nungil.core.search.TargetType
import com.nungil.items.ItemFinder
import com.nungil.people.PersonFinder

/** Builds the matcher for a search target. Call on a worker thread: face and item matchers load models. */
object TargetMatchers {
    fun create(context: Context, type: TargetType, id: Long, label: String): TargetMatcher = when (type) {
        TargetType.LABEL -> LabelMatcher(label)
        TargetType.PERSON -> SavedTargetMatcher(PersonFinder(context), id)
        TargetType.ITEM -> SavedTargetMatcher(ItemFinder(context, Reach.WHOLE_FRAME), id)
    }
}

/** A saved person or item as the search target, from the same finders the scan screens use (SavedFinder). */
class SavedTargetMatcher(private val finder: SavedFinder, private val id: Long) : TargetMatcher {
    override val slow: Boolean = true

    override fun locate(frame: VisionFrame): Box? = finder.find(frame, only = id).firstOrNull()?.box

    override fun close() = finder.close()
}

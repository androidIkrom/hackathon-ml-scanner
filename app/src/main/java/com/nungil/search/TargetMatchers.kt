package com.nungil.search

import android.content.Context
import com.nungil.core.search.TargetType
import com.nungil.items.ItemTargetMatcher
import com.nungil.people.PersonTargetMatcher

/** Builds the matcher for a search target. Call on a worker thread: face and item matchers load models. */
object TargetMatchers {
    fun create(context: Context, type: TargetType, id: Long, label: String): TargetMatcher = when (type) {
        TargetType.LABEL -> LabelMatcher(label)
        TargetType.PERSON -> PersonTargetMatcher(context, id)
        TargetType.ITEM -> ItemTargetMatcher(context, id)
    }
}

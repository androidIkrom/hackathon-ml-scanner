package com.nungil.search

import android.content.Context
import com.nungil.core.items.ItemEnrollmentGuide
import com.nungil.core.search.SavedName
import com.nungil.core.search.TargetType
import com.nungil.data.AppDatabase
import com.nungil.data.byItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every saved person and item as search candidates, read from Room off the main thread. */
object SavedNames {
    suspend fun load(context: Context): List<SavedName> = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        db.people().allPeople().map { SavedName(it.id, it.name, TargetType.PERSON, "person") } +
            db.items().allItems().map { SavedName(it.id, it.name, TargetType.ITEM, it.label) }
    }

    /** The ids of the items learned the old way (ItemEnrollmentGuide.oldStyle): they cannot be searched for. */
    suspend fun oldItems(context: Context): Set<Long> = withContext(Dispatchers.IO) {
        ItemEnrollmentGuide.oldStyle(AppDatabase.get(context).items().vectorCounts().byItem())
    }
}

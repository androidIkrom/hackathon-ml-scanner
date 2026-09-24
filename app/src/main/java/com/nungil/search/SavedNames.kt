package com.nungil.search

import android.content.Context
import com.nungil.core.search.SavedName
import com.nungil.core.search.TargetType
import com.nungil.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every saved person and item as search candidates, read from Room off the main thread. */
object SavedNames {
    suspend fun load(context: Context): List<SavedName> = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        db.people().allPeople().map { SavedName(it.id, it.name, TargetType.PERSON, "person") } +
            db.items().allItems().map { SavedName(it.id, it.name, TargetType.ITEM, it.label) }
    }
}

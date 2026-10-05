package com.nungil.items

import android.content.Context
import com.nungil.core.items.ItemEnrollmentGuide
import com.nungil.core.items.ItemMatcher
import com.nungil.core.people.VectorBytes
import com.nungil.data.AppDatabase
import kotlinx.coroutines.runBlocking

/** Saved items and their samples from Room, matched with ItemMatcher. Load on a worker thread. */
class ItemRecognizer(context: Context) {
    private val db = AppDatabase.get(context.applicationContext)

    @Volatile
    private var known: Map<Long, List<FloatArray>> = emptyMap()

    @Volatile
    private var names: Map<Long, String> = emptyMap()

    val isEmpty: Boolean get() = known.isEmpty()

    /** Blocks on Room; never call on the main thread. */
    fun reload() = runBlocking {
        val items = db.items().allItems()
        val embeddings = db.items().allEmbeddings()
        val byId = items.associate { it.id to it.name }
        names = byId
        known = embeddings
            .filter { it.itemId in byId }
            .groupBy({ it.itemId }, { VectorBytes.toFloats(it.vector) })
    }

    fun identify(vector: FloatArray, threshold: Float = ItemMatcher.THRESHOLD): ItemMatcher.Match? =
        ItemMatcher.bestMatch(vector, known, threshold)

    fun nameOf(itemId: Long): String? = names[itemId]

    /** Every saved item's name, by id. */
    val allNames: Map<Long, String> get() = names

    /** The saved items that have samples of themselves alone (ItemEnrollmentGuide.learnedAlone). */
    fun learnedAlone(): Set<Long> = known.filterValues { ItemEnrollmentGuide.learnedAlone(it.size) }.keys

    /** How many samples are saved for [itemId]. */
    fun samples(itemId: Long): Int = known[itemId]?.size ?: 0

    /** [itemId] and every other saved item of the same name (ItemMatcher.sameName). */
    fun sameName(itemId: Long): Set<Long> = ItemMatcher.sameName(itemId, names)

    companion object {
        /** Blocks on Room; never call on the main thread. */
        fun hasItems(context: Context): Boolean = runBlocking {
            AppDatabase.get(context.applicationContext).items().allItems().isNotEmpty()
        }
    }
}

package com.nungil.people

import android.content.Context
import com.nungil.core.people.FaceMatcher
import com.nungil.core.people.VectorBytes
import com.nungil.data.AppDatabase
import kotlinx.coroutines.runBlocking

/** Saved people and their face samples from Room, matched with FaceMatcher. Load on a worker thread. */
class FaceRecognizer(context: Context) {
    private val db = AppDatabase.get(context.applicationContext)

    @Volatile
    private var known: Map<Long, List<FloatArray>> = emptyMap()

    @Volatile
    private var names: Map<Long, String> = emptyMap()

    val isEmpty: Boolean get() = known.isEmpty()

    /** Blocks on Room; never call on the main thread. */
    fun reload() = runBlocking {
        val people = db.people().allPeople()
        val faces = db.people().allFaces()
        val byId = people.associate { it.id to it.name }
        names = byId
        known = faces
            .filter { it.personId in byId }
            .groupBy({ it.personId }, { VectorBytes.toFloats(it.vector) })
    }

    fun identify(vector: FloatArray): FaceMatcher.Match? = FaceMatcher.bestMatch(vector, known)

    fun nameOf(personId: Long): String? = names[personId]

    companion object {
        /** Blocks on Room; never call on the main thread. */
        fun hasPeople(context: Context): Boolean = runBlocking {
            AppDatabase.get(context.applicationContext).people().allPeople().isNotEmpty()
        }
    }
}

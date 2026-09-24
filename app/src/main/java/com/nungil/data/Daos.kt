package com.nungil.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScanDao {
    @Insert
    abstract suspend fun insertScan(scan: ScanEntity): Long

    @Insert
    abstract suspend fun insertObjects(objects: List<DetectedObjectEntity>)

    /** Parent and children in one transaction: a half-saved scan is worse than none. */
    @Transaction
    open suspend fun insertScanWithObjects(scan: ScanEntity, objects: List<DetectedObjectEntity>): Long {
        val id = insertScan(scan)
        insertObjects(objects.map { it.copy(scanId = id) })
        return id
    }

    @Query("SELECT * FROM scans ORDER BY startedAt DESC")
    abstract fun observeScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE id = :id")
    abstract suspend fun getScan(id: Long): ScanEntity?

    @Query("SELECT * FROM detected_objects WHERE scanId = :scanId")
    abstract suspend fun objectsOf(scanId: Long): List<DetectedObjectEntity>

    @Query("DELETE FROM scans WHERE id = :id")
    abstract suspend fun deleteScan(id: Long)
}

@Dao
abstract class PersonDao {
    @Insert
    abstract suspend fun insertPerson(person: PersonEntity): Long

    @Insert
    abstract suspend fun insertFaces(faces: List<FaceEmbeddingEntity>)

    @Transaction
    open suspend fun insertPersonWithFaces(person: PersonEntity, faces: List<FaceEmbeddingEntity>): Long {
        val id = insertPerson(person)
        insertFaces(faces.map { it.copy(personId = id) })
        return id
    }

    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    abstract fun observePeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people")
    abstract suspend fun allPeople(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id")
    abstract suspend fun getPerson(id: Long): PersonEntity?

    @Query("SELECT * FROM face_embeddings")
    abstract suspend fun allFaces(): List<FaceEmbeddingEntity>

    @Query("UPDATE people SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM people WHERE id = :id")
    abstract suspend fun deletePerson(id: Long)
}

@Dao
abstract class ItemDao {
    @Insert
    abstract suspend fun insertItem(item: ItemEntity): Long

    @Insert
    abstract suspend fun insertEmbeddings(embeddings: List<ItemEmbeddingEntity>)

    @Transaction
    open suspend fun insertItemWithEmbeddings(item: ItemEntity, embeddings: List<ItemEmbeddingEntity>): Long {
        val id = insertItem(item)
        insertEmbeddings(embeddings.map { it.copy(itemId = id) })
        return id
    }

    /** [kind] is ItemKind.name. */
    @Query("SELECT * FROM items WHERE kind = :kind ORDER BY name COLLATE NOCASE")
    abstract fun observeItems(kind: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items")
    abstract suspend fun allItems(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    abstract suspend fun getItem(id: Long): ItemEntity?

    @Query("SELECT * FROM item_embeddings")
    abstract suspend fun allEmbeddings(): List<ItemEmbeddingEntity>

    @Query("UPDATE items SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM items WHERE id = :id")
    abstract suspend fun deleteItem(id: Long)
}

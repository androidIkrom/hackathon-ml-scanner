package com.nungil.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One finished scan. [mode] is ScanMode.name, [lang] is Lang.tag of the summary text. */
@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val mode: String,
    val coveragePercent: Int,
    val summaryText: String,
    val lang: String,
)

@Entity(
    tableName = "detected_objects",
    foreignKeys = [
        ForeignKey(
            entity = ScanEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scanId")],
)
data class DetectedObjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanId: Long = 0,
    val label: String,
    val count: Int,
    val colorName: String?,
    val relAngleDeg: Float,
    val sector8: Int,
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photoPath: String?,
    val createdAt: Long,
)

/** [vector] is a FloatArray stored as little-endian bytes (see Y's VectorBytes). */
@Entity(
    tableName = "face_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class FaceEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long = 0,
    val vector: ByteArray,
)

/** [kind] is ItemKind.name; [label] is the most common COCO label seen while enrolling. */
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val label: String,
    val photoPath: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "item_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class ItemEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long = 0,
    val vector: ByteArray,
)

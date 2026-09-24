package com.nungil.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** All six tables ship in version 1, so no migration is ever needed during the event. */
@Database(
    entities = [
        ScanEntity::class,
        DetectedObjectEntity::class,
        PersonEntity::class,
        FaceEmbeddingEntity::class,
        ItemEntity::class,
        ItemEmbeddingEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scans(): ScanDao
    abstract fun people(): PersonDao
    abstract fun items(): ItemDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "nungil.db")
                    .build()
                    .also { instance = it }
            }
    }
}

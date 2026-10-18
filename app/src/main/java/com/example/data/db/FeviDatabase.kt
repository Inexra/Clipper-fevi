package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, ClipEntity::class, TranscriptEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FeviDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun clipDao(): ClipDao
    abstract fun transcriptDao(): TranscriptDao

    companion object {
        @Volatile
        private var INSTANCE: FeviDatabase? = null

        fun getDatabase(context: Context): FeviDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FeviDatabase::class.java,
                    "fevi_clipper_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.lycoslane.pureaudiobooks.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AudiobookEntity::class,
        ChapterTrackEntity::class,
        BookmarkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PureAudiobooksDatabase : RoomDatabase() {
    abstract fun dao(): PureAudiobooksDao

    companion object {
        @Volatile
        private var INSTANCE: PureAudiobooksDatabase? = null

        fun getInstance(context: Context): PureAudiobooksDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PureAudiobooksDatabase::class.java,
                    "pureaudiobooks.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

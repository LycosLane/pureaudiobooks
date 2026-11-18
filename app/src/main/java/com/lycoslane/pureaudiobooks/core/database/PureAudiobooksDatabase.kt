package com.lycoslane.pureaudiobooks.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AudiobookEntity::class,
        ChapterTrackEntity::class,
        BookmarkEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PureAudiobooksDatabase : RoomDatabase() {
    abstract fun dao(): PureAudiobooksDao

    companion object {
        @Volatile
        private var INSTANCE: PureAudiobooksDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN lastTrackId INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): PureAudiobooksDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PureAudiobooksDatabase::class.java,
                    "pureaudiobooks.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

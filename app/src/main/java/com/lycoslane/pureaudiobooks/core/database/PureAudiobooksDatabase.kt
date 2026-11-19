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
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN contentFingerprint TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN monoModeOverride INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN audioNormalizationOverride INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE chapter_tracks ADD COLUMN monoModeOverride INTEGER NOT NULL DEFAULT -1")
            }
        }

        fun getInstance(context: Context): PureAudiobooksDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PureAudiobooksDatabase::class.java,
                    "pureaudiobooks.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

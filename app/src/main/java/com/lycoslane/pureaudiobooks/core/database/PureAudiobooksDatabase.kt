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
    version = 5,
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN speedOverride REAL NOT NULL DEFAULT -1.0")
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN silenceModeOverride INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN smartRewindOverride INTEGER NOT NULL DEFAULT -1")
                db.execSQL("UPDATE audiobooks SET speedOverride = playbackSpeed WHERE ABS(playbackSpeed - 1.0) >= 0.01")
                db.execSQL("UPDATE audiobooks SET silenceModeOverride = CASE WHEN skipSilence != 0 THEN (MIN(MAX(silenceIntensity, 0), 2) + 1) ELSE -1 END")
            }
        }

        fun getInstance(context: Context): PureAudiobooksDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PureAudiobooksDatabase::class.java,
                    "pureaudiobooks.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

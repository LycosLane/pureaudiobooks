package com.lycoslane.pureaudiobooks.core.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.core.audio.AudioOverride
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudiobookDatabaseTest {

    private lateinit var db: PureAudiobooksDatabase
    private lateinit var dao: PureAudiobooksDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PureAudiobooksDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.dao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertTracksBookmarksAndTrashLifecycle() = runTest {
        val bookId = dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://doc1",
                folderName = "Dune",
                title = "Dune",
                author = "Frank Herbert"
            )
        )
        assertTrue(bookId > 0L)

        dao.insertTracks(
            listOf(
                ChapterTrackEntity(
                    audiobookId = bookId,
                    guid = "t1",
                    title = "Chapter 1",
                    sortOrder = 0,
                    fileUri = "content://a/1",
                    durationMs = 60_000L
                )
            )
        )
        val tracks = dao.getTracksForAudiobook(bookId)
        assertEquals(1, tracks.size)

        dao.insertBookmark(
            BookmarkEntity(
                audiobookId = bookId,
                trackId = tracks[0].id,
                trackTitle = "Chapter 1",
                positionMs = 15_000L,
                note = "Great quote",
                createdAtMs = 1000L
            )
        )
        assertEquals(1, dao.getAllBookmarksOnce().size)

        dao.setDeletedAt(bookId, 5_000L)
        val expired = dao.getExpiredTrashedAudiobooks(10_000L)
        assertEquals(1, expired.size)
        assertNotNull(dao.getAudiobook(bookId))

        dao.deleteAudiobook(bookId)
        assertEquals(0, dao.getTracksForAudiobook(bookId).size)
        assertEquals(0, dao.getAllBookmarksOnce().size)
    }

    @Test
    fun monoNormalizationOverridesAndMostRecentInProgressQueryWork() = runTest {
        val bookId = dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://mono",
                folderName = "MonoBook",
                title = "Mono Book",
                lastPlayedAtMs = 2_000L
            )
        )
        dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://hidden",
                folderName = "HiddenBook",
                title = "Hidden Book",
                isHidden = true,
                lastPlayedAtMs = 5_000L
            )
        )
        dao.insertTracks(
            listOf(
                ChapterTrackEntity(
                    audiobookId = bookId,
                    guid = "m1",
                    title = "Part 1",
                    fileUri = "content://m/1",
                    durationMs = 90_000L
                )
            )
        )
        val track = dao.getTracksForAudiobook(bookId).first()

        dao.setBookMonoModeOverride(bookId, AudioMonoMode.AUTO)
        dao.setBookAudioNormalizationOverride(bookId, AudioOverride.ON)
        dao.setTrackMonoModeOverride(track.id, AudioMonoMode.LEFT)

        val updatedBook = dao.getAudiobook(bookId)!!
        assertEquals(AudioMonoMode.AUTO, updatedBook.monoModeOverride)
        assertEquals(AudioOverride.ON, updatedBook.audioNormalizationOverride)
        assertEquals(AudioMonoMode.LEFT, dao.getTrack(track.id)!!.monoModeOverride)

        val recent = dao.getMostRecentInProgressAudiobook()
        assertNotNull(recent)
        assertEquals(bookId, recent!!.audiobook.id)
    }

    @Test
    fun migratesV3DatabaseToV4PreservingExistingRows() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_3_4_test.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audiobooks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `folderKey` TEXT NOT NULL,
                        `folderDocId` TEXT NOT NULL,
                        `folderUri` TEXT NOT NULL,
                        `folderName` TEXT NOT NULL,
                        `parentFolderName` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `artworkUrl` TEXT NOT NULL,
                        `playbackSpeed` REAL NOT NULL,
                        `skipSilence` INTEGER NOT NULL,
                        `silenceIntensity` INTEGER NOT NULL,
                        `hasTagConflict` INTEGER NOT NULL,
                        `conflictSummaryJson` TEXT NOT NULL,
                        `metadataLockedByUser` INTEGER NOT NULL,
                        `isSplitByAlbum` INTEGER NOT NULL,
                        `isFinished` INTEGER NOT NULL,
                        `finishedAtMs` INTEGER NOT NULL,
                        `isHidden` INTEGER NOT NULL,
                        `lastTrackId` INTEGER NOT NULL,
                        `deletedAtMs` INTEGER,
                        `lastPlayedAtMs` INTEGER NOT NULL,
                        `lastScannedMs` INTEGER NOT NULL,
                        `contentFingerprint` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audiobooks_folderKey` ON `audiobooks` (`folderKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audiobooks_deletedAtMs` ON `audiobooks` (`deletedAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audiobooks_lastPlayedAtMs` ON `audiobooks` (`lastPlayedAtMs`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chapter_tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `audiobookId` INTEGER NOT NULL,
                        `guid` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `trackNumber` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `albumTag` TEXT NOT NULL,
                        `artistTag` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `isFinished` INTEGER NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `lastModifiedMs` INTEGER NOT NULL,
                        FOREIGN KEY(`audiobookId`) REFERENCES `audiobooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_tracks_audiobookId` ON `chapter_tracks` (`audiobookId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapter_tracks_audiobookId_guid` ON `chapter_tracks` (`audiobookId`, `guid`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `audiobookId` INTEGER NOT NULL,
                        `trackId` INTEGER NOT NULL,
                        `trackTitle` TEXT NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        FOREIGN KEY(`audiobookId`) REFERENCES `audiobooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_audiobookId` ON `bookmarks` (`audiobookId`)")

                db.execSQL(
                    """
                    INSERT INTO `audiobooks` (`id`, `folderKey`, `folderDocId`, `folderUri`, `folderName`,
                        `parentFolderName`, `title`, `author`, `description`, `artworkUrl`,
                        `playbackSpeed`, `skipSilence`, `silenceIntensity`, `hasTagConflict`,
                        `conflictSummaryJson`, `metadataLockedByUser`, `isSplitByAlbum`, `isFinished`,
                        `finishedAtMs`, `isHidden`, `lastTrackId`, `deletedAtMs`, `lastPlayedAtMs`,
                        `lastScannedMs`, `contentFingerprint`)
                    VALUES (1, 'local://v3book', 'doc3', 'content://tree', 'Legacy Book',
                        'Author', 'Legacy Book', 'Author', 'Desc', '',
                        1.25, 1, 2, 0,
                        '', 1, 0, 0,
                        0, 0, 10, NULL, 9999,
                        5000, 'fp123')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `chapter_tracks` (`id`, `audiobookId`, `guid`, `title`, `trackNumber`,
                        `sortOrder`, `fileUri`, `albumTag`, `artistTag`, `durationMs`,
                        `positionMs`, `isFinished`, `fileSizeBytes`, `lastModifiedMs`)
                    VALUES (10, 1, 'tr-10', 'Chapter 1', 1,
                        0, 'content://track/10', 'Legacy Book', 'Author', 300000,
                        45000, 0, 1024, 1000)
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migrated = Room.databaseBuilder(context, PureAudiobooksDatabase::class.java, dbName)
            .addMigrations(
                PureAudiobooksDatabase.MIGRATION_1_2,
                PureAudiobooksDatabase.MIGRATION_2_3,
                PureAudiobooksDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

        try {
            val migratedDao = migrated.dao()
            val book = migratedDao.getAudiobook(1L)!!
            assertEquals("Legacy Book", book.title)
            assertEquals(1.25f, book.playbackSpeed, 0.001f)
            assertEquals(AudioMonoMode.INHERIT, book.monoModeOverride)
            assertEquals(AudioOverride.INHERIT, book.audioNormalizationOverride)

            val track = migratedDao.getTrack(10L)!!
            assertEquals(45_000L, track.positionMs)
            assertEquals(AudioMonoMode.INHERIT, track.monoModeOverride)
        } finally {
            migrated.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migratesV1DatabaseThroughV4PreservingExistingRows() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_1_4_test.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audiobooks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `folderKey` TEXT NOT NULL,
                        `folderDocId` TEXT NOT NULL,
                        `folderUri` TEXT NOT NULL,
                        `folderName` TEXT NOT NULL,
                        `parentFolderName` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `artworkUrl` TEXT NOT NULL,
                        `playbackSpeed` REAL NOT NULL,
                        `skipSilence` INTEGER NOT NULL,
                        `silenceIntensity` INTEGER NOT NULL,
                        `hasTagConflict` INTEGER NOT NULL,
                        `conflictSummaryJson` TEXT NOT NULL,
                        `metadataLockedByUser` INTEGER NOT NULL,
                        `isSplitByAlbum` INTEGER NOT NULL,
                        `isFinished` INTEGER NOT NULL,
                        `finishedAtMs` INTEGER NOT NULL,
                        `deletedAtMs` INTEGER,
                        `lastPlayedAtMs` INTEGER NOT NULL,
                        `lastScannedMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audiobooks_folderKey` ON `audiobooks` (`folderKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audiobooks_deletedAtMs` ON `audiobooks` (`deletedAtMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audiobooks_lastPlayedAtMs` ON `audiobooks` (`lastPlayedAtMs`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chapter_tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `audiobookId` INTEGER NOT NULL,
                        `guid` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `trackNumber` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `albumTag` TEXT NOT NULL,
                        `artistTag` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `isFinished` INTEGER NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `lastModifiedMs` INTEGER NOT NULL,
                        FOREIGN KEY(`audiobookId`) REFERENCES `audiobooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_tracks_audiobookId` ON `chapter_tracks` (`audiobookId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapter_tracks_audiobookId_guid` ON `chapter_tracks` (`audiobookId`, `guid`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bookmarks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `audiobookId` INTEGER NOT NULL,
                        `trackId` INTEGER NOT NULL,
                        `trackTitle` TEXT NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        FOREIGN KEY(`audiobookId`) REFERENCES `audiobooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_audiobookId` ON `bookmarks` (`audiobookId`)")

                db.execSQL(
                    """
                    INSERT INTO `audiobooks` (`id`, `folderKey`, `folderDocId`, `folderUri`, `folderName`,
                        `parentFolderName`, `title`, `author`, `description`, `artworkUrl`,
                        `playbackSpeed`, `skipSilence`, `silenceIntensity`, `hasTagConflict`,
                        `conflictSummaryJson`, `metadataLockedByUser`, `isSplitByAlbum`, `isFinished`,
                        `finishedAtMs`, `deletedAtMs`, `lastPlayedAtMs`, `lastScannedMs`)
                    VALUES (1, 'local://v1book', 'doc1', 'content://tree', 'V1 Book',
                        '', 'V1 Book', 'V1 Author', '', '',
                        1.5, 0, 1, 0,
                        '', 0, 0, 0,
                        0, NULL, 1234, 1234)
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migrated = Room.databaseBuilder(context, PureAudiobooksDatabase::class.java, dbName)
            .addMigrations(
                PureAudiobooksDatabase.MIGRATION_1_2,
                PureAudiobooksDatabase.MIGRATION_2_3,
                PureAudiobooksDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

        try {
            val book = migrated.dao().getAudiobook(1L)!!
            assertEquals("V1 Book", book.title)
            assertEquals(false, book.isHidden)
            assertEquals(0L, book.lastTrackId)
            assertEquals("", book.contentFingerprint)
            assertEquals(AudioMonoMode.INHERIT, book.monoModeOverride)
            assertEquals(AudioOverride.INHERIT, book.audioNormalizationOverride)
        } finally {
            migrated.close()
            context.deleteDatabase(dbName)
        }
    }
}

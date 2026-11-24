package com.lycoslane.pureaudiobooks.domain.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.core.audio.AudioOverride
import com.lycoslane.pureaudiobooks.core.audio.SilenceOverride
import com.lycoslane.pureaudiobooks.core.audio.SmartRewindOverride
import com.lycoslane.pureaudiobooks.core.audio.SpeedOverride
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.database.BookmarkEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDao
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDatabase
import com.lycoslane.pureaudiobooks.core.preferences.AppPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudiobookRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: PureAudiobooksDatabase
    private lateinit var dao: PureAudiobooksDao
    private lateinit var prefs: AppPreferences
    private lateinit var repo: AudiobookRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db = Room.inMemoryDatabaseBuilder(context, PureAudiobooksDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
        prefs = AppPreferences(context)
        repo = AudiobookRepository(context, dao, prefs)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun markTracksUpToAndFromUnfinishedWorkAcrossChapters() = runTest {
        val bookId = dao.insertAudiobook(
            AudiobookEntity(folderKey = "local://batch", folderName = "Batch", title = "Batch")
        )
        dao.insertTracks(
            (1..4).map { idx ->
                ChapterTrackEntity(
                    audiobookId = bookId,
                    guid = "ch-$idx",
                    title = "Chapter $idx",
                    sortOrder = idx - 1,
                    trackNumber = idx,
                    fileUri = "content://batch/$idx",
                    durationMs = 60_000L,
                    positionMs = 15_000L
                )
            }
        )
        val tracks = dao.getTracksForAudiobook(bookId)

        // Mark chapters 1..2 finished
        repo.markTracksUpToFinished(bookId, tracks[1].id)
        val afterMarkUpTo = dao.getTracksForAudiobook(bookId)
        assertTrue(afterMarkUpTo[0].isFinished)
        assertTrue(afterMarkUpTo[1].isFinished)
        assertEquals(0L, afterMarkUpTo[1].positionMs)
        assertFalse(afterMarkUpTo[2].isFinished)
        assertEquals(tracks[2].id, dao.getAudiobook(bookId)!!.lastTrackId)

        // Mark chapters 1..4 finished -> marks whole book finished
        repo.markTracksUpToFinished(bookId, tracks[3].id)
        assertTrue(dao.getAudiobook(bookId)!!.isFinished)

        // Mark from chapter 3 as unfinished -> chapters 3..4 reset, book unfinished
        repo.markTracksFromUnfinished(bookId, tracks[2].id)
        val afterFrom = dao.getTracksForAudiobook(bookId)
        assertTrue(afterFrom[0].isFinished)
        assertTrue(afterFrom[1].isFinished)
        assertFalse(afterFrom[2].isFinished)
        assertFalse(afterFrom[3].isFinished)
        assertEquals(0L, afterFrom[2].positionMs)
        assertFalse(dao.getAudiobook(bookId)!!.isFinished)
        assertEquals(tracks[2].id, dao.getAudiobook(bookId)!!.lastTrackId)
    }

    @Test
    fun deleteTracksUpToRemovesSelectedTracksAndBookmarksAndBookWhenAllDeleted() = runTest {
        val bookId = dao.insertAudiobook(
            AudiobookEntity(folderKey = "local://del", folderName = "Del", title = "Del")
        )
        dao.insertTracks(
            (1..3).map { idx ->
                ChapterTrackEntity(
                    audiobookId = bookId,
                    guid = "d-$idx",
                    title = "Ch $idx",
                    sortOrder = idx - 1,
                    trackNumber = idx,
                    fileUri = "content://del/$idx",
                    durationMs = 30_000L
                )
            }
        )
        val tracks = dao.getTracksForAudiobook(bookId)
        dao.updateLastTrack(bookId, tracks[0].id, 1_000L)
        dao.insertBookmark(
            BookmarkEntity(
                audiobookId = bookId,
                trackId = tracks[0].id,
                trackTitle = tracks[0].title,
                positionMs = 5_000L,
                createdAtMs = 1_000L
            )
        )
        dao.insertBookmark(
            BookmarkEntity(
                audiobookId = bookId,
                trackId = tracks[2].id,
                trackTitle = tracks[2].title,
                positionMs = 5_000L,
                createdAtMs = 1_000L
            )
        )

        // Delete chapters 1..2 -> chapter 3 and its bookmark remain
        val removedAll = repo.deleteTracksUpTo(bookId, tracks[1].id)
        assertFalse(removedAll)
        assertEquals(listOf(tracks[2].id), dao.getTracksForAudiobook(bookId).map { it.id })
        assertEquals(1, dao.getAllBookmarksOnce().size)
        assertEquals(tracks[2].id, dao.getAudiobook(bookId)!!.lastTrackId)

        // Delete remaining chapter -> removes audiobook
        val removedBook = repo.deleteTracksUpTo(bookId, tracks[2].id)
        assertTrue(removedBook)
        assertNull(dao.getAudiobook(bookId))
        assertEquals(0, dao.getAllBookmarksOnce().size)
    }

    @Test
    fun applyAudioSettingsToAllBooksUpdatesPrefsAndResetsAllBooksOverrides() = runTest {
        val book1Id = dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://b1",
                folderName = "B1",
                title = "B1",
                playbackSpeed = 1.44f,
                speedOverride = 1.44f,
                skipSilence = true,
                silenceIntensity = 2,
                silenceModeOverride = SilenceOverride.STRONG,
                monoModeOverride = AudioMonoMode.AUTO,
                audioNormalizationOverride = AudioOverride.ON,
                smartRewindOverride = 15
            )
        )
        val book2Id = dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://b2",
                folderName = "B2",
                title = "B2",
                playbackSpeed = 0.85f,
                speedOverride = 0.85f,
                skipSilence = false,
                silenceIntensity = 0,
                silenceModeOverride = SilenceOverride.OFF,
                monoModeOverride = AudioMonoMode.LEFT,
                audioNormalizationOverride = AudioOverride.OFF,
                smartRewindOverride = 30
            )
        )

        val sourceBook = dao.getAudiobook(book1Id)!!
        repo.applyAudioSettingsToAllBooks(sourceBook)

        val savedSettings = prefs.settings.value
        assertEquals(1.44f, savedSettings.defaultPlaybackSpeed, 0.001f)
        assertTrue(savedSettings.defaultSkipSilence)
        assertEquals(2, savedSettings.defaultSilenceIntensity)
        assertEquals(AudioMonoMode.AUTO, savedSettings.monoMode)
        assertTrue(savedSettings.audioNormalizationEnabled)
        assertEquals(15, savedSettings.smartRewindSeconds)

        val b1After = dao.getAudiobook(book1Id)!!
        val b2After = dao.getAudiobook(book2Id)!!
        listOf(b1After, b2After).forEach { book ->
            assertEquals(1.44f, book.playbackSpeed, 0.001f)
            assertTrue(book.skipSilence)
            assertEquals(2, book.silenceIntensity)
            assertEquals(SpeedOverride.INHERIT, book.speedOverride, 0.001f)
            assertEquals(SilenceOverride.INHERIT, book.silenceModeOverride)
            assertEquals(AudioMonoMode.INHERIT, book.monoModeOverride)
            assertEquals(AudioOverride.INHERIT, book.audioNormalizationOverride)
            assertEquals(SmartRewindOverride.INHERIT, book.smartRewindOverride)
        }
    }

    @Test
    fun updateBookCoverFromBytesWritesLocalCoverAndUpdatesArtworkUrl() = runTest {
        val bookId = dao.insertAudiobook(
            AudiobookEntity(folderKey = "local://cov", folderName = "Cov", title = "Cov")
        )
        assertFalse(repo.updateBookCoverFromBytes(bookId, ByteArray(0)))

        val payload = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        assertTrue(repo.updateBookCoverFromBytes(bookId, payload))
        val updated = dao.getAudiobook(bookId)!!
        assertTrue(updated.artworkUrl.isNotBlank())
        assertTrue(updated.metadataLockedByUser)
        val coverFile = File(updated.artworkUrl)
        assertTrue(coverFile.exists())
        assertEquals(4L, coverFile.length())
    }

    @Test
    fun runTrashMaintenanceRespectsAutoDeleteTrashToggle() = runTest {
        val expiredTimestamp = System.currentTimeMillis() - 72L * 3600_000L
        val bookId = dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://trash-test",
                folderName = "Trash Test",
                title = "Trash Test",
                deletedAtMs = expiredTimestamp
            )
        )

        prefs.setTrashRetentionHours(-1)
        repo.runTrashMaintenance()
        assertEquals(bookId, dao.getAudiobook(bookId)?.id)

        prefs.setTrashRetentionHours(48)
        repo.runTrashMaintenance()
        assertNull(dao.getAudiobook(bookId))
    }

    @Test
    fun splitAudiobookByAlbumTagMigratesBookmarksToSplitBooks() = runTest {
        val bookId = dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://split-src",
                folderDocId = "split-doc",
                folderUri = "content://tree/root",
                folderName = "Anthology",
                title = "Anthology"
            )
        )
        dao.insertTracks(
            listOf(
                ChapterTrackEntity(
                    audiobookId = bookId,
                    guid = "sp-1",
                    title = "Book A - Ch 1",
                    sortOrder = 0,
                    trackNumber = 1,
                    fileUri = "content://split/1",
                    albumTag = "Book Alpha",
                    artistTag = "Author One",
                    durationMs = 60_000L
                ),
                ChapterTrackEntity(
                    audiobookId = bookId,
                    guid = "sp-2",
                    title = "Book B - Ch 1",
                    sortOrder = 1,
                    trackNumber = 2,
                    fileUri = "content://split/2",
                    albumTag = "Book Beta",
                    artistTag = "Author Two",
                    durationMs = 90_000L
                )
            )
        )
        val tracks = dao.getTracksForAudiobook(bookId)
        dao.insertBookmark(
            BookmarkEntity(
                audiobookId = bookId,
                trackId = tracks[0].id,
                trackTitle = tracks[0].title,
                positionMs = 12_345L,
                note = "Alpha mark",
                createdAtMs = 100L
            )
        )
        dao.insertBookmark(
            BookmarkEntity(
                audiobookId = bookId,
                trackId = tracks[1].id,
                trackTitle = tracks[1].title,
                positionMs = 54_321L,
                note = "Beta mark",
                createdAtMs = 200L
            )
        )

        val created = repo.splitAudiobookByAlbumTag(bookId)
        assertEquals(2, created)
        assertNull(dao.getAudiobook(bookId))

        val alphaBook = dao.getAudiobookByFolderKey("local://split-doc::Book Alpha")!!
        val betaBook = dao.getAudiobookByFolderKey("local://split-doc::Book Beta")!!
        val alphaTracks = dao.getTracksForAudiobook(alphaBook.id)
        val betaTracks = dao.getTracksForAudiobook(betaBook.id)

        val alphaBookmarks = dao.getBookmarksForAudiobookOnce(alphaBook.id)
        val betaBookmarks = dao.getBookmarksForAudiobookOnce(betaBook.id)
        assertEquals(1, alphaBookmarks.size)
        assertEquals(alphaTracks[0].id, alphaBookmarks[0].trackId)
        assertEquals(12_345L, alphaBookmarks[0].positionMs)
        assertEquals("Alpha mark", alphaBookmarks[0].note)

        assertEquals(1, betaBookmarks.size)
        assertEquals(betaTracks[0].id, betaBookmarks[0].trackId)
        assertEquals(54_321L, betaBookmarks[0].positionMs)
        assertEquals("Beta mark", betaBookmarks[0].note)
    }
}

package com.lycoslane.pureaudiobooks.domain.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudiobookRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: PureAudiobooksDatabase
    private lateinit var dao: PureAudiobooksDao
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
        repo = AudiobookRepository(context, dao, AppPreferences(context))
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
}

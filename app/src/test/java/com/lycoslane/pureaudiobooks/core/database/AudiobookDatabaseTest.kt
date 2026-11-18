package com.lycoslane.pureaudiobooks.core.database

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
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

        // Trash lifecycle
        dao.setDeletedAt(bookId, 5_000L)
        val expired = dao.getExpiredTrashedAudiobooks(10_000L)
        assertEquals(1, expired.size)
        assertNotNull(dao.getAudiobook(bookId))

        dao.deleteAudiobook(bookId)
        assertEquals(0, dao.getTracksForAudiobook(bookId).size)
        assertEquals(0, dao.getAllBookmarksOnce().size)
    }

    private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)
}

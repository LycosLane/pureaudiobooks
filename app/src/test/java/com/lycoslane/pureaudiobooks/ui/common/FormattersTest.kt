package com.lycoslane.pureaudiobooks.ui.common

import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun calculatesBookWideProgressWithActiveTrack() {
        val tracks = listOf(
            ChapterTrackEntity(id = 1, audiobookId = 1, guid = "1", title = "1", fileUri = "u1", durationMs = 100_000L, isFinished = true),
            ChapterTrackEntity(id = 2, audiobookId = 1, guid = "2", title = "2", fileUri = "u2", durationMs = 100_000L, positionMs = 20_000L),
            ChapterTrackEntity(id = 3, audiobookId = 1, guid = "3", title = "3", fileUri = "u3", durationMs = 200_000L)
        )
        val summary = Formatters.calculateBookProgress(tracks, activeTrackId = 2L, activePositionMs = 50_000L)
        assertEquals(400_000L, summary.totalDurationMs)
        assertEquals(150_000L, summary.listenedMs)
        assertEquals(250_000L, summary.remainingMs)
        assertEquals(1, summary.finishedTracks)
        assertEquals(3, summary.totalTracks)
        assertEquals(1, summary.currentTrackIndex)
        assertEquals(0.375f, summary.fraction, 0.001f)
    }

    @Test
    fun jumpingToLaterTrackCountsEarlierTracksInBookProgress() {
        val tracks = (1..10).map { i ->
            ChapterTrackEntity(
                id = i.toLong(),
                audiobookId = 1,
                guid = "$i",
                title = "Track $i",
                fileUri = "u$i",
                durationMs = 100_000L,
                isFinished = false
            )
        }
        // User jumps directly to Track 4 (id = 4) at 50_000ms
        val summary = Formatters.calculateBookProgress(
            tracks = tracks,
            activeTrackId = null,
            activePositionMs = 0L,
            fallbackTrackId = 4L
        )
        assertEquals(1_000_000L, summary.totalDurationMs)
        assertEquals(300_000L, summary.listenedMs) // Tracks 1..3 counted
        assertEquals(0.30f, summary.fraction, 0.001f)
        assertEquals(3, summary.currentTrackIndex)
    }
}

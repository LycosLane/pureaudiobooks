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
        assertEquals(0.375f, summary.fraction, 0.001f)
    }
}

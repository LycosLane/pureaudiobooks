package com.lycoslane.pureaudiobooks.ui.common

import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
        val summary = Formatters.calculateBookProgress(
            tracks = tracks,
            activeTrackId = null,
            activePositionMs = 0L,
            fallbackTrackId = 4L
        )
        assertEquals(1_000_000L, summary.totalDurationMs)
        assertEquals(300_000L, summary.listenedMs)
        assertEquals(0.30f, summary.fraction, 0.001f)
        assertEquals(3, summary.currentTrackIndex)
    }

    @Test
    fun calculatesEffectiveRemainingTimeAndSpeedFormatting() {
        assertEquals("1.0x", Formatters.formatPlaybackSpeed(1.00f, Locale.US))
        assertEquals("1.2x", Formatters.formatPlaybackSpeed(1.20f, Locale.US))
        assertEquals("1.24x", Formatters.formatPlaybackSpeed(1.24f, Locale.US))
        assertFalse(Formatters.hasCustomSpeed(1.00f))
        assertTrue(Formatters.hasCustomSpeed(1.20f))

        // 2 hours raw at 2.0x = 1 hour effective
        assertEquals(3_600_000L, Formatters.effectiveDurationMs(7_200_000L, 2.0f))
        // 1 hour raw at 0.5x = 2 hours effective
        assertEquals(7_200_000L, Formatters.effectiveDurationMs(3_600_000L, 0.5f))
    }

    @Test
    fun distinguishesInProgressAndUnstartedAudiobooks() {
        val unstarted = AudiobookWithTracks(
            audiobook = AudiobookEntity(id = 1, folderKey = "k1", folderName = "New", title = "New"),
            tracks = listOf(ChapterTrackEntity(id = 1, audiobookId = 1, guid = "g1", title = "T1", fileUri = "u1"))
        )
        assertFalse(Formatters.isBookInProgress(unstarted))

        val startedByPos = unstarted.copy(
            tracks = listOf(ChapterTrackEntity(id = 1, audiobookId = 1, guid = "g1", title = "T1", fileUri = "u1", positionMs = 1_000L))
        )
        assertTrue(Formatters.isBookInProgress(startedByPos))

        val finishedBook = startedByPos.copy(
            audiobook = startedByPos.audiobook.copy(isFinished = true)
        )
        assertFalse(Formatters.isBookInProgress(finishedBook))
    }
}

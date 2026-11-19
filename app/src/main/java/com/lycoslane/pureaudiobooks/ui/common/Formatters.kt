package com.lycoslane.pureaudiobooks.ui.common

import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class BookProgressSummary(
    val totalDurationMs: Long,
    val listenedMs: Long,
    val remainingMs: Long,
    val fraction: Float,
    val finishedTracks: Int,
    val totalTracks: Int,
    val currentTrackIndex: Int
)

object Formatters {

    fun formatDuration(ms: Long): String {
        val totalSecs = (ms.coerceAtLeast(0L) / 1000L)
        val hours = totalSecs / 3600L
        val mins = (totalSecs % 3600L) / 60L
        val secs = totalSecs % 60L
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, mins, secs)
        } else {
            "%02d:%02d".format(mins, secs)
        }
    }

    fun formatCompactHoursMinutes(ms: Long): String {
        val totalMins = (ms.coerceAtLeast(0L) / 60_000L)
        val hours = totalMins / 60L
        val mins = totalMins % 60L
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    fun formatPlaybackSpeed(speed: Float, locale: Locale = Locale.getDefault()): String {
        val hundredths = (speed.coerceIn(0.50f, 3.00f) * 100f).roundToInt()
        val normalized = hundredths / 100f
        return if (hundredths % 10 == 0) {
            String.format(locale, "%.1fx", normalized)
        } else {
            String.format(locale, "%.2fx", normalized)
        }
    }

    fun hasCustomSpeed(speed: Float): Boolean = abs(speed - 1.0f) >= 0.01f

    fun effectiveDurationMs(rawMs: Long, playbackSpeed: Float): Long {
        if (rawMs <= 0L) return 0L
        val safeSpeed = playbackSpeed.coerceIn(0.50f, 3.00f)
        return (rawMs.toDouble() / safeSpeed.toDouble()).roundToLong().coerceAtLeast(0L)
    }

    fun sortedTracks(tracks: List<ChapterTrackEntity>): List<ChapterTrackEntity> =
        tracks.sortedWith(
            compareBy<ChapterTrackEntity> { it.sortOrder }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        )

    fun isBookInProgress(item: AudiobookWithTracks): Boolean {
        val book = item.audiobook
        if (book.isFinished) return false
        return book.lastTrackId > 0L || item.tracks.any { it.positionMs > 0L || it.isFinished }
    }

    fun calculateBookProgress(
        tracks: List<ChapterTrackEntity>,
        activeTrackId: Long? = null,
        activePositionMs: Long = 0L,
        fallbackTrackId: Long = 0L
    ): BookProgressSummary {
        if (tracks.isEmpty()) {
            return BookProgressSummary(0L, 0L, 0L, 0f, 0, 0, 0)
        }
        val effectiveTrackId = when {
            activeTrackId != null && tracks.any { it.id == activeTrackId } -> activeTrackId
            fallbackTrackId > 0L && tracks.any { it.id == fallbackTrackId } -> fallbackTrackId
            else -> null
        }
        val currentIdx = if (effectiveTrackId != null) {
            tracks.indexOfFirst { it.id == effectiveTrackId }
        } else {
            tracks.indexOfFirst { !it.isFinished && it.positionMs > 0L }
                .takeIf { it >= 0 }
                ?: tracks.indexOfFirst { !it.isFinished }.coerceAtLeast(0)
        }

        var total = 0L
        var listened = 0L
        var finishedCount = 0

        tracks.forEachIndexed { idx, track ->
            val dur = track.durationMs.coerceAtLeast(0L)
            total += dur
            val isBeforeCurrent = effectiveTrackId != null && idx < currentIdx
            when {
                track.isFinished || isBeforeCurrent -> {
                    listened += dur
                    finishedCount++
                }
                track.id == activeTrackId -> {
                    listened += activePositionMs.coerceIn(0L, if (dur > 0L) dur else activePositionMs)
                }
                else -> {
                    listened += track.positionMs.coerceIn(0L, if (dur > 0L) dur else track.positionMs)
                }
            }
        }

        val remaining = (total - listened).coerceAtLeast(0L)
        val fraction = if (total > 0L) (listened.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
        return BookProgressSummary(
            totalDurationMs = total,
            listenedMs = listened,
            remainingMs = remaining,
            fraction = fraction,
            finishedTracks = finishedCount,
            totalTracks = tracks.size,
            currentTrackIndex = currentIdx.coerceAtLeast(0)
        )
    }
}

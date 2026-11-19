package com.lycoslane.pureaudiobooks.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.PlayerState
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.ui.common.BookProgressSummary
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import com.lycoslane.pureaudiobooks.ui.player.PlayerControlPanel
import com.lycoslane.pureaudiobooks.ui.player.PlayerPrimaryControlsBar

@Composable
fun BookDetailBottomBar(
    book: AudiobookEntity,
    tracks: List<ChapterTrackEntity>,
    currentTrack: ChapterTrackEntity?,
    progress: BookProgressSummary,
    playerState: PlayerState,
    isThisBookActive: Boolean,
    isThisBookPlaying: Boolean,
    currentSpeed: Float,
    currentSilence: Boolean,
    currentSilenceIntensity: Int,
    globalMonoMode: Int,
    currentTrackMonoOverride: Int,
    currentEffectiveMono: Int,
    activePanel: PlayerControlPanel,
    onTogglePanel: (PlayerControlPanel) -> Unit,
    onOpenBookmarkDialog: () -> Unit,
    onPlayBook: (ChapterTrackEntity?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipTrack: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSilenceChange: (Boolean, Int) -> Unit,
    onSetTrackMonoOverride: (Long, Int) -> Unit,
    onSetBookMonoOverride: (Int) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfTrack: (Boolean) -> Unit
) {
    val trackPosMs = if (isThisBookActive) playerState.positionMs else (currentTrack?.positionMs ?: 0L)
    val trackDurMs = if (isThisBookActive && playerState.durationMs > 0L) {
        playerState.durationMs
    } else {
        (currentTrack?.durationMs ?: 0L).coerceAtLeast(1L)
    }
    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderValue by remember(trackPosMs) { mutableFloatStateOf(trackPosMs.toFloat()) }

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (currentTrack != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (progress.totalTracks > 1) {
                            "${stringResource(R.string.chapter_prefix, progress.currentTrackIndex + 1, progress.totalTracks)} · ${currentTrack.title}"
                        } else {
                            currentTrack.title
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onOpenBookmarkDialog,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(R.string.add_bookmark),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            PlayerPrimaryControlsBar(
                bookId = book.id,
                trackId = currentTrack?.id ?: 0L,
                playbackSpeed = currentSpeed,
                skipSilence = currentSilence,
                silenceIntensity = currentSilenceIntensity,
                globalMonoMode = globalMonoMode,
                bookMonoOverride = book.monoModeOverride,
                trackMonoOverride = currentTrackMonoOverride,
                effectiveMonoMode = currentEffectiveMono,
                sleepTimerRemainingMs = playerState.sleepTimerRemainingMs,
                sleepTimerPresetMinutes = playerState.sleepTimerPresetMinutes,
                sleepAtEndOfTrack = playerState.sleepAtEndOfTrack,
                remainingRawMs = progress.remainingMs,
                activePanel = activePanel,
                panelAboveTiles = true,
                onTogglePanel = onTogglePanel,
                onSetSpeed = onSpeedChange,
                onSetSilenceMode = onSilenceChange,
                onSetTrackMonoOverride = { mode ->
                    if (currentTrack != null) onSetTrackMonoOverride(currentTrack.id, mode)
                },
                onSetBookMonoOverride = onSetBookMonoOverride,
                onSetSleepMinutes = onSleepMinutes,
                onSetSleepEndOfTrack = onSleepEndOfTrack
            )

            Spacer(Modifier.height(6.dp))

            Slider(
                value = (if (isSeeking) seekSliderValue else trackPosMs.toFloat()).coerceIn(0f, trackDurMs.toFloat()),
                onValueChange = {
                    isSeeking = true
                    seekSliderValue = it
                },
                onValueChangeFinished = {
                    isSeeking = false
                    if (isThisBookActive) {
                        onSeekTo(seekSliderValue.toLong())
                    } else {
                        onPlayBook(currentTrack)
                        onSeekTo(seekSliderValue.toLong())
                    }
                },
                valueRange = 0f..trackDurMs.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Formatters.formatDuration(if (isSeeking) seekSliderValue.toLong() else trackPosMs),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = Formatters.formatDuration(trackDurMs),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isThisBookActive) onSkipTrack(false)
                        else tracks.getOrNull((progress.currentTrackIndex - 1).coerceAtLeast(0))?.let { onPlayBook(it) }
                    },
                    enabled = tracks.size > 1
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.cd_prev_chapter))
                }
                IconButton(
                    onClick = {
                        if (isThisBookActive) onSeekBy(-10_000L)
                        else {
                            onPlayBook(currentTrack)
                            onSeekBy(-10_000L)
                        }
                    }
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.cd_rewind_10))
                }
                FilledIconButton(
                    onClick = {
                        if (isThisBookActive) onTogglePlayPause() else onPlayBook(null)
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isThisBookPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isThisBookPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (isThisBookActive) onSeekBy(30_000L)
                        else {
                            onPlayBook(currentTrack)
                            onSeekBy(30_000L)
                        }
                    }
                ) {
                    Icon(Icons.Default.Forward30, contentDescription = stringResource(R.string.cd_forward_30))
                }
                IconButton(
                    onClick = {
                        if (isThisBookActive) onSkipTrack(true)
                        else tracks.getOrNull((progress.currentTrackIndex + 1).coerceAtMost(tracks.lastIndex.coerceAtLeast(0)))?.let { onPlayBook(it) }
                    },
                    enabled = tracks.size > 1
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.cd_next_chapter))
                }
            }
        }
    }
}

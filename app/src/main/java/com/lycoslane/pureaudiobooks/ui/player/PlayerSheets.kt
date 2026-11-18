package com.lycoslane.pureaudiobooks.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.PlayerState
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage
import com.lycoslane.pureaudiobooks.ui.common.Formatters

@Composable
fun MiniPlayer(
    state: PlayerState,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book = state.currentBook ?: return
    val track = state.currentTrack ?: return
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverImage(
                    url = book.artworkUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (state.totalBookTracks > 1) {
                            "${state.currentTrackIndex + 1}/${state.totalBookTracks} · ${track.title}"
                        } else {
                            track.title
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.cd_pause else R.string.cd_play
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipTrack: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSilenceChange: (Boolean, Int) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfTrack: (Boolean) -> Unit,
    onCancelSleep: () -> Unit,
    onAddBookmark: (String) -> Unit
) {
    val book = state.currentBook ?: return
    val track = state.currentTrack ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSleepDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderValue by remember(state.positionMs) {
        mutableFloatStateOf(state.positionMs.toFloat())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BookCoverImage(
                url = book.artworkUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.totalBookTracks > 1) {
                    "${stringResource(R.string.chapter_prefix, state.currentTrackIndex + 1, state.totalBookTracks)} · ${track.title}"
                } else {
                    track.title
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            val maxDuration = state.durationMs.coerceAtLeast(1L).toFloat()
            Slider(
                value = (if (isSeeking) seekSliderValue else state.positionMs.toFloat()).coerceIn(0f, maxDuration),
                onValueChange = {
                    isSeeking = true
                    seekSliderValue = it
                },
                onValueChangeFinished = {
                    isSeeking = false
                    onSeekTo(seekSliderValue.toLong())
                },
                valueRange = 0f..maxDuration,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Formatters.formatDuration(if (isSeeking) seekSliderValue.toLong() else state.positionMs),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = Formatters.formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSkipTrack(false) }, enabled = state.totalBookTracks > 1) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.cd_prev_chapter))
                }
                IconButton(onClick = { onSeekBy(-10_000L) }) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.cd_rewind_10))
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { onSeekBy(30_000L) }) {
                    Icon(Icons.Default.Forward30, contentDescription = stringResource(R.string.cd_forward_30))
                }
                IconButton(onClick = { onSkipTrack(true) }, enabled = state.totalBookTracks > 1) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.cd_next_chapter))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Speed & Silence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.playback_speed_label, state.playbackSpeed),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showBookmarkDialog = true }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_bookmark))
                    }
                    OutlinedButton(onClick = { showSleepDialog = true }) {
                        Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        val label = when {
                            state.sleepAtEndOfTrack -> stringResource(R.string.sleep_end_of_chapter_short)
                            state.sleepTimerRemainingMs > 0L -> Formatters.formatDuration(state.sleepTimerRemainingMs)
                            else -> stringResource(R.string.sleep_timer)
                        }
                        Text(label)
                    }
                }
            }

            Slider(
                value = state.playbackSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.75f..3.0f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.skip_silence), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = state.skipSilence,
                    onCheckedChange = { onSilenceChange(it, state.silenceIntensity) }
                )
            }

            if (state.skipSilence) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to R.string.silence_soft,
                        1 to R.string.silence_medium,
                        2 to R.string.silence_strong
                    ).forEach { (tier, resId) ->
                        FilterChip(
                            selected = state.silenceIntensity == tier,
                            onClick = { onSilenceChange(true, tier) },
                            label = { Text(stringResource(resId)) }
                        )
                    }
                }
            }
        }
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text(stringResource(R.string.sleep_timer)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 30, 45, 60).forEach { mins ->
                        TextButton(
                            onClick = {
                                onSleepMinutes(mins)
                                showSleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.sleep_minutes_option, mins))
                        }
                    }
                    TextButton(
                        onClick = {
                            onSleepEndOfTrack(true)
                            showSleepDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sleep_end_of_chapter))
                    }
                    if (state.sleepTimerRemainingMs > 0L || state.sleepAtEndOfTrack) {
                        TextButton(
                            onClick = {
                                onCancelSleep()
                                showSleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.sleep_cancel))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text(stringResource(R.string.add_bookmark)) },
            text = {
                OutlinedTextField(
                    value = bookmarkNote,
                    onValueChange = { bookmarkNote = it },
                    label = { Text(stringResource(R.string.bookmark_note_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddBookmark(bookmarkNote)
                    bookmarkNote = ""
                    showBookmarkDialog = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

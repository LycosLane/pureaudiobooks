package com.lycoslane.pureaudiobooks.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.PlayerState
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import com.lycoslane.pureaudiobooks.ui.detail.AudiobookSettingsSheet
import com.lycoslane.pureaudiobooks.ui.detail.ChaptersSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    state: PlayerState,
    globalMonoMode: Int,
    globalAudioNormalization: Boolean,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipTrack: (Boolean) -> Unit,
    onPlayTrack: (ChapterTrackEntity) -> Unit,
    onToggleTrackFinished: (Long, Boolean) -> Unit,
    onMarkTracksUpToFinished: (Long) -> Unit,
    onMarkTracksFromUnfinished: (Long) -> Unit,
    onDeleteTracksUpTo: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSilenceChange: (Boolean, Int) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfTrack: (Boolean) -> Unit,
    onSetTrackMonoOverride: (Int) -> Unit,
    onSetBookMonoOverride: (Int) -> Unit,
    onSetBookNormalizationOverride: (Int) -> Unit,
    onSetBookHidden: (Boolean) -> Unit,
    onMarkBookFinished: (Boolean) -> Unit,
    onResetBookProgress: () -> Unit,
    onMoveBookToTrash: () -> Unit,
    onAddBookmark: (String) -> Unit
) {
    val book = state.currentBook ?: return
    val track = state.currentTrack ?: return
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var activePanel by rememberSaveable { mutableStateOf(PlayerControlPanel.NONE) }
    var showChaptersSheet by rememberSaveable { mutableStateOf(false) }
    var showBookSettings by rememberSaveable { mutableStateOf(false) }
    var showBookmarkDialog by rememberSaveable { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    var scrubPos by remember(track.id) { mutableStateOf<Float?>(null) }

    fun expandSheet() {
        scope.launch { runCatching { sheetState.expand() } }
    }

    val bookProgress = remember(state.bookTracks, track.id, state.positionMs, book.lastTrackId) {
        if (state.bookTracks.isNotEmpty()) {
            Formatters.calculateBookProgress(
                tracks = state.bookTracks,
                activeTrackId = track.id,
                activePositionMs = state.positionMs,
                fallbackTrackId = book.lastTrackId
            )
        } else {
            null
        }
    }
    val rawRemainingMs = bookProgress?.remainingMs ?: (state.durationMs - state.positionMs).coerceAtLeast(0L)
    val effectiveRemainingMs = Formatters.effectiveDurationMs(rawRemainingMs, state.playbackSpeed)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BookCoverImage(
                url = book.artworkUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
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
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (rawRemainingMs > 0L) {
                Spacer(Modifier.height(2.dp))
                val remLabel = if (Formatters.hasCustomSpeed(state.playbackSpeed)) {
                    stringResource(
                        R.string.remaining_time_at_speed,
                        Formatters.formatCompactHoursMinutes(effectiveRemainingMs),
                        Formatters.formatPlaybackSpeed(state.playbackSpeed)
                    )
                } else {
                    stringResource(
                        R.string.remaining_time_compact,
                        Formatters.formatCompactHoursMinutes(rawRemainingMs)
                    )
                }
                Text(
                    text = remLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            val maxDur = state.durationMs.coerceAtLeast(1L).toFloat()
            val displayPosMs = scrubPos?.toLong()
                ?: state.positionMs.coerceIn(0L, state.durationMs.coerceAtLeast(0L))
            val posFormatted = Formatters.formatDuration(displayPosMs)
            val durFormatted = Formatters.formatDuration(state.durationMs)

            Slider(
                value = displayPosMs.toFloat(),
                onValueChange = { scrubPos = it },
                onValueChangeFinished = {
                    scrubPos?.let { target ->
                        onSeekTo(target.toLong())
                        scrubPos = null
                    }
                },
                valueRange = 0f..maxDur,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = "$posFormatted / $durFormatted" }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(posFormatted, style = MaterialTheme.typography.labelSmall)
                Text(durFormatted, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSkipTrack(false) },
                    enabled = state.totalBookTracks > 1,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_prev_chapter),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { onSeekBy(-10_000L) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = stringResource(R.string.cd_rewind_10),
                        modifier = Modifier.size(32.dp)
                    )
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.cd_pause else R.string.cd_play
                        ),
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = { onSeekBy(30_000L) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Forward30,
                        contentDescription = stringResource(R.string.cd_forward_30),
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(
                    onClick = { onSkipTrack(true) },
                    enabled = state.totalBookTracks > 1,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.cd_next_chapter),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            PlayerPrimaryControlsBar(
                bookId = book.id,
                trackId = track.id,
                playbackSpeed = state.playbackSpeed,
                skipSilence = state.skipSilence,
                silenceIntensity = state.silenceIntensity,
                globalMonoMode = globalMonoMode,
                bookMonoOverride = state.bookMonoOverride,
                trackMonoOverride = state.trackMonoOverride,
                effectiveMonoMode = state.effectiveMonoMode,
                sleepTimerRemainingMs = state.sleepTimerRemainingMs,
                sleepTimerPresetMinutes = state.sleepTimerPresetMinutes,
                sleepAtEndOfTrack = state.sleepAtEndOfTrack,
                remainingRawMs = rawRemainingMs,
                activePanel = activePanel,
                onTogglePanel = { target ->
                    if (activePanel == target) {
                        activePanel = PlayerControlPanel.NONE
                    } else {
                        expandSheet()
                        activePanel = target
                    }
                },
                onSetSpeed = onSpeedChange,
                onSetSilenceMode = onSilenceChange,
                onSetTrackMonoOverride = onSetTrackMonoOverride,
                onSetBookMonoOverride = onSetBookMonoOverride,
                onSetSleepMinutes = onSleepMinutes,
                onSetSleepEndOfTrack = onSleepEndOfTrack
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerSecondaryTile(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    label = stringResource(R.string.player_chapters_short),
                    active = showChaptersSheet,
                    onClick = { showChaptersSheet = true },
                    modifier = Modifier.weight(1f)
                )
                PlayerSecondaryTile(
                    icon = Icons.Default.BookmarkAdd,
                    label = stringResource(R.string.add_bookmark),
                    active = showBookmarkDialog,
                    onClick = { showBookmarkDialog = true },
                    modifier = Modifier.weight(1f)
                )
                PlayerSecondaryTile(
                    icon = Icons.Default.Tune,
                    label = stringResource(R.string.player_book_short),
                    active = showBookSettings,
                    onClick = { showBookSettings = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showChaptersSheet) {
        ChaptersSheet(
            tracks = state.bookTracks,
            activeTrackId = track.id,
            fallbackTrackId = book.lastTrackId,
            activePositionMs = state.positionMs,
            folderUri = book.folderUri,
            onSelectTrack = { chosen ->
                showChaptersSheet = false
                onPlayTrack(chosen)
            },
            onToggleTrackFinished = onToggleTrackFinished,
            onMarkTracksUpToFinished = onMarkTracksUpToFinished,
            onMarkTracksFromUnfinished = onMarkTracksFromUnfinished,
            onDeleteTracksUpTo = { tid ->
                showChaptersSheet = false
                onDeleteTracksUpTo(tid)
            },
            onDismiss = { showChaptersSheet = false }
        )
    }

    if (showBookSettings) {
        AudiobookSettingsSheet(
            book = book,
            remainingRawMs = rawRemainingMs,
            globalMonoMode = globalMonoMode,
            globalAudioNormalization = globalAudioNormalization,
            onDismiss = { showBookSettings = false },
            onSetSpeed = onSpeedChange,
            onSetSilenceMode = onSilenceChange,
            onSetMonoModeOverride = onSetBookMonoOverride,
            onSetNormalizationOverride = onSetBookNormalizationOverride,
            onSetHidden = { hidden ->
                showBookSettings = false
                onSetBookHidden(hidden)
                if (hidden) onDismiss()
            },
            onMarkFinished = onMarkBookFinished,
            onResetProgress = {
                showBookSettings = false
                onResetBookProgress()
            },
            onMoveToTrash = {
                showBookSettings = false
                onMoveBookToTrash()
                onDismiss()
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

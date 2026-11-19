package com.lycoslane.pureaudiobooks.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.core.audio.PlayerState
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.database.BookmarkEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.network.OnlineBookMatch
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import com.lycoslane.pureaudiobooks.ui.common.PureSegmentedControl
import com.lycoslane.pureaudiobooks.ui.player.PlayerControlPanel
import com.lycoslane.pureaudiobooks.ui.player.PlayerPrimaryControlsBar

private enum class BookDetailTab {
    CHAPTERS, BOOKMARKS, INFO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    data: AudiobookWithTracks?,
    bookmarks: List<BookmarkEntity>,
    globalMonoMode: Int,
    globalAudioNormalization: Boolean,
    onlineLookupEnabled: Boolean,
    onlineResults: List<OnlineBookMatch>,
    isSearchingOnline: Boolean,
    playerState: PlayerState,
    onBack: () -> Unit,
    onPlayBook: (ChapterTrackEntity?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipTrack: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSilenceChange: (Boolean, Int) -> Unit,
    onSetTrackMonoOverride: (Long, Int) -> Unit,
    onSetBookMonoOverride: (Int) -> Unit,
    onSetBookNormalizationOverride: (Int) -> Unit,
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfTrack: (Boolean) -> Unit,
    onAddBookmark: (ChapterTrackEntity, Long, String) -> Unit,
    onToggleTrackFinished: (Long, Boolean) -> Unit,
    onMarkTracksUpToFinished: (Long) -> Unit,
    onMarkTracksFromUnfinished: (Long) -> Unit,
    onDeleteTracksUpTo: (Long) -> Unit,
    onMarkBookFinished: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onMoveToTrash: () -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onJumpToBookmark: (ChapterTrackEntity, Long) -> Unit,
    onSearchOnline: (String) -> Unit,
    onResolveMetadata: (String, String, String, String?) -> Unit,
    onSplitByAlbum: () -> Unit
) {
    val book = data?.audiobook
    val tracks = remember(data?.tracks) {
        Formatters.sortedTracks(data?.tracks.orEmpty())
    }
    val activeTrackId = playerState.currentTrack?.id
    val activePositionMs = playerState.positionMs
    val isPlaying = playerState.isPlaying

    var selectedTab by rememberSaveable { mutableStateOf(BookDetailTab.CHAPTERS) }
    var showMetadataSheet by rememberSaveable { mutableStateOf(false) }
    var showBookSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var activePanel by rememberSaveable { mutableStateOf(PlayerControlPanel.NONE) }
    var showBookmarkDialog by rememberSaveable { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    var chapterOptionsTarget by remember { mutableStateOf<Pair<Int, ChapterTrackEntity>?>(null) }

    val isThisBookActive = book != null && tracks.any { it.id == activeTrackId }
    val isThisBookPlaying = isPlaying && isThisBookActive
    val progress = remember(tracks, activeTrackId, activePositionMs, book?.lastTrackId) {
        Formatters.calculateBookProgress(
            tracks = tracks,
            activeTrackId = activeTrackId,
            activePositionMs = activePositionMs,
            fallbackTrackId = book?.lastTrackId ?: 0L
        )
    }
    val currentTrack = tracks.getOrNull(progress.currentTrackIndex)
    val currentSpeed = if (isThisBookActive) playerState.playbackSpeed else (book?.playbackSpeed ?: 1.0f)
    val currentSilence = if (isThisBookActive) playerState.skipSilence else (book?.skipSilence ?: false)
    val currentSilenceIntensity = if (isThisBookActive) playerState.silenceIntensity else (book?.silenceIntensity ?: 1)
    val currentTrackMonoOverride = currentTrack?.monoModeOverride ?: AudioMonoMode.INHERIT
    val currentEffectiveMono = if (isThisBookActive) {
        playerState.effectiveMonoMode
    } else if (book != null) {
        AudioMonoMode.resolveEffective(globalMonoMode, book.monoModeOverride, currentTrackMonoOverride)
    } else {
        AudioMonoMode.OFF
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = book?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (book != null) {
                        IconButton(onClick = { showBookSettingsSheet = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_book_settings))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (book != null) {
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
                                    onClick = { showBookmarkDialog = true },
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
                            onTogglePanel = { target ->
                                activePanel = if (activePanel == target) PlayerControlPanel.NONE else target
                            },
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
        }
    ) { padding ->
        if (book == null) return@Scaffold

        val effectiveRemainingMs = Formatters.effectiveDurationMs(progress.remainingMs, currentSpeed)
        val customSpeed = Formatters.hasCustomSpeed(currentSpeed)
        val effectiveTab = if (selectedTab == BookDetailTab.INFO && book.description.isBlank()) {
            BookDetailTab.CHAPTERS
        } else {
            selectedTab
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (book.hasTagConflict) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showMetadataSheet = true }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.tag_conflict_banner_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.tag_conflict_banner_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Compact Header: Cover + Title + Author
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverImage(
                    url = book.artworkUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (book.author.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Remaining-time & progress card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val remainingText = if (book.isFinished) {
                            stringResource(R.string.finished)
                        } else if (customSpeed) {
                            stringResource(
                                R.string.remaining_time_at_speed,
                                Formatters.formatCompactHoursMinutes(effectiveRemainingMs),
                                Formatters.formatPlaybackSpeed(currentSpeed)
                            )
                        } else {
                            stringResource(
                                R.string.remaining_time_compact,
                                Formatters.formatCompactHoursMinutes(progress.remainingMs)
                            )
                        }
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(progress.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(
                                R.string.listened_of_total,
                                Formatters.formatCompactHoursMinutes(progress.listenedMs),
                                Formatters.formatCompactHoursMinutes(progress.totalDurationMs)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (customSpeed && !book.isFinished) {
                                stringResource(
                                    R.string.real_time_remaining,
                                    Formatters.formatCompactHoursMinutes(progress.remainingMs)
                                )
                            } else {
                                stringResource(R.string.tracks_count, progress.totalTracks)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            val tabOptions = buildList {
                add(BookDetailTab.CHAPTERS to stringResource(R.string.tab_chapters, tracks.size))
                add(BookDetailTab.BOOKMARKS to stringResource(R.string.tab_bookmarks, bookmarks.size))
                if (book.description.isNotBlank()) {
                    add(BookDetailTab.INFO to stringResource(R.string.tab_info))
                }
            }
            PureSegmentedControl(
                options = tabOptions,
                selected = effectiveTab,
                onSelect = { selectedTab = it }
            )

            Spacer(Modifier.height(8.dp))

            when (effectiveTab) {
                BookDetailTab.CHAPTERS -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
                            val isCurrent = track.id == activeTrackId ||
                                (activeTrackId == null && track.id == book.lastTrackId)
                            ChapterItemRow(
                                track = track,
                                chapterIndex = idx,
                                isCurrent = isCurrent,
                                currentPosMs = if (track.id == activeTrackId) activePositionMs else track.positionMs,
                                onOpenOptions = { chapterOptionsTarget = idx to track },
                                onQuickPlay = { onPlayBook(track) }
                            )
                        }
                    }
                }

                BookDetailTab.BOOKMARKS -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentTrack != null) {
                            item(key = "add_bm_btn") {
                                OutlinedButton(
                                    onClick = { showBookmarkDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.add_bookmark))
                                }
                            }
                        }
                        if (bookmarks.isEmpty()) {
                            item(key = "empty_bm") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.empty_bookmarks),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(bookmarks, key = { it.id }) { bm ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val targetTrack = tracks.firstOrNull { it.id == bm.trackId }
                                            if (targetTrack != null) onJumpToBookmark(targetTrack, bm.positionMs)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${bm.trackTitle} · ${Formatters.formatDuration(bm.positionMs)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (bm.note.isNotBlank()) {
                                                Text(
                                                    text = bm.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_permanently))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                BookDetailTab.INFO -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = book.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBookmarkDialog && book != null) {
        val targetTrack = tracks.firstOrNull { it.id == activeTrackId }
            ?: tracks.firstOrNull { it.id == book.lastTrackId }
            ?: tracks.firstOrNull()
        val targetPos = if (tracks.any { it.id == activeTrackId }) playerState.positionMs else (targetTrack?.positionMs ?: 0L)
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
                    if (targetTrack != null) {
                        onAddBookmark(targetTrack, targetPos, bookmarkNote)
                    }
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

    chapterOptionsTarget?.let { (idx, targetTrack) ->
        if (book != null) {
            ChapterOptionsSheet(
                track = targetTrack,
                chapterIndex = idx,
                totalChapters = tracks.size,
                folderUri = book.folderUri,
                onPlay = { onPlayBook(targetTrack) },
                onToggleSingleFinished = { onToggleTrackFinished(targetTrack.id, !targetTrack.isFinished) },
                onMarkUpToHereFinished = { onMarkTracksUpToFinished(targetTrack.id) },
                onMarkFromHereUnfinished = { onMarkTracksFromUnfinished(targetTrack.id) },
                onDeleteUpToHere = { onDeleteTracksUpTo(targetTrack.id) },
                onDismiss = { chapterOptionsTarget = null }
            )
        }
    }

    if (showBookSettingsSheet && book != null) {
        AudiobookSettingsSheet(
            book = book,
            remainingRawMs = progress.remainingMs,
            globalMonoMode = globalMonoMode,
            globalAudioNormalization = globalAudioNormalization,
            onDismiss = { showBookSettingsSheet = false },
            onSetSpeed = onSpeedChange,
            onSetSilenceMode = onSilenceChange,
            onSetMonoModeOverride = onSetBookMonoOverride,
            onSetNormalizationOverride = onSetBookNormalizationOverride,
            onSetHidden = { hidden ->
                showBookSettingsSheet = false
                onSetHidden(hidden)
            },
            onMarkFinished = onMarkBookFinished,
            onResetProgress = {
                showBookSettingsSheet = false
                onResetProgress()
            },
            onMoveToTrash = {
                showBookSettingsSheet = false
                onMoveToTrash()
            },
            onEditMetadata = {
                showBookSettingsSheet = false
                showMetadataSheet = true
            }
        )
    }

    if (showMetadataSheet && book != null) {
        MetadataResolverSheet(
            book = book,
            onlineLookupEnabled = onlineLookupEnabled,
            onlineResults = onlineResults,
            isSearchingOnline = isSearchingOnline,
            onSearchOnline = onSearchOnline,
            onSplitByAlbum = {
                showMetadataSheet = false
                onSplitByAlbum()
            },
            onSave = { title, author, desc, coverUrl ->
                showMetadataSheet = false
                onResolveMetadata(title, author, desc, coverUrl)
            },
            onDismiss = { showMetadataSheet = false }
        )
    }
}

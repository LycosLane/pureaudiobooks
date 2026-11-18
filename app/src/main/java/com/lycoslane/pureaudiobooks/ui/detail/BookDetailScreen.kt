package com.lycoslane.pureaudiobooks.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.PlayerState
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.database.BookmarkEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.network.OnlineBookMatch
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    data: AudiobookWithTracks?,
    bookmarks: List<BookmarkEntity>,
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
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfTrack: (Boolean) -> Unit,
    onCancelSleep: () -> Unit,
    onAddBookmark: (ChapterTrackEntity, Long, String) -> Unit,
    onToggleTrackFinished: (Long, Boolean) -> Unit,
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
        data?.tracks?.sortedWith(
            compareBy<ChapterTrackEntity> { it.sortOrder }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        ).orEmpty()
    }
    val activeTrackId = playerState.currentTrack?.id
    val activePositionMs = playerState.positionMs
    val isPlaying = playerState.isPlaying

    var showMetadataSheet by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var showSpeedRow by remember { mutableStateOf(false) }
    var showSilenceRow by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }

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
                        IconButton(onClick = { showMetadataSheet = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_metadata))
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more))
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (book.isFinished) R.string.mark_unfinished else R.string.mark_finished
                                            )
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onMarkBookFinished(!book.isFinished)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reset_progress)) },
                                    onClick = {
                                        showMoreMenu = false
                                        onResetProgress()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (book.isHidden) R.string.unhide_book else R.string.hide_book
                                            )
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onSetHidden(!book.isHidden)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.move_to_trash),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onMoveToTrash()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (book == null) return@Scaffold

        val isThisBookPlaying = isPlaying && tracks.any { it.id == activeTrackId }
        val progress = Formatters.calculateBookProgress(
            tracks = tracks,
            activeTrackId = activeTrackId,
            activePositionMs = activePositionMs,
            fallbackTrackId = book.lastTrackId
        )
        val currentTrack = tracks.getOrNull(progress.currentTrackIndex)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BookCoverImage(
                url = book.artworkUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Book-wide progress card
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(progress.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${Formatters.formatCompactHoursMinutes(progress.remainingMs)} ${stringResource(R.string.remaining_suffix)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    if (currentTrack != null) {
                        Text(
                            text = "${stringResource(R.string.chapter_prefix, progress.currentTrackIndex + 1, progress.totalTracks)} · ${currentTrack.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val isThisBookActive = tracks.any { it.id == activeTrackId }
            val trackPosMs = if (isThisBookActive) playerState.positionMs else (currentTrack?.positionMs ?: 0L)
            val trackDurMs = if (isThisBookActive && playerState.durationMs > 0L) {
                playerState.durationMs
            } else {
                (currentTrack?.durationMs ?: 0L).coerceAtLeast(1L)
            }
            var isSeeking by remember { mutableStateOf(false) }
            var seekSliderValue by remember(trackPosMs) { mutableFloatStateOf(trackPosMs.toFloat()) }

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
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (isThisBookPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isThisBookPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(34.dp)
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

            val currentSpeed = if (isThisBookActive) playerState.playbackSpeed else book.playbackSpeed
            val currentSilence = if (isThisBookActive) playerState.skipSilence else book.skipSilence
            val currentSilenceIntensity = if (isThisBookActive) playerState.silenceIntensity else book.silenceIntensity

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = showSpeedRow,
                    onClick = {
                        showSpeedRow = !showSpeedRow
                        showSilenceRow = false
                    },
                    label = { Text(String.format(Locale.getDefault(), "%.2fx", currentSpeed)) },
                    leadingIcon = { Icon(Icons.Default.Speed, null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = currentSilence || showSilenceRow,
                    onClick = {
                        val next = !currentSilence
                        onSilenceChange(next, currentSilenceIntensity)
                        showSilenceRow = next
                        showSpeedRow = false
                    },
                    label = { Text(stringResource(R.string.player_silence)) },
                    leadingIcon = { Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = playerState.sleepTimerRemainingMs > 0L || playerState.sleepAtEndOfTrack,
                    onClick = { showSleepDialog = true },
                    label = {
                        Text(
                            when {
                                playerState.sleepAtEndOfTrack -> stringResource(R.string.sleep_end_of_chapter_short)
                                playerState.sleepTimerRemainingMs > 0L -> Formatters.formatDuration(playerState.sleepTimerRemainingMs)
                                else -> stringResource(R.string.sleep_timer)
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Bedtime, null, modifier = Modifier.size(16.dp)) }
                )
                if (currentTrack != null) {
                    FilterChip(
                        selected = false,
                        onClick = { showBookmarkDialog = true },
                        label = { Text(stringResource(R.string.add_bookmark)) },
                        leadingIcon = { Icon(Icons.Default.BookmarkAdd, null, modifier = Modifier.size(16.dp)) }
                    )
                }
                if (book.description.isNotBlank()) {
                    FilterChip(
                        selected = showDescription,
                        onClick = { showDescription = !showDescription },
                        label = { Text(stringResource(R.string.field_description)) },
                        leadingIcon = { Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp)) }
                    )
                }
                FilterChip(
                    selected = showChaptersSheet,
                    onClick = { showChaptersSheet = true },
                    label = { Text(stringResource(R.string.chapters_header, tracks.size)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, modifier = Modifier.size(16.dp)) }
                )
            }

            if (showSpeedRow) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f).forEach { speed ->
                        FilterChip(
                            selected = kotlin.math.abs(currentSpeed - speed) < 0.01f,
                            onClick = { onSpeedChange(speed) },
                            label = { Text(String.format(Locale.getDefault(), "%.2fx", speed)) }
                        )
                    }
                }
            }

            if (showSilenceRow && currentSilence) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0 to R.string.silence_soft,
                        1 to R.string.silence_medium,
                        2 to R.string.silence_strong
                    ).forEach { (tier, resId) ->
                        FilterChip(
                            selected = currentSilenceIntensity == tier,
                            onClick = { onSilenceChange(true, tier) },
                            label = { Text(stringResource(resId)) }
                        )
                    }
                }
            }

            if (showDescription && book.description.isNotBlank()) {
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = book.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (bookmarks.isNotEmpty()) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBookmarks = !showBookmarks },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.bookmarks_header, bookmarks.size),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = stringResource(if (showBookmarks) R.string.hide else R.string.show),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedVisibility(visible = showBookmarks) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                bookmarks.forEach { bm ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val targetTrack = tracks.firstOrNull { it.id == bm.trackId }
                                                if (targetTrack != null) {
                                                    onJumpToBookmark(targetTrack, bm.positionMs)
                                                }
                                            }
                                            .padding(vertical = 4.dp),
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
                    if (playerState.sleepTimerRemainingMs > 0L || playerState.sleepAtEndOfTrack) {
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

    if (showBookmarkDialog && book != null) {
        val targetTrack = if (tracks.any { it.id == activeTrackId }) {
            tracks.first { it.id == activeTrackId }
        } else {
            tracks.getOrNull(progress.currentTrackIndex)
        }
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

    if (showChaptersSheet && book != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showChaptersSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.chapters_header, tracks.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        val isCurrent = track.id == activeTrackId || (activeTrackId == null && track.id == book.lastTrackId)
                        TrackSwipeRow(
                            track = track,
                            isCurrent = isCurrent,
                            currentPosMs = if (track.id == activeTrackId) activePositionMs else track.positionMs,
                            onClick = {
                                showChaptersSheet = false
                                onPlayBook(track)
                            },
                            onDoubleTap = { onToggleTrackFinished(track.id, !track.isFinished) },
                            onSwipeToggleFinished = { onToggleTrackFinished(track.id, !track.isFinished) }
                        )
                    }
                }
            }
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackSwipeRow(
    track: ChapterTrackEntity,
    isCurrent: Boolean,
    currentPosMs: Long,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeToggleFinished: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var rowWidth by remember { mutableFloatStateOf(1f) }
    var triggeredHaptic by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidth = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        Surface(
            tonalElevation = if (isCurrent) 4.dp else 1.dp,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX / rowWidth >= 0.45f) {
                                onSwipeToggleFinished()
                            }
                            offsetX = 0f
                            triggeredHaptic = false
                        },
                        onDragCancel = {
                            offsetX = 0f
                            triggeredHaptic = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(0f, rowWidth * 0.6f)
                            val reached = offsetX / rowWidth >= 0.45f
                            if (reached && !triggeredHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                triggeredHaptic = true
                            }
                        }
                    )
                }
                .combinedClickable(
                    onClick = onClick,
                    onDoubleClick = onDoubleTap
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (track.isFinished) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val durText = Formatters.formatDuration(track.durationMs)
                    val posText = if (currentPosMs > 0L && !track.isFinished) {
                        "${Formatters.formatDuration(currentPosMs)} / $durText"
                    } else {
                        durText
                    }
                    Text(
                        text = posText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (track.isFinished) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.finished),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isCurrent) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

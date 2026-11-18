package com.lycoslane.pureaudiobooks.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.database.BookmarkEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.network.OnlineBookMatch
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookDetailScreen(
    data: AudiobookWithTracks?,
    bookmarks: List<BookmarkEntity>,
    onlineLookupEnabled: Boolean,
    onlineResults: List<OnlineBookMatch>,
    isSearchingOnline: Boolean,
    activeTrackId: Long?,
    activePositionMs: Long,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayBook: (ChapterTrackEntity?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleTrackFinished: (Long, Boolean) -> Unit,
    onMarkBookFinished: (Boolean) -> Unit,
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

    var showMetadataSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }

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
                                    text = { Text(stringResource(R.string.move_to_trash)) },
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
        val progress = Formatters.calculateBookProgress(tracks, activeTrackId, activePositionMs)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BookCoverImage(
                        url = book.artworkUrl,
                        contentDescription = book.title,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${progress.finishedTracks}/${progress.totalTracks} · ${Formatters.formatCompactHoursMinutes(progress.remainingMs)} ${stringResource(R.string.remaining_suffix)} (${(progress.fraction * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (tracks.any { it.id == activeTrackId }) onTogglePlayPause() else onPlayBook(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isThisBookPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (isThisBookPlaying) R.string.pause else R.string.play_resume
                        )
                    )
                }
            }

            if (book.hasTagConflict) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMetadataSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.tag_conflict_banner_title),
                                    style = MaterialTheme.typography.titleSmall,
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
            }

            if (book.description.isNotBlank()) {
                item {
                    Text(
                        text = book.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (bookmarks.isNotEmpty()) {
                item {
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

            item {
                Text(
                    text = stringResource(R.string.chapters_header, tracks.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(tracks, key = { it.id }) { track ->
                TrackSwipeRow(
                    track = track,
                    isCurrent = track.id == activeTrackId,
                    currentPosMs = if (track.id == activeTrackId) activePositionMs else track.positionMs,
                    onClick = { onPlayBook(track) },
                    onDoubleTap = { onToggleTrackFinished(track.id, !track.isFinished) },
                    onSwipeToggleFinished = { onToggleTrackFinished(track.id, !track.isFinished) }
                )
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

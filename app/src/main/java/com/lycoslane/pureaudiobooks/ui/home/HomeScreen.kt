package com.lycoslane.pureaudiobooks.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage
import com.lycoslane.pureaudiobooks.ui.common.Formatters

private enum class LibraryFilter {
    ALL, IN_PROGRESS, FINISHED, HIDDEN, CONFLICTS, TRASH
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    activeBooks: List<AudiobookWithTracks>,
    trashedBooks: List<AudiobookWithTracks>,
    hasFolderConfigured: Boolean,
    gridLayout: Boolean,
    isScanning: Boolean,
    activeTrackId: Long?,
    activePositionMs: Long,
    onToggleLayout: () -> Unit,
    onScan: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onMarkFinished: (Long, Boolean) -> Unit,
    onResetProgress: (Long) -> Unit,
    onSetHidden: (Long, Boolean) -> Unit,
    onMoveToTrash: (Long) -> Unit,
    onRestoreFromTrash: (Long) -> Unit,
    onPermanentlyDelete: (Long) -> Unit
) {
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var bookMenuTarget by remember { mutableStateOf<AudiobookWithTracks?>(null) }
    var confirmPermDelete by remember { mutableStateOf<AudiobookWithTracks?>(null) }

    val conflictCount = remember(activeBooks) {
        activeBooks.count { !it.audiobook.isHidden && it.audiobook.hasTagConflict }
    }
    val hiddenCount = remember(activeBooks) {
        activeBooks.count { it.audiobook.isHidden }
    }

    val displayedBooks = remember(activeBooks, trashedBooks, selectedFilter, searchQuery) {
        val base = when (selectedFilter) {
            // Main view hides finished and manually hidden audiobooks to stay uncluttered
            LibraryFilter.ALL -> activeBooks.filter { !it.audiobook.isFinished && !it.audiobook.isHidden }
            LibraryFilter.IN_PROGRESS -> activeBooks.filter { item ->
                !item.audiobook.isFinished && !item.audiobook.isHidden && (
                    item.audiobook.lastTrackId > 0L || item.tracks.any { t -> t.positionMs > 0L || t.isFinished }
                )
            }
            LibraryFilter.FINISHED -> activeBooks.filter { it.audiobook.isFinished && !it.audiobook.isHidden }
            LibraryFilter.HIDDEN -> activeBooks.filter { it.audiobook.isHidden }
            LibraryFilter.CONFLICTS -> activeBooks.filter { !it.audiobook.isHidden && it.audiobook.hasTagConflict }
            LibraryFilter.TRASH -> trashedBooks
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) base else base.filter {
            it.audiobook.title.contains(q, ignoreCase = true) ||
                it.audiobook.author.contains(q, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (hasFolderConfigured) {
                        IconButton(onClick = onScan, enabled = !isScanning) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_scan))
                            }
                        }
                    }
                    IconButton(onClick = onToggleLayout) {
                        Icon(
                            imageVector = if (gridLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = stringResource(R.string.cd_toggle_layout)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasFolderConfigured && activeBooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.empty_no_folder_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.empty_no_folder_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onPickFolder) {
                            Text(stringResource(R.string.pick_audiobooks_folder))
                        }
                    }
                }
                return@Scaffold
            }

            if (activeBooks.size > 4 || searchQuery.isNotBlank()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_library_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == LibraryFilter.ALL,
                    onClick = { selectedFilter = LibraryFilter.ALL },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                FilterChip(
                    selected = selectedFilter == LibraryFilter.IN_PROGRESS,
                    onClick = { selectedFilter = LibraryFilter.IN_PROGRESS },
                    label = { Text(stringResource(R.string.filter_in_progress)) }
                )
                FilterChip(
                    selected = selectedFilter == LibraryFilter.FINISHED,
                    onClick = { selectedFilter = LibraryFilter.FINISHED },
                    label = { Text(stringResource(R.string.filter_finished)) }
                )
                if (hiddenCount > 0 || selectedFilter == LibraryFilter.HIDDEN) {
                    FilterChip(
                        selected = selectedFilter == LibraryFilter.HIDDEN,
                        onClick = { selectedFilter = LibraryFilter.HIDDEN },
                        label = { Text(stringResource(R.string.filter_hidden)) }
                    )
                }
                if (conflictCount > 0 || selectedFilter == LibraryFilter.CONFLICTS) {
                    FilterChip(
                        selected = selectedFilter == LibraryFilter.CONFLICTS,
                        onClick = { selectedFilter = LibraryFilter.CONFLICTS },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.filter_conflicts))
                                if (conflictCount > 0) Badge { Text(conflictCount.toString()) }
                            }
                        }
                    )
                }
                if (trashedBooks.isNotEmpty() || selectedFilter == LibraryFilter.TRASH) {
                    FilterChip(
                        selected = selectedFilter == LibraryFilter.TRASH,
                        onClick = { selectedFilter = LibraryFilter.TRASH },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.filter_trash))
                                if (trashedBooks.isNotEmpty()) Badge { Text(trashedBooks.size.toString()) }
                            }
                        }
                    )
                }
            }

            if (displayedBooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty_filter_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (selectedFilter == LibraryFilter.TRASH) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedBooks, key = { it.audiobook.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BookCoverImage(
                                    url = item.audiobook.artworkUrl,
                                    contentDescription = item.audiobook.title,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.audiobook.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.audiobook.author.isNotBlank()) {
                                        Text(
                                            text = item.audiobook.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                IconButton(onClick = { onRestoreFromTrash(item.audiobook.id) }) {
                                    Icon(Icons.Default.RestoreFromTrash, contentDescription = stringResource(R.string.restore))
                                }
                                IconButton(onClick = { confirmPermDelete = item }) {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = stringResource(R.string.delete_permanently),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (gridLayout) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedBooks, key = { it.audiobook.id }) { item ->
                        val sortedTracks = remember(item.tracks) {
                            item.tracks.sortedWith(
                                compareBy<ChapterTrackEntity> { it.sortOrder }
                                    .thenBy { it.trackNumber }
                                    .thenBy { it.title }
                            )
                        }
                        val summary = Formatters.calculateBookProgress(
                            tracks = sortedTracks,
                            activeTrackId = activeTrackId,
                            activePositionMs = activePositionMs,
                            fallbackTrackId = item.audiobook.lastTrackId
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenBook(item.audiobook.id) },
                                    onLongClick = { bookMenuTarget = item }
                                )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Box {
                                    BookCoverImage(
                                        url = item.audiobook.artworkUrl,
                                        contentDescription = item.audiobook.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    if (item.audiobook.hasTagConflict) {
                                        Icon(
                                            imageVector = Icons.Default.WarningAmber,
                                            contentDescription = stringResource(R.string.filter_conflicts),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = item.audiobook.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.audiobook.author.ifBlank {
                                        stringResource(R.string.tracks_count, item.tracks.size)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { summary.fraction },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedBooks, key = { it.audiobook.id }) { item ->
                        val sortedTracks = remember(item.tracks) {
                            item.tracks.sortedWith(
                                compareBy<ChapterTrackEntity> { it.sortOrder }
                                    .thenBy { it.trackNumber }
                                    .thenBy { it.title }
                            )
                        }
                        val summary = Formatters.calculateBookProgress(
                            tracks = sortedTracks,
                            activeTrackId = activeTrackId,
                            activePositionMs = activePositionMs,
                            fallbackTrackId = item.audiobook.lastTrackId
                        )
                        Card(
                            colors = CardDefaults.cardColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenBook(item.audiobook.id) },
                                    onLongClick = { bookMenuTarget = item }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BookCoverImage(
                                    url = item.audiobook.artworkUrl,
                                    contentDescription = item.audiobook.title,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.audiobook.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (item.audiobook.hasTagConflict) {
                                            Icon(
                                                Icons.Default.WarningAmber,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (item.audiobook.author.isNotBlank()) {
                                        Text(
                                            text = item.audiobook.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { summary.fraction },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${(summary.fraction * 100).toInt()}% · ${Formatters.formatCompactHoursMinutes(summary.remainingMs)} ${stringResource(R.string.remaining_suffix)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    bookMenuTarget?.let { target ->
        val book = target.audiobook
        AlertDialog(
            onDismissRequest = { bookMenuTarget = null },
            title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            bookMenuTarget = null
                            onOpenBook(book.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.open_book)) }

                    TextButton(
                        onClick = {
                            bookMenuTarget = null
                            onMarkFinished(book.id, !book.isFinished)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (book.isFinished) R.string.mark_unfinished else R.string.mark_finished
                            )
                        )
                    }

                    TextButton(
                        onClick = {
                            bookMenuTarget = null
                            onResetProgress(book.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.reset_progress)) }

                    TextButton(
                        onClick = {
                            bookMenuTarget = null
                            onSetHidden(book.id, !book.isHidden)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (book.isHidden) R.string.unhide_book else R.string.hide_book
                            )
                        )
                    }

                    TextButton(
                        onClick = {
                            bookMenuTarget = null
                            onMoveToTrash(book.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.move_to_trash),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { bookMenuTarget = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    confirmPermDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmPermDelete = null },
            title = { Text(stringResource(R.string.delete_permanently)) },
            text = { Text(stringResource(R.string.delete_permanently_confirm, target.audiobook.title)) },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.audiobook.id
                    confirmPermDelete = null
                    onPermanentlyDelete(id)
                }) {
                    Text(stringResource(R.string.delete_permanently), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPermDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

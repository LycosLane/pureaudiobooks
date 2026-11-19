package com.lycoslane.pureaudiobooks.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import com.lycoslane.pureaudiobooks.ui.common.PureSegmentedControl

private enum class LibraryFilter {
    ACTIVE, FINISHED, HIDDEN, CONFLICTS, TRASH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    activeBooks: List<AudiobookWithTracks>,
    trashedBooks: List<AudiobookWithTracks>,
    hasFolderConfigured: Boolean,
    gridLayout: Boolean,
    showRefreshButton: Boolean,
    showSearchBar: Boolean,
    isScanning: Boolean,
    activeTrackId: Long?,
    activePositionMs: Long,
    isPlaying: Boolean,
    onToggleLayout: () -> Unit,
    onScan: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onPlayBook: (AudiobookWithTracks) -> Unit,
    onMarkFinished: (Long, Boolean) -> Unit,
    onResetProgress: (Long) -> Unit,
    onSetHidden: (Long, Boolean) -> Unit,
    onMoveToTrash: (Long) -> Unit,
    onRestoreFromTrash: (Long) -> Unit,
    onPermanentlyDelete: (Long) -> Unit
) {
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.ACTIVE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var bookMenuTarget by remember { mutableStateOf<AudiobookWithTracks?>(null) }
    var confirmPermDelete by remember { mutableStateOf<AudiobookWithTracks?>(null) }

    val q = if (showSearchBar) searchQuery.trim() else ""
    fun List<AudiobookWithTracks>.applyQuery(): List<AudiobookWithTracks> =
        if (q.isEmpty()) this else filter {
            it.audiobook.title.contains(q, ignoreCase = true) ||
                it.audiobook.author.contains(q, ignoreCase = true)
        }

    val visibleActive = remember(activeBooks) {
        activeBooks.filter { !it.audiobook.isFinished && !it.audiobook.isHidden }
    }
    val finishedBooks = remember(activeBooks, q) {
        activeBooks.filter { it.audiobook.isFinished && !it.audiobook.isHidden }.applyQuery()
    }
    val hiddenBooks = remember(activeBooks, q) {
        activeBooks.filter { it.audiobook.isHidden }.applyQuery()
    }
    val conflictBooks = remember(activeBooks, q) {
        activeBooks.filter { !it.audiobook.isHidden && it.audiobook.hasTagConflict }.applyQuery()
    }
    val filteredTrashed = remember(trashedBooks, q) {
        trashedBooks.applyQuery()
    }

    val conflictCount = remember(activeBooks) {
        activeBooks.count { !it.audiobook.isHidden && it.audiobook.hasTagConflict }
    }

    val inProgressBooks = remember(visibleActive, q) {
        visibleActive.filter { Formatters.isBookInProgress(it) }.applyQuery()
    }
    val unstartedBooks = remember(visibleActive, q) {
        visibleActive
            .filter { !Formatters.isBookInProgress(it) }
            .sortedBy { it.audiobook.title.lowercase() }
            .applyQuery()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    if (hasFolderConfigured && showRefreshButton) {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
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

            if (showSearchBar && (activeBooks.isNotEmpty() || searchQuery.isNotBlank())) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_library_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Primary listening-state segmented filter
            val primarySelected = if (selectedFilter in listOf(LibraryFilter.ACTIVE, LibraryFilter.FINISHED, LibraryFilter.HIDDEN)) {
                selectedFilter
            } else {
                null
            }
            PureSegmentedControl(
                options = listOf(
                    LibraryFilter.ACTIVE to stringResource(R.string.filter_active),
                    LibraryFilter.FINISHED to stringResource(R.string.filter_finished),
                    LibraryFilter.HIDDEN to stringResource(R.string.filter_hidden)
                ),
                selected = primarySelected,
                onSelect = { chosen ->
                    if (chosen != null) selectedFilter = chosen
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Contextual utility pills (Conflicts & Trash only shown when needed)
            if (conflictCount > 0 || trashedBooks.isNotEmpty() ||
                selectedFilter == LibraryFilter.CONFLICTS || selectedFilter == LibraryFilter.TRASH
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (conflictCount > 0 || selectedFilter == LibraryFilter.CONFLICTS) {
                        FilterChip(
                            selected = selectedFilter == LibraryFilter.CONFLICTS,
                            onClick = {
                                selectedFilter = if (selectedFilter == LibraryFilter.CONFLICTS) {
                                    LibraryFilter.ACTIVE
                                } else {
                                    LibraryFilter.CONFLICTS
                                }
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(stringResource(R.string.filter_conflicts))
                                    if (conflictCount > 0) Badge { Text(conflictCount.toString()) }
                                }
                            }
                        )
                    }
                    if (trashedBooks.isNotEmpty() || selectedFilter == LibraryFilter.TRASH) {
                        FilterChip(
                            selected = selectedFilter == LibraryFilter.TRASH,
                            onClick = {
                                selectedFilter = if (selectedFilter == LibraryFilter.TRASH) {
                                    LibraryFilter.ACTIVE
                                } else {
                                    LibraryFilter.TRASH
                                }
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(stringResource(R.string.filter_trash))
                                    if (trashedBooks.isNotEmpty()) Badge { Text(trashedBooks.size.toString()) }
                                }
                            }
                        )
                    }
                }
            }

            when (selectedFilter) {
                LibraryFilter.ACTIVE -> {
                    if (inProgressBooks.isEmpty() && unstartedBooks.isEmpty()) {
                        EmptyFilterMessage()
                    } else if (gridLayout) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(155.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (inProgressBooks.isNotEmpty()) {
                                item(key = "hdr_in_progress", span = { GridItemSpan(maxLineSpan) }) {
                                    LibrarySectionHeader(
                                        title = stringResource(R.string.section_continue_listening),
                                        count = inProgressBooks.size,
                                        trailing = stringResource(R.string.section_remaining_focus),
                                        highlighted = true
                                    )
                                }
                                items(inProgressBooks, key = { "prog_${it.audiobook.id}" }) { item ->
                                    InProgressBookGridCard(
                                        item = item,
                                        activeTrackId = activeTrackId,
                                        activePositionMs = activePositionMs,
                                        isPlaying = isPlaying,
                                        onOpen = { onOpenBook(item.audiobook.id) },
                                        onLongPress = { bookMenuTarget = item },
                                        onPlayToggle = { onPlayBook(item) }
                                    )
                                }
                            }
                            if (unstartedBooks.isNotEmpty()) {
                                item(key = "hdr_unstarted", span = { GridItemSpan(maxLineSpan) }) {
                                    LibrarySectionHeader(
                                        title = stringResource(R.string.section_not_started),
                                        count = unstartedBooks.size,
                                        trailing = stringResource(R.string.section_total_duration)
                                    )
                                }
                                items(unstartedBooks, key = { "new_${it.audiobook.id}" }) { item ->
                                    UnstartedBookGridCard(
                                        item = item,
                                        onOpen = { onOpenBook(item.audiobook.id) },
                                        onLongPress = { bookMenuTarget = item }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (inProgressBooks.isNotEmpty()) {
                                item(key = "hdr_in_progress") {
                                    LibrarySectionHeader(
                                        title = stringResource(R.string.section_continue_listening),
                                        count = inProgressBooks.size,
                                        trailing = stringResource(R.string.section_remaining_focus),
                                        highlighted = true
                                    )
                                }
                                items(inProgressBooks, key = { "prog_${it.audiobook.id}" }) { item ->
                                    InProgressBookListCard(
                                        item = item,
                                        activeTrackId = activeTrackId,
                                        activePositionMs = activePositionMs,
                                        isPlaying = isPlaying,
                                        onOpen = { onOpenBook(item.audiobook.id) },
                                        onLongPress = { bookMenuTarget = item },
                                        onPlayToggle = { onPlayBook(item) }
                                    )
                                }
                            }
                            if (unstartedBooks.isNotEmpty()) {
                                item(key = "hdr_unstarted") {
                                    LibrarySectionHeader(
                                        title = stringResource(R.string.section_not_started),
                                        count = unstartedBooks.size,
                                        trailing = stringResource(R.string.section_total_duration)
                                    )
                                }
                                items(unstartedBooks, key = { "new_${it.audiobook.id}" }) { item ->
                                    UnstartedBookListCard(
                                        item = item,
                                        onOpen = { onOpenBook(item.audiobook.id) },
                                        onLongPress = { bookMenuTarget = item }
                                    )
                                }
                            }
                        }
                    }
                }

                LibraryFilter.FINISHED -> {
                    if (finishedBooks.isEmpty()) {
                        EmptyFilterMessage()
                    } else if (gridLayout) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(155.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(finishedBooks, key = { it.audiobook.id }) { item ->
                                FinishedBookGridCard(
                                    item = item,
                                    onOpen = { onOpenBook(item.audiobook.id) },
                                    onLongPress = { bookMenuTarget = item }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(finishedBooks, key = { it.audiobook.id }) { item ->
                                FinishedBookListCard(
                                    item = item,
                                    onOpen = { onOpenBook(item.audiobook.id) },
                                    onLongPress = { bookMenuTarget = item }
                                )
                            }
                        }
                    }
                }

                LibraryFilter.HIDDEN -> {
                    if (hiddenBooks.isEmpty()) {
                        EmptyFilterMessage()
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(hiddenBooks, key = { it.audiobook.id }) { item ->
                                HiddenBookRow(
                                    item = item,
                                    onOpen = { onOpenBook(item.audiobook.id) },
                                    onLongPress = { bookMenuTarget = item },
                                    onUnhide = { onSetHidden(item.audiobook.id, false) }
                                )
                            }
                        }
                    }
                }

                LibraryFilter.CONFLICTS -> {
                    if (conflictBooks.isEmpty()) {
                        EmptyFilterMessage()
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(conflictBooks, key = { it.audiobook.id }) { item ->
                                UnstartedBookListCard(
                                    item = item,
                                    onOpen = { onOpenBook(item.audiobook.id) },
                                    onLongPress = { bookMenuTarget = item }
                                )
                            }
                        }
                    }
                }

                LibraryFilter.TRASH -> {
                    if (filteredTrashed.isEmpty()) {
                        EmptyFilterMessage()
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredTrashed, key = { it.audiobook.id }) { item ->
                                TrashBookRow(
                                    item = item,
                                    onRestore = { onRestoreFromTrash(item.audiobook.id) },
                                    onDeletePermanently = { confirmPermDelete = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    bookMenuTarget?.let { target ->
        BookActionsDialog(
            target = target,
            onDismiss = { bookMenuTarget = null },
            onOpenBook = onOpenBook,
            onMarkFinished = onMarkFinished,
            onResetProgress = onResetProgress,
            onSetHidden = onSetHidden,
            onMoveToTrash = onMoveToTrash
        )
    }

    confirmPermDelete?.let { target ->
        ConfirmPermanentDeleteDialog(
            target = target,
            onDismiss = { confirmPermDelete = null },
            onConfirmDelete = onPermanentlyDelete
        )
    }
}

@Composable
private fun EmptyFilterMessage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.empty_filter_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

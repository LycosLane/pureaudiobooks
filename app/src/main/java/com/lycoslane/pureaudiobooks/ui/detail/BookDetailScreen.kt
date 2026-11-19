package com.lycoslane.pureaudiobooks.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.core.audio.PlayerState
import com.lycoslane.pureaudiobooks.core.audio.SilenceOverride
import com.lycoslane.pureaudiobooks.core.audio.SpeedOverride
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.database.BookmarkEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.network.BoundedInputStream
import com.lycoslane.pureaudiobooks.core.network.OnlineBookMatch
import com.lycoslane.pureaudiobooks.core.preferences.AppSettings
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import com.lycoslane.pureaudiobooks.ui.common.PureSegmentedControl
import com.lycoslane.pureaudiobooks.ui.player.PlayerControlPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class BookDetailTab {
    CHAPTERS, BOOKMARKS, INFO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    data: AudiobookWithTracks?,
    bookmarks: List<BookmarkEntity>,
    globalSettings: AppSettings,
    initialOpenMetadata: Boolean = false,
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
    onSetBookSpeedOverride: (Float) -> Unit,
    onSetBookSilenceOverride: (Int) -> Unit,
    onSetTrackMonoOverride: (Long, Int) -> Unit,
    onSetBookMonoOverride: (Int) -> Unit,
    onSetBookNormalizationOverride: (Int) -> Unit,
    onSetBookSmartRewindOverride: (Int) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onApplyToAllAudiobooks: () -> Unit,
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
    onEnableOnlineLookup: (String) -> Unit,
    onSearchOnline: (String) -> Unit,
    onPickGalleryCoverBytes: (ByteArray) -> Unit,
    onResolveMetadata: (String, String, String, String?) -> Unit,
    onSplitByAlbum: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = data?.audiobook
    val tracks = remember(data?.tracks) {
        Formatters.sortedTracks(data?.tracks.orEmpty())
    }
    val activeTrackId = playerState.currentTrack?.id
    val activePositionMs = playerState.positionMs
    val isPlaying = playerState.isPlaying

    var selectedTab by rememberSaveable { mutableStateOf<BookDetailTab?>(null) }
    var showMetadataSheet by rememberSaveable { mutableStateOf(false) }
    var metadataCoverFocusMode by rememberSaveable { mutableStateOf(false) }
    var handledInitialMetadata by rememberSaveable { mutableStateOf(false) }
    var showCoverLightbox by rememberSaveable { mutableStateOf(false) }
    var showBookSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var activePanel by rememberSaveable { mutableStateOf(PlayerControlPanel.NONE) }
    var showBookmarkDialog by rememberSaveable { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    var chapterOptionsTarget by remember { mutableStateOf<Pair<Int, ChapterTrackEntity>?>(null) }

    LaunchedEffect(initialOpenMetadata, book?.id) {
        if (initialOpenMetadata && !handledInitialMetadata && book != null) {
            handledInitialMetadata = true
            metadataCoverFocusMode = false
            showMetadataSheet = true
        }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BoundedInputStream(input, 8L * 1024 * 1024).use { it.readBytes() }
                    }
                }.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onPickGalleryCoverBytes(bytes)
                    }
                }
            }
        }
    }

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
    val currentSpeed = when {
        isThisBookActive -> playerState.playbackSpeed
        book != null -> SpeedOverride.resolve(globalSettings.defaultPlaybackSpeed, book.speedOverride)
        else -> globalSettings.defaultPlaybackSpeed
    }
    val hasBookSpeedOverride = book != null && !SpeedOverride.isInherit(book.speedOverride)
    val resolvedSilence = if (book != null) {
        SilenceOverride.resolve(
            globalSettings.defaultSkipSilence,
            globalSettings.defaultSilenceIntensity,
            book.silenceModeOverride
        )
    } else {
        globalSettings.defaultSkipSilence to globalSettings.defaultSilenceIntensity
    }
    val currentSilence = if (isThisBookActive) playerState.skipSilence else resolvedSilence.first
    val currentSilenceIntensity = if (isThisBookActive) playerState.silenceIntensity else resolvedSilence.second
    val hasBookSilenceOverride =
        book != null && SilenceOverride.normalize(book.silenceModeOverride) != SilenceOverride.INHERIT
    val currentTrackMonoOverride = currentTrack?.monoModeOverride ?: AudioMonoMode.INHERIT
    val currentEffectiveMono = if (isThisBookActive) {
        playerState.effectiveMonoMode
    } else if (book != null) {
        AudioMonoMode.resolveEffective(globalSettings.monoMode, book.monoModeOverride, currentTrackMonoOverride)
    } else {
        AudioMonoMode.OFF
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedTab == null) {
                            stringResource(R.string.player_book_short)
                        } else {
                            book?.title.orEmpty()
                        },
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
                BookDetailBottomBar(
                    book = book,
                    tracks = tracks,
                    currentTrack = currentTrack,
                    progress = progress,
                    playerState = playerState,
                    isThisBookActive = isThisBookActive,
                    isThisBookPlaying = isThisBookPlaying,
                    currentSpeed = currentSpeed,
                    hasBookSpeedOverride = hasBookSpeedOverride,
                    currentSilence = currentSilence,
                    currentSilenceIntensity = currentSilenceIntensity,
                    hasBookSilenceOverride = hasBookSilenceOverride,
                    globalMonoMode = globalSettings.monoMode,
                    currentTrackMonoOverride = currentTrackMonoOverride,
                    currentEffectiveMono = currentEffectiveMono,
                    activePanel = activePanel,
                    onTogglePanel = { target ->
                        activePanel = if (activePanel == target) PlayerControlPanel.NONE else target
                    },
                    onOpenBookmarkDialog = { showBookmarkDialog = true },
                    onPlayBook = onPlayBook,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onSeekBy = onSeekBy,
                    onSkipTrack = onSkipTrack,
                    onSpeedChange = onSpeedChange,
                    onResetSpeedToGlobal = { onSetBookSpeedOverride(SpeedOverride.INHERIT) },
                    onSilenceChange = onSilenceChange,
                    onResetSilenceToGlobal = { onSetBookSilenceOverride(SilenceOverride.INHERIT) },
                    onSetTrackMonoOverride = onSetTrackMonoOverride,
                    onSetBookMonoOverride = onSetBookMonoOverride,
                    onSleepMinutes = onSleepMinutes,
                    onSleepEndOfTrack = onSleepEndOfTrack
                )
            }
        }
    ) { padding ->
        if (book == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (book.hasTagConflict) {
                Spacer(Modifier.height(4.dp))
                TagConflictAlertBanner(
                    onClick = {
                        metadataCoverFocusMode = false
                        showMetadataSheet = true
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            val tabOptions: List<Pair<BookDetailTab?, String>> = listOf(
                BookDetailTab.CHAPTERS to stringResource(R.string.tab_chapters, tracks.size),
                BookDetailTab.BOOKMARKS to stringResource(R.string.tab_bookmarks, bookmarks.size),
                BookDetailTab.INFO to stringResource(R.string.tab_info)
            )
            PureSegmentedControl<BookDetailTab?>(
                options = tabOptions,
                selected = selectedTab,
                onSelect = { chosen ->
                    selectedTab = if (selectedTab == chosen) null else chosen
                }
            )
            if (selectedTab != null) {
                Text(
                    text = stringResource(R.string.tap_active_tab_to_collapse),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            when (selectedTab) {
                null -> {
                    FocusedCoverView(
                        book = book,
                        progress = progress,
                        currentSpeed = currentSpeed,
                        onOpenCoverLightbox = { showCoverLightbox = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                BookDetailTab.CHAPTERS -> {
                    CompactBookHeader(
                        book = book,
                        progress = progress,
                        currentSpeed = currentSpeed,
                        onOpenCoverLightbox = { showCoverLightbox = true }
                    )
                    Spacer(Modifier.height(8.dp))
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
                    CompactBookHeader(
                        book = book,
                        progress = progress,
                        currentSpeed = currentSpeed,
                        onOpenCoverLightbox = { showCoverLightbox = true }
                    )
                    Spacer(Modifier.height(8.dp))
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
                                BookmarkRowCard(
                                    bm = bm,
                                    onJump = {
                                        val targetTrack = tracks.firstOrNull { it.id == bm.trackId }
                                        if (targetTrack != null) onJumpToBookmark(targetTrack, bm.positionMs)
                                    },
                                    onDelete = { onDeleteBookmark(bm.id) }
                                )
                            }
                        }
                    }
                }

                BookDetailTab.INFO -> {
                    CompactBookHeader(
                        book = book,
                        progress = progress,
                        currentSpeed = currentSpeed,
                        onOpenCoverLightbox = { showCoverLightbox = true }
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = book.description.ifBlank { stringResource(R.string.no_description_available) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = {
                                        metadataCoverFocusMode = false
                                        showMetadataSheet = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.edit_metadata_and_cover))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCoverLightbox && book != null) {
        CoverLightboxDialog(
            title = book.title,
            author = book.author,
            artworkUrl = book.artworkUrl,
            onDismiss = { showCoverLightbox = false },
            onSearchOnlineAlternatives = {
                metadataCoverFocusMode = true
                showMetadataSheet = true
            },
            onPickCoverFromGallery = {
                coverPickerLauncher.launch("image/*")
            }
        )
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
            globalSettings = globalSettings,
            progressFraction = progress.fraction,
            onDismiss = { showBookSettingsSheet = false },
            onSetSpeedOverride = onSetBookSpeedOverride,
            onSetSilenceModeOverride = onSetBookSilenceOverride,
            onSetMonoModeOverride = onSetBookMonoOverride,
            onSetNormalizationOverride = onSetBookNormalizationOverride,
            onSetSmartRewindOverride = onSetBookSmartRewindOverride,
            onTextScaleChange = onTextScaleChange,
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
            onApplyToAllAudiobooks = onApplyToAllAudiobooks,
            onEditMetadata = {
                showBookSettingsSheet = false
                metadataCoverFocusMode = false
                showMetadataSheet = true
            }
        )
    }

    if (showMetadataSheet && book != null) {
        MetadataResolverSheet(
            book = book,
            coverFocusMode = metadataCoverFocusMode,
            onlineLookupEnabled = globalSettings.onlineLookupEnabled,
            textScale = globalSettings.textScale,
            onTextScaleChange = onTextScaleChange,
            onlineResults = onlineResults,
            isSearchingOnline = isSearchingOnline,
            onEnableOnlineLookup = onEnableOnlineLookup,
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

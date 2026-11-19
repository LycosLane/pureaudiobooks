package com.lycoslane.pureaudiobooks.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lycoslane.pureaudiobooks.PureAudiobooksApplication
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.ui.detail.BookDetailScreen
import com.lycoslane.pureaudiobooks.ui.home.HomeScreen
import com.lycoslane.pureaudiobooks.ui.player.FullPlayerSheet
import com.lycoslane.pureaudiobooks.ui.player.MiniPlayer
import com.lycoslane.pureaudiobooks.ui.settings.SettingsScreen
import com.lycoslane.pureaudiobooks.ui.viewmodel.BookDetailViewModel
import com.lycoslane.pureaudiobooks.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

@Composable
fun PureAudiobooksApp(app: PureAudiobooksApplication) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val libraryViewModel: LibraryViewModel = viewModel()

    LaunchedEffect(Unit) {
        app.playerController.connect()
    }

    val activeBooks by libraryViewModel.activeBooks.collectAsStateWithLifecycle()
    val trashedBooks by libraryViewModel.trashedBooks.collectAsStateWithLifecycle()
    val settings by libraryViewModel.settings.collectAsStateWithLifecycle()
    val isScanning by libraryViewModel.isScanning.collectAsStateWithLifecycle()
    val playerState by app.playerController.state.collectAsStateWithLifecycle()

    var showFullPlayer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(playerState.currentTrack?.id) {
        if (playerState.currentTrack == null) {
            showFullPlayer = false
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }.onFailure {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            libraryViewModel.setLocalFolderUriAndScan(uri.toString())
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            libraryViewModel.exportBackup { json ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.backup_exported))
                    }
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
            if (!json.isNullOrBlank()) {
                libraryViewModel.importBackup(json) { count ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (count >= 0) context.getString(R.string.backup_imported, count)
                            else context.getString(R.string.backup_import_failed)
                        )
                    }
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentDetailBookId = navBackStackEntry?.arguments?.getLong("bookId")
    val isViewingCurrentBookDetail = currentRoute?.startsWith("detail/") == true &&
        currentDetailBookId != null &&
        currentDetailBookId == playerState.currentBook?.id
    val hideMiniPlayer = currentRoute == "settings" || isViewingCurrentBookDetail

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (playerState.currentTrack != null && !hideMiniPlayer) {
                    MiniPlayer(
                        state = playerState,
                        onTogglePlayPause = { app.playerController.togglePlayPause() },
                        onForward30 = { app.playerController.seekBy(30_000L) },
                        onExpand = { showFullPlayer = true }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    activeBooks = activeBooks,
                    trashedBooks = trashedBooks,
                    hasFolderConfigured = settings.localFolderUri.isNotBlank(),
                    gridLayout = settings.gridLayout,
                    showRefreshButton = settings.showRefreshButton,
                    showSearchBar = settings.showSearchBar,
                    isScanning = isScanning,
                    activeTrackId = playerState.currentTrack?.id,
                    activePositionMs = playerState.positionMs,
                    isPlaying = playerState.isPlaying,
                    onToggleLayout = { libraryViewModel.preferences.setGridLayout(!settings.gridLayout) },
                    onScan = { libraryViewModel.scanFolder() },
                    onPickFolder = { folderPickerLauncher.launch(null) },
                    onOpenSettings = {
                        navController.navigate("settings") { launchSingleTop = true }
                    },
                    onOpenBook = { bookId ->
                        navController.navigate("detail/$bookId") { launchSingleTop = true }
                    },
                    onPlayBook = { item -> libraryViewModel.playOrToggleBook(item) },
                    onMarkFinished = { bookId, finished -> libraryViewModel.markBookFinished(bookId, finished) },
                    onResetProgress = { bookId -> libraryViewModel.resetBookProgress(bookId) },
                    onSetHidden = { bookId, hidden ->
                        libraryViewModel.setBookHidden(bookId, hidden)
                        if (hidden) {
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.snackbar_book_hidden),
                                    actionLabel = context.getString(R.string.action_undo),
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    libraryViewModel.setBookHidden(bookId, false)
                                }
                            }
                        }
                    },
                    onMoveToTrash = { bookId -> libraryViewModel.moveToTrash(bookId) },
                    onRestoreFromTrash = { bookId -> libraryViewModel.restoreFromTrash(bookId) },
                    onPermanentlyDelete = { bookId -> libraryViewModel.permanentlyDelete(bookId) }
                )
            }

            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onChangeThemeMode = { libraryViewModel.preferences.setThemeMode(it) },
                    onChangeLightAccent = { libraryViewModel.preferences.setLightAccent(it) },
                    onChangeDarkAccent = { libraryViewModel.preferences.setDarkAccent(it) },
                    onToggleAudioNormalization = { libraryViewModel.preferences.setAudioNormalizationEnabled(it) },
                    onChangeMonoMode = { libraryViewModel.preferences.setMonoMode(it) },
                    onChangeSmartRewind = { libraryViewModel.preferences.setSmartRewindSeconds(it) },
                    onChangeBluetoothNextPrevSeeks = { libraryViewModel.preferences.setBluetoothNextPrevSeeks(it) },
                    onPickFolder = { folderPickerLauncher.launch(null) },
                    onRescanFolder = {
                        navController.popBackStack()
                        libraryViewModel.scanFolder()
                    },
                    onToggleShowRefreshButton = { libraryViewModel.preferences.setShowRefreshButton(it) },
                    onToggleShowSearchBar = { libraryViewModel.preferences.setShowSearchBar(it) },
                    onToggleOnlineLookup = { libraryViewModel.preferences.setOnlineLookupEnabled(it) },
                    onChangeTrashRetentionHours = { libraryViewModel.preferences.setTrashRetentionHours(it) },
                    onChangeAutoTrashFinishedDays = { libraryViewModel.preferences.setAutoMoveFinishedToTrashDays(it) },
                    onChangeAppLanguage = { lang ->
                        libraryViewModel.preferences.setAppLanguage(lang)
                        (context as? Activity)?.recreate()
                    },
                    onExportBackup = { exportBackupLauncher.launch("pureaudiobooks-backup.json") },
                    onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) }
                )
            }

            composable(
                route = "detail/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
                val detailViewModel: BookDetailViewModel = viewModel(
                    key = "detail_$bookId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return BookDetailViewModel(app, bookId) as T
                        }
                    }
                )
                val bookData by detailViewModel.bookWithTracks.collectAsStateWithLifecycle()
                val bookmarks by detailViewModel.bookmarks.collectAsStateWithLifecycle()
                val onlineResults by detailViewModel.onlineResults.collectAsStateWithLifecycle()
                val isSearchingOnline by detailViewModel.isSearchingOnline.collectAsStateWithLifecycle()

                BookDetailScreen(
                    data = bookData,
                    bookmarks = bookmarks,
                    globalMonoMode = settings.monoMode,
                    globalAudioNormalization = settings.audioNormalizationEnabled,
                    onlineLookupEnabled = settings.onlineLookupEnabled,
                    onlineResults = onlineResults,
                    isSearchingOnline = isSearchingOnline,
                    playerState = playerState,
                    onBack = { navController.popBackStack() },
                    onPlayBook = { track -> detailViewModel.playBook(track) },
                    onTogglePlayPause = { app.playerController.togglePlayPause() },
                    onSeekTo = { app.playerController.seekTo(it) },
                    onSeekBy = { app.playerController.seekBy(it) },
                    onSkipTrack = { app.playerController.skipTrack(it) },
                    onSpeedChange = { detailViewModel.setPlaybackSpeed(it) },
                    onSilenceChange = { enabled, intensity -> detailViewModel.setSkipSilence(enabled, intensity) },
                    onSetTrackMonoOverride = { trackId, mode -> detailViewModel.setTrackMonoOverride(trackId, mode) },
                    onSetBookMonoOverride = { mode -> detailViewModel.setBookMonoOverride(mode) },
                    onSetBookNormalizationOverride = { ov -> detailViewModel.setBookNormalizationOverride(ov) },
                    onSleepMinutes = { app.playerController.startSleepTimerMinutes(it) },
                    onSleepEndOfTrack = { app.playerController.setSleepAtEndOfTrack(it) },
                    onAddBookmark = { track, posMs, note ->
                        scope.launch {
                            app.repository.addBookmark(bookId, track, posMs, note)
                            snackbarHostState.showSnackbar(context.getString(R.string.bookmark_saved))
                        }
                    },
                    onToggleTrackFinished = { tid, finished -> detailViewModel.setTrackFinished(tid, finished) },
                    onMarkBookFinished = { finished -> detailViewModel.markBookFinished(finished) },
                    onResetProgress = { detailViewModel.resetBookProgress() },
                    onSetHidden = { hidden ->
                        detailViewModel.setBookHidden(hidden)
                        if (hidden) {
                            navController.popBackStack()
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.snackbar_book_hidden),
                                    actionLabel = context.getString(R.string.action_undo),
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    libraryViewModel.setBookHidden(bookId, false)
                                }
                            }
                        }
                    },
                    onMoveToTrash = {
                        libraryViewModel.moveToTrash(bookId)
                        navController.popBackStack()
                    },
                    onDeleteBookmark = { bmId -> detailViewModel.deleteBookmark(bmId) },
                    onJumpToBookmark = { track, posMs ->
                        detailViewModel.playBook(track)
                        app.playerController.seekTo(posMs)
                    },
                    onSearchOnline = { query -> detailViewModel.searchOnline(query) },
                    onResolveMetadata = { title, author, desc, cover ->
                        detailViewModel.resolveMetadata(title, author, desc, cover) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.metadata_saved))
                            }
                        }
                    },
                    onSplitByAlbum = {
                        detailViewModel.splitByAlbumTag { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.split_completed, count))
                            }
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }

    if (showFullPlayer && playerState.currentTrack != null) {
        val activeBook = playerState.currentBook
        val activeTrack = playerState.currentTrack
        FullPlayerSheet(
            state = playerState,
            globalMonoMode = settings.monoMode,
            globalAudioNormalization = settings.audioNormalizationEnabled,
            onDismiss = { showFullPlayer = false },
            onTogglePlayPause = { app.playerController.togglePlayPause() },
            onSeekTo = { app.playerController.seekTo(it) },
            onSeekBy = { app.playerController.seekBy(it) },
            onSkipTrack = { app.playerController.skipTrack(it) },
            onPlayTrack = { chosen ->
                if (activeBook != null) {
                    app.playerController.playAudiobook(activeBook, playerState.bookTracks, chosen)
                }
            },
            onToggleTrackFinished = { tid, finished ->
                scope.launch {
                    app.repository.setTrackFinished(tid, finished)
                    if (activeBook != null) {
                        app.playerController.refreshCurrentBookMetadata(activeBook.id)
                    }
                }
            },
            onSpeedChange = { app.playerController.setPlaybackSpeed(it) },
            onSilenceChange = { enabled, intensity -> app.playerController.setSkipSilence(enabled, intensity) },
            onSleepMinutes = { app.playerController.startSleepTimerMinutes(it) },
            onSleepEndOfTrack = { app.playerController.setSleepAtEndOfTrack(it) },
            onSetTrackMonoOverride = { mode ->
                if (activeTrack != null) {
                    app.playerController.setTrackMonoOverride(activeTrack.id, mode)
                }
            },
            onSetBookMonoOverride = { mode ->
                if (activeBook != null) {
                    app.playerController.setBookMonoOverride(activeBook.id, mode)
                }
            },
            onSetBookNormalizationOverride = { ov ->
                if (activeBook != null) {
                    app.playerController.setBookNormalizationOverride(activeBook.id, ov)
                }
            },
            onSetBookHidden = { hidden ->
                if (activeBook != null) {
                    libraryViewModel.setBookHidden(activeBook.id, hidden)
                }
            },
            onMarkBookFinished = { finished ->
                if (activeBook != null) {
                    libraryViewModel.markBookFinished(activeBook.id, finished)
                }
            },
            onResetBookProgress = {
                if (activeBook != null) {
                    libraryViewModel.resetBookProgress(activeBook.id)
                }
            },
            onMoveBookToTrash = {
                if (activeBook != null) {
                    libraryViewModel.moveToTrash(activeBook.id)
                }
            },
            onAddBookmark = { note ->
                val book = playerState.currentBook ?: return@FullPlayerSheet
                val track = playerState.currentTrack ?: return@FullPlayerSheet
                scope.launch {
                    app.repository.addBookmark(book.id, track, playerState.positionMs, note)
                    snackbarHostState.showSnackbar(context.getString(R.string.bookmark_saved))
                }
            }
        )
    }
}

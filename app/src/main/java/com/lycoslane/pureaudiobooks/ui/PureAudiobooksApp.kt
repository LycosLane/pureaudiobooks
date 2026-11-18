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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lycoslane.pureaudiobooks.PureAudiobooksApplication
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.ui.detail.BookDetailScreen
import com.lycoslane.pureaudiobooks.ui.home.HomeScreen
import com.lycoslane.pureaudiobooks.ui.home.SettingsSheet
import com.lycoslane.pureaudiobooks.ui.player.FullPlayerSheet
import com.lycoslane.pureaudiobooks.ui.player.MiniPlayer
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
    var showSettings by rememberSaveable { mutableStateOf(false) }

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
    val currentDetailBookId = navBackStackEntry?.arguments?.getLong("bookId")
    val isViewingCurrentBookDetail = navBackStackEntry?.destination?.route?.startsWith("detail/") == true &&
        currentDetailBookId != null &&
        currentDetailBookId == playerState.currentBook?.id

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (playerState.currentTrack != null && !isViewingCurrentBookDetail) {
                    MiniPlayer(
                        state = playerState,
                        onTogglePlayPause = { app.playerController.togglePlayPause() },
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
                    isScanning = isScanning,
                    activeTrackId = playerState.currentTrack?.id,
                    activePositionMs = playerState.positionMs,
                    onToggleLayout = { libraryViewModel.preferences.setGridLayout(!settings.gridLayout) },
                    onScan = { libraryViewModel.scanFolder() },
                    onPickFolder = { folderPickerLauncher.launch(null) },
                    onOpenSettings = { showSettings = true },
                    onOpenBook = { bookId ->
                        navController.navigate("detail/$bookId") { launchSingleTop = true }
                    },
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
                    onSpeedChange = { app.playerController.setPlaybackSpeed(it) },
                    onSilenceChange = { enabled, intensity -> app.playerController.setSkipSilence(enabled, intensity) },
                    onSleepMinutes = { app.playerController.startSleepTimerMinutes(it) },
                    onSleepEndOfTrack = { app.playerController.setSleepAtEndOfTrack(it) },
                    onCancelSleep = { app.playerController.cancelSleepTimer() },
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
                        if (hidden) navController.popBackStack()
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
        FullPlayerSheet(
            state = playerState,
            onDismiss = { showFullPlayer = false },
            onTogglePlayPause = { app.playerController.togglePlayPause() },
            onSeekTo = { app.playerController.seekTo(it) },
            onSeekBy = { app.playerController.seekBy(it) },
            onSkipTrack = { app.playerController.skipTrack(it) },
            onSpeedChange = { app.playerController.setPlaybackSpeed(it) },
            onSilenceChange = { enabled, intensity -> app.playerController.setSkipSilence(enabled, intensity) },
            onSleepMinutes = { app.playerController.startSleepTimerMinutes(it) },
            onSleepEndOfTrack = { app.playerController.setSleepAtEndOfTrack(it) },
            onCancelSleep = { app.playerController.cancelSleepTimer() },
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

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            preferences = libraryViewModel.preferences,
            onPickFolder = { folderPickerLauncher.launch(null) },
            onRescanFolder = {
                showSettings = false
                libraryViewModel.scanFolder()
            },
            onExportBackup = { exportBackupLauncher.launch("pureaudiobooks-backup.json") },
            onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
            onLanguageChanged = { lang ->
                libraryViewModel.preferences.setAppLanguage(lang)
                (context as? Activity)?.recreate()
            },
            onDismiss = { showSettings = false }
        )
    }
}

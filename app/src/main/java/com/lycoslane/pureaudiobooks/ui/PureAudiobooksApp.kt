package com.lycoslane.pureaudiobooks.ui

import android.app.Activity
import android.content.Intent
import android.util.Log
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.lycoslane.pureaudiobooks.core.network.BoundedInputStream
import com.lycoslane.pureaudiobooks.ui.detail.BookDetailScreen
import com.lycoslane.pureaudiobooks.ui.home.HomeScreen
import com.lycoslane.pureaudiobooks.ui.player.MiniPlayer
import com.lycoslane.pureaudiobooks.ui.settings.SettingsScreen
import com.lycoslane.pureaudiobooks.ui.viewmodel.BookDetailViewModel
import com.lycoslane.pureaudiobooks.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use {
                                it.write(json.toByteArray(Charsets.UTF_8))
                                true
                            } ?: false
                        }.onFailure {
                            Log.e("PureAudiobooksApp", "Backup export failed", it)
                        }.getOrDefault(false)
                    }
                    snackbarHostState.showSnackbar(
                        context.getString(if (ok) R.string.backup_exported else R.string.backup_export_failed)
                    )
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BoundedInputStream(input, 10L * 1024 * 1024).use {
                                it.readBytes().toString(Charsets.UTF_8)
                            }
                        }
                    }.onFailure {
                        Log.e("PureAudiobooksApp", "Backup file read failed", it)
                    }.getOrNull()
                }
                if (json.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(context.getString(R.string.backup_import_failed))
                    return@launch
                }
                val previousLanguage = libraryViewModel.preferences.settings.value.appLanguage
                libraryViewModel.importBackup(json) { count ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (count >= 0) context.getString(R.string.backup_imported, count)
                            else context.getString(R.string.backup_import_failed)
                        )
                        if (count >= 0 && libraryViewModel.preferences.settings.value.appLanguage != previousLanguage) {
                            (context as? Activity)?.recreate()
                        }
                    }
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isInDetailRoute = currentRoute?.startsWith("detail/") == true
    val hideMiniPlayer = currentRoute == "settings" || isInDetailRoute

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                val activeBookId = playerState.currentBook?.id
                if (playerState.currentTrack != null && activeBookId != null && !hideMiniPlayer) {
                    MiniPlayer(
                        state = playerState,
                        onTogglePlayPause = { app.playerController.togglePlayPause() },
                        onRewind10 = { app.playerController.seekBy(-10_000L) },
                        onExpand = {
                            navController.navigate("detail/$activeBookId") {
                                launchSingleTop = true
                            }
                        }
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
                    globalSettings = settings,
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
                    onOpenBookMetadata = { bookId ->
                        navController.navigate("detail/$bookId?openMetadata=true") { launchSingleTop = true }
                    },
                    onPlayBook = { item -> libraryViewModel.playOrToggleBook(item) },
                    onSetBookSpeedOverride = { bookId, speedOv ->
                        libraryViewModel.setBookSpeedOverride(bookId, speedOv)
                    },
                    onSetBookSilenceModeOverride = { bookId, silOv ->
                        libraryViewModel.setBookSilenceOverride(bookId, silOv)
                    },
                    onSetBookMonoOverride = { bookId, mode -> libraryViewModel.setBookMonoOverride(bookId, mode) },
                    onSetBookNormalizationOverride = { bookId, ov ->
                        libraryViewModel.setBookNormalizationOverride(bookId, ov)
                    },
                    onSetBookSmartRewindOverride = { bookId, rwOv ->
                        libraryViewModel.setBookSmartRewindOverride(bookId, rwOv)
                    },
                    onApplyBookAudioToAll = { book ->
                        libraryViewModel.applyBookAudioSettingsToAll(book) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.applied_to_all_audiobooks))
                            }
                        }
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

            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onChangeThemeMode = { libraryViewModel.preferences.setThemeMode(it) },
                    onChangeLightAccent = { libraryViewModel.preferences.setLightAccent(it) },
                    onChangeDarkAccent = { libraryViewModel.preferences.setDarkAccent(it) },
                    onChangeUiScale = { libraryViewModel.preferences.setUiScale(it) },
                    onChangeDefaultSpeed = { libraryViewModel.preferences.setDefaultPlaybackSpeed(it) },
                    onChangeDefaultSilenceMode = { enabled, intensity ->
                        libraryViewModel.preferences.setDefaultSilenceMode(enabled, intensity)
                    },
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
                        if (lang != settings.appLanguage) {
                            libraryViewModel.preferences.setAppLanguage(lang)
                            (context as? Activity)?.recreate()
                        }
                    },
                    onExportBackup = { exportBackupLauncher.launch("pureaudiobooks-backup.json") },
                    onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) }
                )
            }

            composable(
                route = "detail/{bookId}?openMetadata={openMetadata}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType },
                    navArgument("openMetadata") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
                val openMetadata = backStackEntry.arguments?.getBoolean("openMetadata") ?: false
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
                    globalSettings = settings,
                    initialOpenMetadata = openMetadata,
                    onlineResults = onlineResults,
                    isSearchingOnline = isSearchingOnline,
                    playerState = playerState,
                    onBack = { navController.popBackStack() },
                    onPlayBook = { track, startPosMs, forcePlay ->
                        detailViewModel.playBook(
                            specificTrack = track,
                            startPositionMs = startPosMs,
                            forcePlay = forcePlay
                        )
                    },
                    onTogglePlayPause = { app.playerController.togglePlayPause() },
                    onSeekTo = { app.playerController.seekTo(it) },
                    onSeekBy = { app.playerController.seekBy(it) },
                    onSkipTrack = { app.playerController.skipTrack(it) },
                    onSpeedChange = { detailViewModel.setPlaybackSpeed(it) },
                    onSilenceChange = { enabled, intensity -> detailViewModel.setSkipSilence(enabled, intensity) },
                    onSetBookSpeedOverride = { speedOv -> detailViewModel.setBookSpeedOverride(speedOv) },
                    onSetBookSilenceOverride = { silOv -> detailViewModel.setBookSilenceOverride(silOv) },
                    onSetTrackMonoOverride = { trackId, mode -> detailViewModel.setTrackMonoOverride(trackId, mode) },
                    onSetBookMonoOverride = { mode -> detailViewModel.setBookMonoOverride(mode) },
                    onSetBookNormalizationOverride = { ov -> detailViewModel.setBookNormalizationOverride(ov) },
                    onSetBookSmartRewindOverride = { rwOv -> detailViewModel.setBookSmartRewindOverride(rwOv) },
                    onTextScaleChange = { scale -> libraryViewModel.preferences.setTextScale(scale) },
                    onApplyToAllAudiobooks = {
                        detailViewModel.applyAudioSettingsToAll {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.applied_to_all_audiobooks))
                            }
                        }
                    },
                    onSleepMinutes = { app.playerController.startSleepTimerMinutes(it) },
                    onSleepEndOfTrack = { app.playerController.setSleepAtEndOfTrack(it) },
                    onAddBookmark = { track, posMs, note ->
                        detailViewModel.addBookmark(track, posMs, note) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.bookmark_saved))
                            }
                        }
                    },
                    onToggleTrackFinished = { tid, finished -> detailViewModel.setTrackFinished(tid, finished) },
                    onMarkTracksUpToFinished = { tid -> detailViewModel.markTracksUpToFinished(tid) },
                    onMarkTracksFromUnfinished = { tid -> detailViewModel.markTracksFromUnfinished(tid) },
                    onDeleteTracksUpTo = { tid ->
                        detailViewModel.deleteTracksUpTo(tid) {
                            navController.popBackStack()
                        }
                    },
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
                        detailViewModel.playBook(
                            specificTrack = track,
                            startPositionMs = posMs,
                            forcePlay = true
                        )
                    },
                    onEnableOnlineLookup = { query -> detailViewModel.enableOnlineLookupAndSearch(query) },
                    onSearchOnline = { query -> detailViewModel.searchOnline(query) },
                    onClearOnlineResults = { detailViewModel.clearOnlineResults() },
                    onPickGalleryCoverBytes = { bytes ->
                        if (bytes.isEmpty()) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.cover_update_failed))
                            }
                        } else {
                            detailViewModel.updateBookCover(bytes) { updated ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            if (updated) R.string.cover_updated else R.string.cover_update_failed
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onResolveMetadata = { title, author, desc, cover ->
                        detailViewModel.resolveMetadata(title, author, desc, cover) { coverFailed ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        if (coverFailed) R.string.metadata_saved_cover_failed else R.string.metadata_saved
                                    )
                                )
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
}

package com.lycoslane.pureaudiobooks.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lycoslane.pureaudiobooks.PureAudiobooksApplication
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.preferences.AppSettings
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PureAudiobooksApplication
    private val repo = app.repository
    val preferences = app.preferences

    val activeBooks: StateFlow<List<AudiobookWithTracks>> = repo.observeActiveAudiobooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashedBooks: StateFlow<List<AudiobookWithTracks>> = repo.observeTrashedAudiobooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = preferences.settings

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            repo.runTrashMaintenance()
        }
    }

    fun scanFolder(forceRestart: Boolean = false) {
        if (!forceRestart && _isScanning.value) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            try {
                repo.syncLocalLibrary()
                app.playerController.reconcileActiveBook()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Library scan failed", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setLocalFolderUriAndScan(uri: String) {
        preferences.setLocalFolderUri(uri)
        scanFolder(forceRestart = true)
    }

    fun playOrToggleBook(item: AudiobookWithTracks) {
        val tracks = Formatters.sortedTracks(item.tracks)
        if (tracks.isEmpty()) return
        app.playerController.playAudiobook(item.audiobook, tracks)
    }

    fun moveToTrash(bookId: Long) {
        viewModelScope.launch {
            app.playerController.stopIfPlayingBook(bookId)
            repo.moveToTrash(bookId)
        }
    }

    fun setBookHidden(bookId: Long, hidden: Boolean) {
        viewModelScope.launch {
            if (hidden) app.playerController.stopIfPlayingBook(bookId)
            repo.setBookHidden(bookId, hidden)
        }
    }

    fun markBookFinished(bookId: Long, finished: Boolean) {
        viewModelScope.launch {
            if (finished) app.playerController.stopIfPlayingBook(bookId)
            repo.markBookFinished(bookId, finished)
        }
    }

    fun resetBookProgress(bookId: Long) {
        viewModelScope.launch {
            app.playerController.stopIfPlayingBook(bookId)
            repo.resetBookProgress(bookId)
        }
    }

    fun setBookSpeedOverride(bookId: Long, speedOverride: Float) {
        app.playerController.setBookSpeedOverride(bookId, speedOverride)
    }

    fun setBookSilenceOverride(bookId: Long, silenceModeOverride: Int) {
        app.playerController.setBookSilenceOverride(bookId, silenceModeOverride)
    }

    fun setBookSmartRewindOverride(bookId: Long, smartRewindOverride: Int) {
        app.playerController.setBookSmartRewindOverride(bookId, smartRewindOverride)
    }

    fun setBookMonoOverride(bookId: Long, mode: Int) {
        app.playerController.setBookMonoOverride(bookId, mode)
    }

    fun setBookNormalizationOverride(bookId: Long, override: Int) {
        app.playerController.setBookNormalizationOverride(bookId, override)
    }

    fun applyBookAudioSettingsToAll(book: AudiobookEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.applyAudioSettingsToAllBooks(book)
            app.playerController.refreshAfterApplyAll()
            onDone()
        }
    }

    fun restoreFromTrash(bookId: Long) {
        viewModelScope.launch { repo.restoreFromTrash(bookId) }
    }

    fun permanentlyDelete(bookId: Long) {
        viewModelScope.launch {
            app.playerController.stopIfPlayingBook(bookId)
            repo.permanentlyDeleteAudiobook(bookId)
        }
    }

    fun exportBackup(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = repo.exportBackupJson()
            onReady(json)
        }
    }

    fun importBackup(json: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = try {
                val restored = repo.importBackupJson(json)
                app.playerController.reconcileActiveBook(syncTrackPositionFromDb = true)
                restored
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Backup import failed", e)
                -1
            }
            onComplete(count)
        }
    }

    companion object {
        private const val TAG = "LibraryViewModel"
    }
}

package com.lycoslane.pureaudiobooks.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lycoslane.pureaudiobooks.PureAudiobooksApplication
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.preferences.AppSettings
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

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repo.runTrashMaintenance()
        }
    }

    fun scanFolder() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repo.syncLocalLibrary()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setLocalFolderUriAndScan(uri: String) {
        preferences.setLocalFolderUri(uri)
        scanFolder()
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
            val count = runCatching { repo.importBackupJson(json) }.getOrDefault(-1)
            onComplete(count)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}

package com.lycoslane.pureaudiobooks.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lycoslane.pureaudiobooks.PureAudiobooksApplication
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.database.BookmarkEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.network.OnlineBookMatch
import com.lycoslane.pureaudiobooks.core.network.OnlineMetadataClient
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailViewModel(
    application: Application,
    private val audiobookId: Long
) : AndroidViewModel(application) {
    private val app = application as PureAudiobooksApplication
    private val repo = app.repository

    val bookWithTracks: StateFlow<AudiobookWithTracks?> = repo.observeAudiobook(audiobookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val bookmarks: StateFlow<List<BookmarkEntity>> = repo.observeBookmarks(audiobookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _onlineResults = MutableStateFlow<List<OnlineBookMatch>>(emptyList())
    val onlineResults: StateFlow<List<OnlineBookMatch>> = _onlineResults.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    fun playBook(specificTrack: ChapterTrackEntity? = null) {
        val data = bookWithTracks.value ?: return
        val sortedTracks = Formatters.sortedTracks(data.tracks)
        app.playerController.playAudiobook(data.audiobook, sortedTracks, specificTrack)
    }

    fun setTrackFinished(trackId: Long, finished: Boolean) {
        viewModelScope.launch {
            repo.setTrackFinished(trackId, finished)
            app.playerController.refreshCurrentBookMetadata(audiobookId)
        }
    }

    fun markTracksUpToFinished(trackId: Long) {
        viewModelScope.launch {
            val data = bookWithTracks.value
            val activeTrackId = app.playerController.state.value.currentTrack?.id
            val affectsPlaying = if (data != null && activeTrackId != null) {
                val sorted = Formatters.sortedTracks(data.tracks)
                val idx = sorted.indexOfFirst { it.id == trackId }
                idx >= 0 && sorted.subList(0, idx + 1).any { it.id == activeTrackId }
            } else false
            if (affectsPlaying) {
                app.playerController.stopIfPlayingBook(audiobookId)
            }
            repo.markTracksUpToFinished(audiobookId, trackId)
            if (!affectsPlaying) {
                app.playerController.refreshCurrentBookMetadata(audiobookId)
            }
        }
    }

    fun markTracksFromUnfinished(trackId: Long) {
        viewModelScope.launch {
            repo.markTracksFromUnfinished(audiobookId, trackId)
            app.playerController.refreshCurrentBookMetadata(audiobookId)
        }
    }

    fun deleteTracksUpTo(trackId: Long, onBookRemoved: () -> Unit) {
        viewModelScope.launch {
            val data = bookWithTracks.value
            val activeTrackId = app.playerController.state.value.currentTrack?.id
            val affectsPlaying = if (data != null && activeTrackId != null) {
                val sorted = Formatters.sortedTracks(data.tracks)
                val idx = sorted.indexOfFirst { it.id == trackId }
                idx >= 0 && sorted.subList(0, idx + 1).any { it.id == activeTrackId }
            } else false
            if (affectsPlaying) {
                app.playerController.stopIfPlayingBook(audiobookId)
            }
            val removedBook = repo.deleteTracksUpTo(audiobookId, trackId)
            if (removedBook) {
                app.playerController.stopIfPlayingBook(audiobookId)
                onBookRemoved()
            } else {
                app.playerController.refreshCurrentBookMetadata(audiobookId)
            }
        }
    }

    fun markBookFinished(finished: Boolean) {
        viewModelScope.launch {
            if (finished) app.playerController.stopIfPlayingBook(audiobookId)
            repo.markBookFinished(audiobookId, finished)
        }
    }

    fun resetBookProgress() {
        viewModelScope.launch {
            app.playerController.stopIfPlayingBook(audiobookId)
            repo.resetBookProgress(audiobookId)
        }
    }

    fun setBookHidden(hidden: Boolean) {
        viewModelScope.launch {
            if (hidden) app.playerController.stopIfPlayingBook(audiobookId)
            repo.setBookHidden(audiobookId, hidden)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        app.playerController.setBookPlaybackSpeed(audiobookId, speed)
    }

    fun setSkipSilence(enabled: Boolean, intensity: Int) {
        app.playerController.setBookSilenceMode(audiobookId, enabled, intensity)
    }

    fun setTrackMonoOverride(trackId: Long, mode: Int) {
        app.playerController.setTrackMonoOverride(trackId, mode)
    }

    fun setBookMonoOverride(mode: Int) {
        app.playerController.setBookMonoOverride(audiobookId, mode)
    }

    fun setBookNormalizationOverride(override: Int) {
        app.playerController.setBookNormalizationOverride(audiobookId, override)
    }

    fun addBookmark(track: ChapterTrackEntity, positionMs: Long, note: String) {
        viewModelScope.launch {
            repo.addBookmark(audiobookId, track, positionMs, note)
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch { repo.deleteBookmark(bookmarkId) }
    }

    fun resolveMetadata(
        title: String,
        author: String,
        description: String,
        onlineCoverUrl: String? = null,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repo.resolveBookMetadata(
                audiobookId = audiobookId,
                title = title,
                author = author,
                description = description,
                onlineCoverUrl = onlineCoverUrl,
                writeSidecarFiles = true
            )
            app.playerController.refreshCurrentBookMetadata(audiobookId)
            onDone()
        }
    }

    fun splitByAlbumTag(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            app.playerController.stopIfPlayingBook(audiobookId)
            val created = repo.splitAudiobookByAlbumTag(audiobookId)
            onDone(created)
        }
    }

    fun searchOnline(query: String) {
        if (!app.preferences.settings.value.onlineLookupEnabled) return
        viewModelScope.launch {
            _isSearchingOnline.value = true
            try {
                _onlineResults.value = OnlineMetadataClient.search(query)
            } finally {
                _isSearchingOnline.value = false
            }
        }
    }

    fun clearOnlineResults() {
        _onlineResults.value = emptyList()
    }
}

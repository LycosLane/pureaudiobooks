package com.lycoslane.pureaudiobooks.core.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDao
import com.lycoslane.pureaudiobooks.core.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerState(
    val currentBook: AudiobookEntity? = null,
    val currentTrack: ChapterTrackEntity? = null,
    val totalBookTracks: Int = 0,
    val currentTrackIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val skipSilence: Boolean = false,
    val silenceIntensity: Int = 1,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepAtEndOfTrack: Boolean = false
)

class PlayerController(
    private val context: Context,
    private val dao: PureAudiobooksDao,
    private val preferences: AppPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    attachListener(controller)
                    syncFromController(controller)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun attachListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startProgressLoop() else progressJob?.cancel()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncFromController(controller)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && _state.value.sleepAtEndOfTrack) {
                    _state.update { it.copy(sleepAtEndOfTrack = false, sleepTimerRemainingMs = 0L) }
                }
                syncFromController(controller)
            }
        })
    }

    private fun syncFromController(controller: MediaController) {
        val trackId = controller.currentMediaItem?.mediaId?.toLongOrNull()
        val pos = controller.currentPosition.coerceAtLeast(0L)
        val dur = controller.duration.coerceAtLeast(0L)
        val isPlaying = controller.isPlaying
        val speed = controller.playbackParameters.speed

        _state.update {
            it.copy(
                isPlaying = isPlaying,
                positionMs = pos,
                durationMs = dur,
                playbackSpeed = speed
            )
        }
        if (isPlaying) startProgressLoop()

        if (trackId != null) {
            scope.launch(Dispatchers.IO) {
                val pair = dao.getTrackWithAudiobook(trackId) ?: return@launch
                val allTracks = dao.getTracksForAudiobook(pair.audiobook.id)
                val idx = allTracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                _state.update {
                    it.copy(
                        currentBook = pair.audiobook,
                        currentTrack = pair.track,
                        totalBookTracks = allTracks.size,
                        currentTrackIndex = idx,
                        playbackSpeed = pair.audiobook.playbackSpeed,
                        skipSilence = pair.audiobook.skipSilence,
                        silenceIntensity = pair.audiobook.silenceIntensity
                    )
                }
            }
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val c = mediaController ?: break
                _state.update {
                    it.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        durationMs = c.duration.coerceAtLeast(it.currentTrack?.durationMs ?: 0L),
                        isPlaying = c.isPlaying
                    )
                }
                delay(500L)
            }
        }
    }

    fun playAudiobook(book: AudiobookEntity, tracks: List<ChapterTrackEntity>, specificTrack: ChapterTrackEntity? = null) {
        if (tracks.isEmpty()) return
        val targetTrack = specificTrack
            ?: tracks.firstOrNull { !it.isFinished && it.positionMs > 0L }
            ?: tracks.firstOrNull { !it.isFinished }
            ?: tracks.first()

        val controller = mediaController ?: return
        val uri = if (targetTrack.fileUri.startsWith("content://")) {
            Uri.parse(targetTrack.fileUri)
        } else {
            val f = File(targetTrack.fileUri)
            if (f.exists()) Uri.fromFile(f) else return
        }

        val currentTrackId = _state.value.currentTrack?.id
        if (currentTrackId == targetTrack.id && controller.mediaItemCount > 0) {
            togglePlayPause()
            return
        }

        // Save previous position before switching
        currentTrackId?.let { prevId ->
            val prevPos = controller.currentPosition.coerceAtLeast(0L)
            scope.launch(Dispatchers.IO) { dao.updateTrackPosition(prevId, prevPos) }
        }

        val artworkUri = book.artworkUrl
            .takeIf { it.startsWith("content://") || it.startsWith("file://") }
            ?.let { Uri.parse(it) }
        val metadata = MediaMetadata.Builder()
            .setTitle(targetTrack.title)
            .setArtist(book.author.ifBlank { book.title })
            .setAlbumTitle(book.title)
            .setArtworkUri(artworkUri)
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(targetTrack.id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        val rewindMs = if (targetTrack.positionMs > 0L) {
            preferences.settings.value.smartRewindSeconds * 1000L
        } else {
            0L
        }
        val startPos = (targetTrack.positionMs - rewindMs).coerceAtLeast(0L)
        val idx = tracks.indexOfFirst { it.id == targetTrack.id }.coerceAtLeast(0)

        _state.update {
            it.copy(
                currentBook = book,
                currentTrack = targetTrack,
                totalBookTracks = tracks.size,
                currentTrackIndex = idx,
                positionMs = startPos,
                durationMs = targetTrack.durationMs,
                playbackSpeed = book.playbackSpeed,
                skipSilence = book.skipSilence,
                silenceIntensity = book.silenceIntensity
            )
        }

        controller.setMediaItem(mediaItem, startPos)
        controller.playbackParameters = PlaybackParameters(book.playbackSpeed.coerceIn(0.75f, 3.0f))
        sendSkipSilenceCommand(book.skipSilence, book.silenceIntensity)
        controller.prepare()
        controller.play()

        scope.launch(Dispatchers.IO) {
            dao.updateLastPlayed(book.id, System.currentTimeMillis())
        }
    }

    fun togglePlayPause() {
        val c = mediaController ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        val c = mediaController ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        c.seekTo(clamped)
        _state.update { it.copy(positionMs = clamped) }
    }

    fun seekBy(deltaMs: Long) {
        val c = mediaController ?: return
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        c.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun skipTrack(forward: Boolean) {
        val c = mediaController ?: return
        val cmd = if (forward) PlaybackService.CMD_SKIP_BOOK_TRACK_NEXT else PlaybackService.CMD_SKIP_BOOK_TRACK_PREV
        c.sendCustomCommand(SessionCommand(cmd, Bundle.EMPTY), Bundle.EMPTY)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.75f, 3.0f)
        mediaController?.playbackParameters = PlaybackParameters(clamped)
        _state.update { it.copy(playbackSpeed = clamped) }
        val bookId = _state.value.currentBook?.id ?: return
        scope.launch(Dispatchers.IO) { dao.updatePlaybackSpeed(bookId, clamped) }
    }

    fun setSkipSilence(enabled: Boolean, intensity: Int) {
        val normIntensity = intensity.coerceIn(0, 2)
        sendSkipSilenceCommand(enabled, normIntensity)
        _state.update { it.copy(skipSilence = enabled, silenceIntensity = normIntensity) }
        val bookId = _state.value.currentBook?.id ?: return
        scope.launch(Dispatchers.IO) { dao.updateSkipSilence(bookId, enabled, normIntensity) }
    }

    private fun sendSkipSilenceCommand(enabled: Boolean, intensity: Int) {
        val args = Bundle().apply {
            putBoolean(PlaybackService.ARG_SKIP_SILENCE, enabled)
            putInt(PlaybackService.ARG_SILENCE_INTENSITY, intensity.coerceIn(0, 2))
        }
        mediaController?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SET_SKIP_SILENCE, Bundle.EMPTY),
            args
        )
    }

    fun startSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        setSleepAtEndOfTrack(false)
        if (minutes <= 0) {
            _state.update { it.copy(sleepTimerRemainingMs = 0L) }
            return
        }
        val totalMs = minutes * 60_000L
        _state.update { it.copy(sleepTimerRemainingMs = totalMs) }
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0L && isActive) {
                delay(1_000L)
                remaining -= 1_000L
                _state.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            if (isActive) {
                mediaController?.pause()
                _state.update { it.copy(sleepTimerRemainingMs = 0L) }
            }
        }
    }

    fun setSleepAtEndOfTrack(enabled: Boolean) {
        if (enabled) sleepTimerJob?.cancel()
        _state.update {
            it.copy(
                sleepAtEndOfTrack = enabled,
                sleepTimerRemainingMs = if (enabled) 0L else it.sleepTimerRemainingMs
            )
        }
        val args = Bundle().apply { putBoolean(PlaybackService.ARG_SLEEP_END_OF_TRACK, enabled) }
        mediaController?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SET_SLEEP_END_OF_TRACK, Bundle.EMPTY),
            args
        )
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        setSleepAtEndOfTrack(false)
        _state.update { it.copy(sleepTimerRemainingMs = 0L, sleepAtEndOfTrack = false) }
    }

    fun refreshCurrentBookMetadata(bookId: Long) {
        if (_state.value.currentBook?.id != bookId) return
        scope.launch(Dispatchers.IO) {
            val updated = dao.getAudiobook(bookId) ?: return@launch
            _state.update { it.copy(currentBook = updated) }
        }
    }

    fun stopIfPlayingBook(bookId: Long) {
        if (_state.value.currentBook?.id == bookId) {
            mediaController?.stop()
            _state.update { PlayerState() }
        }
    }
}

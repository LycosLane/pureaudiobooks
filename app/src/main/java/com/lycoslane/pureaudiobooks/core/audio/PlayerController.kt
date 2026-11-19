package com.lycoslane.pureaudiobooks.core.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerState(
    val currentBook: AudiobookEntity? = null,
    val currentTrack: ChapterTrackEntity? = null,
    val bookTracks: List<ChapterTrackEntity> = emptyList(),
    val totalBookTracks: Int = 0,
    val currentTrackIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val skipSilence: Boolean = false,
    val silenceIntensity: Int = 1,
    val bookMonoOverride: Int = AudioMonoMode.INHERIT,
    val trackMonoOverride: Int = AudioMonoMode.INHERIT,
    val effectiveMonoMode: Int = AudioMonoMode.OFF,
    val bookNormalizationOverride: Int = AudioOverride.INHERIT,
    val effectiveAudioNormalization: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerPresetMinutes: Int = 0,
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
    private var syncJob: Job? = null
    private var pendingPlay: Triple<AudiobookEntity, List<ChapterTrackEntity>, ChapterTrackEntity?>? = null
    private var hasExplicitIdleSeek: Boolean = false

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        scope.launch {
            preferences.settings
                .map { it.monoMode to it.audioNormalizationEnabled }
                .distinctUntilChanged()
                .collect { refreshEffectiveAudioSettings() }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (mediaController === controller) {
                mediaController = null
                controllerFuture = null
                progressJob?.cancel()
                _state.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun connect() {
        val existing = mediaController
        if (existing != null) {
            if (existing.isConnected) return
            mediaController = null
        }
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                controllerFuture = null
                if (controller == null) return@addListener
                mediaController = controller
                attachListener(controller)
                if (controller.currentMediaItem != null) {
                    syncFromController(controller)
                } else {
                    restoreLastAudiobookIfIdle()
                }
                pendingPlay?.let { (book, tracks, specific) ->
                    pendingPlay = null
                    playAudiobook(book, tracks, specific)
                }
            },
            ContextCompat.getMainExecutor(context)
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

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _state.update { it.copy(playbackSpeed = playbackParameters.speed) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && _state.value.sleepAtEndOfTrack) {
                    _state.update {
                        it.copy(
                            sleepAtEndOfTrack = false,
                            sleepTimerRemainingMs = 0L,
                            sleepTimerPresetMinutes = 0
                        )
                    }
                }
                syncFromController(controller)
            }
        })
    }

    private fun restoreLastAudiobookIfIdle() {
        scope.launch(Dispatchers.IO) {
            if (_state.value.currentTrack != null) return@launch
            val lastTrackId = lastTrackPrefs().getLong(KEY_LAST_TRACK_ID, -1L)
            val fromPref = if (lastTrackId > 0L) dao.getTrackWithAudiobook(lastTrackId) else null
            val validPrefBook = fromPref?.takeIf {
                it.audiobook.deletedAtMs == null && !it.audiobook.isHidden && !it.audiobook.isFinished
            }

            val resolvedPair: Pair<AudiobookEntity, ChapterTrackEntity>? = if (validPrefBook != null) {
                validPrefBook.audiobook to validPrefBook.track
            } else {
                val recent = dao.getMostRecentInProgressAudiobook()
                if (recent != null && recent.tracks.isNotEmpty()) {
                    val sorted = sortTracks(recent.tracks)
                    val target = pickResumeTrack(recent.audiobook, sorted)
                    recent.audiobook to target
                } else {
                    null
                }
            }
            val (book, track) = resolvedPair ?: return@launch
            val sortedTracks = sortTracks(dao.getTracksForAudiobook(book.id))
            if (sortedTracks.isEmpty()) return@launch
            val idx = sortedTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val global = preferences.settings.value
            val effectiveMono = AudioMonoMode.resolveEffective(
                globalMonoMode = global.monoMode,
                bookMonoOverride = book.monoModeOverride,
                trackMonoOverride = track.monoModeOverride
            )
            val effectiveNorm = AudioOverride.resolve(
                globalEnabled = global.audioNormalizationEnabled,
                bookOverride = book.audioNormalizationOverride
            )
            _state.update { st ->
                if (st.currentTrack != null) {
                    st
                } else {
                    st.copy(
                        currentBook = book,
                        currentTrack = track,
                        bookTracks = sortedTracks,
                        totalBookTracks = sortedTracks.size,
                        currentTrackIndex = idx,
                        isPlaying = false,
                        positionMs = track.positionMs.coerceAtLeast(0L),
                        durationMs = track.durationMs.coerceAtLeast(0L),
                        playbackSpeed = book.playbackSpeed.coerceIn(0.50f, 3.00f),
                        skipSilence = book.skipSilence,
                        silenceIntensity = book.silenceIntensity.coerceIn(0, 2),
                        bookMonoOverride = book.monoModeOverride,
                        trackMonoOverride = track.monoModeOverride,
                        effectiveMonoMode = effectiveMono,
                        bookNormalizationOverride = book.audioNormalizationOverride,
                        effectiveAudioNormalization = effectiveNorm
                    )
                }
            }
        }
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
                durationMs = if (dur > 0L) dur else it.durationMs,
                playbackSpeed = speed
            )
        }
        if (isPlaying) startProgressLoop()

        if (trackId != null) {
            syncJob?.cancel()
            syncJob = scope.launch(Dispatchers.IO) {
                val pair = dao.getTrackWithAudiobook(trackId) ?: return@launch
                val allTracks = sortTracks(dao.getTracksForAudiobook(pair.audiobook.id))
                val idx = allTracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                val global = preferences.settings.value
                val effectiveMono = AudioMonoMode.resolveEffective(
                    globalMonoMode = global.monoMode,
                    bookMonoOverride = pair.audiobook.monoModeOverride,
                    trackMonoOverride = pair.track.monoModeOverride
                )
                val effectiveNorm = AudioOverride.resolve(
                    globalEnabled = global.audioNormalizationEnabled,
                    bookOverride = pair.audiobook.audioNormalizationOverride
                )
                _state.update {
                    it.copy(
                        currentBook = pair.audiobook,
                        currentTrack = pair.track,
                        bookTracks = allTracks,
                        totalBookTracks = allTracks.size,
                        currentTrackIndex = idx,
                        playbackSpeed = pair.audiobook.playbackSpeed.coerceIn(0.50f, 3.00f),
                        skipSilence = pair.audiobook.skipSilence,
                        silenceIntensity = pair.audiobook.silenceIntensity.coerceIn(0, 2),
                        bookMonoOverride = pair.audiobook.monoModeOverride,
                        trackMonoOverride = pair.track.monoModeOverride,
                        effectiveMonoMode = effectiveMono,
                        bookNormalizationOverride = pair.audiobook.audioNormalizationOverride,
                        effectiveAudioNormalization = effectiveNorm
                    )
                }
                withContext(Dispatchers.Main) {
                    val mc = mediaController
                    if (mc != null && mc.isConnected && mc.currentMediaItem?.mediaId == trackId.toString()) {
                        sendMonoModeCommand(mc, effectiveMono)
                        sendAudioNormalizationCommand(mc, effectiveNorm)
                    }
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

    fun playAudiobook(
        book: AudiobookEntity,
        tracks: List<ChapterTrackEntity>,
        specificTrack: ChapterTrackEntity? = null
    ) {
        if (tracks.isEmpty()) return
        val sortedTracks = sortTracks(tracks)
        val targetTrack = specificTrack ?: pickResumeTrack(book, sortedTracks)

        lastTrackPrefs().edit().putLong(KEY_LAST_TRACK_ID, targetTrack.id).apply()

        val controller = mediaController?.takeIf { it.isConnected }
        if (controller == null) {
            mediaController = null
            pendingPlay = Triple(book, sortedTracks, targetTrack)
            connect()
            return
        }

        val currentTrackId = _state.value.currentTrack?.id
        if (currentTrackId == targetTrack.id &&
            controller.currentMediaItem?.mediaId == targetTrack.id.toString() &&
            controller.playbackState != Player.STATE_ENDED
        ) {
            togglePlayPause()
            return
        }

        val uri = if (targetTrack.fileUri.startsWith("content://")) {
            Uri.parse(targetTrack.fileUri)
        } else {
            val f = File(targetTrack.fileUri)
            if (f.exists()) Uri.fromFile(f) else return
        }

        if (currentTrackId != null && controller.currentMediaItem != null) {
            val prevPos = controller.currentPosition.coerceAtLeast(0L)
            scope.launch(Dispatchers.IO) { dao.updateTrackPosition(currentTrackId, prevPos) }
        }

        val useExplicitIdlePos = hasExplicitIdleSeek && currentTrackId == targetTrack.id
        val explicitPosMs = _state.value.positionMs
        hasExplicitIdleSeek = false

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

        val global = preferences.settings.value
        val rewindMs = if (targetTrack.positionMs > 0L) {
            global.smartRewindSeconds * 1000L
        } else {
            0L
        }
        val startPos = when {
            useExplicitIdlePos -> explicitPosMs.coerceAtLeast(0L)
            targetTrack.positionMs > 0L -> (targetTrack.positionMs - rewindMs).coerceAtLeast(0L)
            else -> 0L
        }
        val idx = sortedTracks.indexOfFirst { it.id == targetTrack.id }.coerceAtLeast(0)
        val speed = book.playbackSpeed.coerceIn(0.50f, 3.00f)
        val intensity = book.silenceIntensity.coerceIn(0, 2)
        val effectiveMono = AudioMonoMode.resolveEffective(
            globalMonoMode = global.monoMode,
            bookMonoOverride = book.monoModeOverride,
            trackMonoOverride = targetTrack.monoModeOverride
        )
        val effectiveNorm = AudioOverride.resolve(
            globalEnabled = global.audioNormalizationEnabled,
            bookOverride = book.audioNormalizationOverride
        )

        _state.update {
            it.copy(
                currentBook = book,
                currentTrack = targetTrack,
                bookTracks = sortedTracks,
                totalBookTracks = sortedTracks.size,
                currentTrackIndex = idx,
                positionMs = startPos,
                durationMs = targetTrack.durationMs,
                playbackSpeed = speed,
                skipSilence = book.skipSilence,
                silenceIntensity = intensity,
                bookMonoOverride = book.monoModeOverride,
                trackMonoOverride = targetTrack.monoModeOverride,
                effectiveMonoMode = effectiveMono,
                bookNormalizationOverride = book.audioNormalizationOverride,
                effectiveAudioNormalization = effectiveNorm
            )
        }

        controller.setMediaItem(mediaItem, startPos)
        controller.playbackParameters = PlaybackParameters(speed)
        sendSkipSilenceCommand(controller, book.skipSilence, intensity)
        sendMonoModeCommand(controller, effectiveMono)
        sendAudioNormalizationCommand(controller, effectiveNorm)
        controller.prepare()
        controller.play()

        scope.launch(Dispatchers.IO) {
            dao.updateLastTrack(book.id, targetTrack.id, System.currentTimeMillis())
        }
    }

    fun togglePlayPause() {
        val c = mediaController
        if (c == null || !c.isConnected || c.currentMediaItem == null) {
            val st = _state.value
            val book = st.currentBook ?: return
            val track = st.currentTrack ?: return
            val tracks = st.bookTracks.ifEmpty { listOf(track) }
            playAudiobook(book, tracks, track)
            return
        }
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        val dur = _state.value.durationMs
        val clamped = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        val c = mediaController
        if (c != null && c.isConnected && c.currentMediaItem != null) {
            c.seekTo(clamped)
        } else {
            hasExplicitIdleSeek = true
        }
        _state.update {
            it.copy(
                positionMs = clamped,
                currentTrack = it.currentTrack?.copy(positionMs = clamped)
            )
        }
        val trackId = _state.value.currentTrack?.id ?: return
        scope.launch(Dispatchers.IO) { dao.updateTrackPosition(trackId, clamped) }
    }

    fun seekBy(deltaMs: Long) {
        val c = mediaController
        val basePos = if (c != null && c.isConnected && c.currentMediaItem != null) {
            c.currentPosition
        } else {
            _state.value.positionMs
        }
        seekTo(basePos + deltaMs)
    }

    fun skipTrack(forward: Boolean) {
        val c = mediaController
        if (c != null && c.isConnected && c.currentMediaItem != null) {
            val cmd = if (forward) PlaybackService.CMD_SKIP_BOOK_TRACK_NEXT else PlaybackService.CMD_SKIP_BOOK_TRACK_PREV
            c.sendCustomCommand(SessionCommand(cmd, Bundle.EMPTY), Bundle.EMPTY)
            return
        }
        val st = _state.value
        val book = st.currentBook ?: return
        val tracks = st.bookTracks
        if (tracks.isEmpty()) return
        val nextIdx = if (forward) {
            (st.currentTrackIndex + 1).coerceAtMost(tracks.lastIndex)
        } else {
            (st.currentTrackIndex - 1).coerceAtLeast(0)
        }
        playAudiobook(book, tracks, tracks[nextIdx])
    }

    fun setPlaybackSpeed(speed: Float) {
        val bookId = _state.value.currentBook?.id ?: return
        setBookPlaybackSpeed(bookId, speed)
    }

    fun setBookPlaybackSpeed(bookId: Long, speed: Float) {
        val clamped = speed.coerceIn(0.50f, 3.00f)
        if (_state.value.currentBook?.id == bookId) {
            mediaController?.playbackParameters = PlaybackParameters(clamped)
            _state.update { st ->
                st.copy(
                    playbackSpeed = clamped,
                    currentBook = st.currentBook?.copy(playbackSpeed = clamped)
                )
            }
        }
        scope.launch(Dispatchers.IO) { dao.updatePlaybackSpeed(bookId, clamped) }
    }

    fun setSkipSilence(enabled: Boolean, intensity: Int) {
        val bookId = _state.value.currentBook?.id ?: return
        setBookSilenceMode(bookId, enabled, intensity)
    }

    fun setBookSilenceMode(bookId: Long, enabled: Boolean, intensity: Int) {
        val normIntensity = intensity.coerceIn(0, 2)
        if (_state.value.currentBook?.id == bookId) {
            mediaController?.let { sendSkipSilenceCommand(it, enabled, normIntensity) }
            _state.update { st ->
                st.copy(
                    skipSilence = enabled,
                    silenceIntensity = normIntensity,
                    currentBook = st.currentBook?.copy(
                        skipSilence = enabled,
                        silenceIntensity = normIntensity
                    )
                )
            }
        }
        scope.launch(Dispatchers.IO) { dao.updateSkipSilence(bookId, enabled, normIntensity) }
    }

    fun setTrackMonoOverride(trackId: Long, mode: Int) {
        val normalized = AudioMonoMode.normalizeOverride(mode)
        val st = _state.value
        if (st.currentTrack?.id == trackId) {
            val global = preferences.settings.value
            val effectiveMono = AudioMonoMode.resolveEffective(
                globalMonoMode = global.monoMode,
                bookMonoOverride = st.currentBook?.monoModeOverride ?: st.bookMonoOverride,
                trackMonoOverride = normalized
            )
            mediaController?.let { sendMonoModeCommand(it, effectiveMono) }
            _state.update { cur ->
                cur.copy(
                    trackMonoOverride = normalized,
                    effectiveMonoMode = effectiveMono,
                    currentTrack = cur.currentTrack?.copy(monoModeOverride = normalized)
                )
            }
        }
        scope.launch(Dispatchers.IO) { dao.setTrackMonoModeOverride(trackId, normalized) }
    }

    fun setBookMonoOverride(bookId: Long, mode: Int) {
        val normalized = AudioMonoMode.normalizeOverride(mode)
        if (_state.value.currentBook?.id == bookId) {
            val global = preferences.settings.value
            val effectiveMono = AudioMonoMode.resolveEffective(
                globalMonoMode = global.monoMode,
                bookMonoOverride = normalized,
                trackMonoOverride = _state.value.trackMonoOverride
            )
            mediaController?.let { sendMonoModeCommand(it, effectiveMono) }
            _state.update { st ->
                st.copy(
                    bookMonoOverride = normalized,
                    effectiveMonoMode = effectiveMono,
                    currentBook = st.currentBook?.copy(monoModeOverride = normalized)
                )
            }
        }
        scope.launch(Dispatchers.IO) { dao.setBookMonoModeOverride(bookId, normalized) }
    }

    fun setBookNormalizationOverride(bookId: Long, override: Int) {
        val normalized = AudioOverride.normalize(override)
        if (_state.value.currentBook?.id == bookId) {
            val global = preferences.settings.value
            val effectiveNorm = AudioOverride.resolve(
                globalEnabled = global.audioNormalizationEnabled,
                bookOverride = normalized
            )
            mediaController?.let { sendAudioNormalizationCommand(it, effectiveNorm) }
            _state.update { st ->
                st.copy(
                    bookNormalizationOverride = normalized,
                    effectiveAudioNormalization = effectiveNorm,
                    currentBook = st.currentBook?.copy(audioNormalizationOverride = normalized)
                )
            }
        }
        scope.launch(Dispatchers.IO) { dao.setBookAudioNormalizationOverride(bookId, normalized) }
    }

    fun refreshEffectiveAudioSettings() {
        val st = _state.value
        val global = preferences.settings.value
        val effectiveMono = AudioMonoMode.resolveEffective(
            globalMonoMode = global.monoMode,
            bookMonoOverride = st.currentBook?.monoModeOverride ?: st.bookMonoOverride,
            trackMonoOverride = st.currentTrack?.monoModeOverride ?: st.trackMonoOverride
        )
        val effectiveNorm = AudioOverride.resolve(
            globalEnabled = global.audioNormalizationEnabled,
            bookOverride = st.currentBook?.audioNormalizationOverride ?: st.bookNormalizationOverride
        )
        _state.update {
            it.copy(
                effectiveMonoMode = effectiveMono,
                effectiveAudioNormalization = effectiveNorm
            )
        }
        mediaController?.takeIf { it.isConnected }?.let { mc ->
            sendMonoModeCommand(mc, effectiveMono)
            sendAudioNormalizationCommand(mc, effectiveNorm)
        }
    }

    private fun sendMonoModeCommand(mc: MediaController, mode: Int) {
        val args = Bundle().apply {
            putInt(PlaybackService.ARG_MONO_MODE, AudioMonoMode.normalizeConcrete(mode))
        }
        mc.sendCustomCommand(SessionCommand(PlaybackService.CMD_SET_MONO_MODE, Bundle.EMPTY), args)
    }

    private fun sendAudioNormalizationCommand(mc: MediaController, enabled: Boolean) {
        val args = Bundle().apply {
            putBoolean(PlaybackService.ARG_AUDIO_NORMALIZATION, enabled)
        }
        mc.sendCustomCommand(SessionCommand(PlaybackService.CMD_SET_AUDIO_NORMALIZATION, Bundle.EMPTY), args)
    }

    private fun sendSkipSilenceCommand(mc: MediaController, enabled: Boolean, intensity: Int) {
        val args = Bundle().apply {
            putBoolean(PlaybackService.ARG_SKIP_SILENCE, enabled)
            putInt(PlaybackService.ARG_SILENCE_INTENSITY, intensity.coerceIn(0, 2))
        }
        mc.sendCustomCommand(SessionCommand(PlaybackService.CMD_SET_SKIP_SILENCE, Bundle.EMPTY), args)
    }

    fun startSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (_state.value.sleepAtEndOfTrack) {
            sendSleepEndOfTrackCommand(false)
        }
        if (minutes <= 0) {
            _state.update {
                it.copy(
                    sleepTimerRemainingMs = 0L,
                    sleepTimerPresetMinutes = 0,
                    sleepAtEndOfTrack = false
                )
            }
            return
        }
        val clampedMinutes = minutes.coerceIn(1, 120)
        val totalMs = clampedMinutes * 60_000L
        val endElapsed = SystemClock.elapsedRealtime() + totalMs
        _state.update {
            it.copy(
                sleepTimerRemainingMs = totalMs,
                sleepTimerPresetMinutes = clampedMinutes,
                sleepAtEndOfTrack = false
            )
        }
        sleepTimerJob = scope.launch {
            while (isActive) {
                val remaining = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                _state.update {
                    it.copy(
                        sleepTimerRemainingMs = remaining,
                        sleepTimerPresetMinutes = if (remaining == 0L) 0 else clampedMinutes
                    )
                }
                if (remaining == 0L) break
                delay(1_000L)
            }
            if (isActive) {
                mediaController?.pause()
            }
        }
    }

    fun setSleepAtEndOfTrack(enabled: Boolean) {
        if (enabled) sleepTimerJob?.cancel()
        _state.update {
            it.copy(
                sleepAtEndOfTrack = enabled,
                sleepTimerRemainingMs = if (enabled) 0L else it.sleepTimerRemainingMs,
                sleepTimerPresetMinutes = if (enabled) 0 else it.sleepTimerPresetMinutes
            )
        }
        sendSleepEndOfTrackCommand(enabled)
    }

    private fun sendSleepEndOfTrackCommand(enabled: Boolean) {
        val args = Bundle().apply { putBoolean(PlaybackService.ARG_SLEEP_END_OF_TRACK, enabled) }
        mediaController?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SET_SLEEP_END_OF_TRACK, Bundle.EMPTY),
            args
        )
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sendSleepEndOfTrackCommand(false)
        _state.update {
            it.copy(
                sleepTimerRemainingMs = 0L,
                sleepTimerPresetMinutes = 0,
                sleepAtEndOfTrack = false
            )
        }
    }

    fun refreshCurrentBookMetadata(bookId: Long) {
        if (_state.value.currentBook?.id != bookId) return
        scope.launch(Dispatchers.IO) {
            val updated = dao.getAudiobook(bookId) ?: return@launch
            val tracks = sortTracks(dao.getTracksForAudiobook(bookId))
            val curTrackId = _state.value.currentTrack?.id
            val updatedTrack = tracks.firstOrNull { it.id == curTrackId } ?: _state.value.currentTrack
            val global = preferences.settings.value
            val effectiveMono = AudioMonoMode.resolveEffective(
                globalMonoMode = global.monoMode,
                bookMonoOverride = updated.monoModeOverride,
                trackMonoOverride = updatedTrack?.monoModeOverride ?: AudioMonoMode.INHERIT
            )
            val effectiveNorm = AudioOverride.resolve(
                globalEnabled = global.audioNormalizationEnabled,
                bookOverride = updated.audioNormalizationOverride
            )
            _state.update {
                it.copy(
                    currentBook = updated,
                    currentTrack = updatedTrack,
                    bookTracks = tracks,
                    totalBookTracks = tracks.size,
                    playbackSpeed = updated.playbackSpeed.coerceIn(0.50f, 3.00f),
                    skipSilence = updated.skipSilence,
                    silenceIntensity = updated.silenceIntensity.coerceIn(0, 2),
                    bookMonoOverride = updated.monoModeOverride,
                    trackMonoOverride = updatedTrack?.monoModeOverride ?: AudioMonoMode.INHERIT,
                    effectiveMonoMode = effectiveMono,
                    bookNormalizationOverride = updated.audioNormalizationOverride,
                    effectiveAudioNormalization = effectiveNorm
                )
            }
        }
    }

    fun stopIfPlayingBook(bookId: Long) {
        if (_state.value.currentBook?.id == bookId) {
            progressJob?.cancel()
            sleepTimerJob?.cancel()
            hasExplicitIdleSeek = false
            lastTrackPrefs().edit().remove(KEY_LAST_TRACK_ID).apply()
            mediaController?.let { mc ->
                mc.stop()
                mc.clearMediaItems()
            }
            _state.value = PlayerState()
        }
    }

    private fun pickResumeTrack(book: AudiobookEntity, sortedTracks: List<ChapterTrackEntity>): ChapterTrackEntity {
        return sortedTracks.firstOrNull { it.id == book.lastTrackId && !it.isFinished }
            ?: sortedTracks.firstOrNull { !it.isFinished && it.positionMs > 0L }
            ?: sortedTracks.firstOrNull { !it.isFinished }
            ?: sortedTracks.first()
    }

    private fun sortTracks(tracks: List<ChapterTrackEntity>): List<ChapterTrackEntity> =
        tracks.sortedWith(
            compareBy<ChapterTrackEntity> { it.sortOrder }
                .thenBy { it.trackNumber }
                .thenBy { it.title }
        )

    private fun lastTrackPrefs() = context.getSharedPreferences("playback_state", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_TRACK_ID = "last_track_id"
    }
}

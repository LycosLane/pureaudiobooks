package com.lycoslane.pureaudiobooks.core.audio

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lycoslane.pureaudiobooks.MainActivity
import com.lycoslane.pureaudiobooks.PureAudiobooksApplication
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var positionSaveJob: Job? = null
    private var skipTrackJob: Job? = null
    private var resolveAudioJob: Job? = null
    private var lastPausedElapsedMs: Long = 0L

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var attachedAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var currentEffectiveNormalization: Boolean = false

    @Volatile
    private var sleepAtEndOfTrack: Boolean = false

    @Volatile
    private var currentEffectiveSmartRewindSeconds: Int = 10

    @OptIn(UnstableApi::class)
    private lateinit var monoProcessor: MonoMixAudioProcessor

    @OptIn(UnstableApi::class)
    private lateinit var silenceProcessor: DelegatingSilenceAudioProcessor

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        monoProcessor = MonoMixAudioProcessor()
        silenceProcessor = DelegatingSilenceAudioProcessor()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(monoProcessor, silenceProcessor))
                    .build()
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(30_000L)
            .build()

        val app = applicationContext as PureAudiobooksApplication
        currentEffectiveSmartRewindSeconds = app.preferences.settings.value.smartRewindSeconds
        serviceScope.launch {
            app.preferences.settings
                .map { s ->
                    listOf(
                        s.defaultPlaybackSpeed,
                        s.defaultSkipSilence,
                        s.defaultSilenceIntensity,
                        s.audioNormalizationEnabled,
                        s.monoMode,
                        s.smartRewindSeconds
                    )
                }
                .distinctUntilChanged()
                .collect { resolveAndApplyCurrentAudioSettings() }
        }

        val smartRewindPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .addAll(
                        COMMAND_SEEK_TO_NEXT,
                        COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        COMMAND_SEEK_TO_PREVIOUS,
                        COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                    )
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
                lastPausedElapsedMs = 0L
                super.setMediaItem(mediaItem, startPositionMs)
            }

            override fun stop() {
                lastPausedElapsedMs = 0L
                super.stop()
            }

            override fun play() {
                if (!player.playWhenReady && player.playbackState != STATE_ENDED && lastPausedElapsedMs > 0L) {
                    val pausedDuration = SystemClock.elapsedRealtime() - lastPausedElapsedMs
                    lastPausedElapsedMs = 0L
                    val rewindSecs = currentEffectiveSmartRewindSeconds
                    if (rewindSecs > 0 && pausedDuration >= 30_000L) {
                        val target = (player.currentPosition - rewindSecs * 1000L).coerceAtLeast(0L)
                        player.seekTo(target)
                    }
                }
                super.play()
            }

            override fun pause() {
                if (player.playbackState != STATE_ENDED && player.playbackState != STATE_IDLE) {
                    lastPausedElapsedMs = SystemClock.elapsedRealtime()
                }
                super.pause()
            }

            override fun seekToNext() = handleBluetoothSkip(forward = true)
            override fun seekToNextMediaItem() = handleBluetoothSkip(forward = true)
            override fun seekToPrevious() = handleBluetoothSkip(forward = false)
            override fun seekToPreviousMediaItem() = handleBluetoothSkip(forward = false)
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error (${error.errorCodeName}): ${error.message}", error)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                currentAudioSessionId = audioSessionId
                applyAudioNormalization(currentEffectiveNormalization)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    lastPausedElapsedMs = 0L
                    startPositionSaver()
                } else {
                    if (player.playbackState != Player.STATE_ENDED &&
                        player.playbackState != Player.STATE_IDLE &&
                        !player.playWhenReady
                    ) {
                        lastPausedElapsedMs = SystemClock.elapsedRealtime()
                    }
                    saveCurrentPosition()
                    positionSaveJob?.cancel()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    positionSaveJob?.cancel()
                    lastPausedElapsedMs = 0L
                    val trackId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                    val stopForSleep = sleepAtEndOfTrack
                    sleepAtEndOfTrack = false

                    serviceScope.launch(Dispatchers.IO) {
                        val dao = PureAudiobooksDatabase.getInstance(applicationContext).dao()
                        val track = dao.getTrack(trackId) ?: return@launch
                        val now = System.currentTimeMillis()
                        dao.setTrackFinished(trackId, true)
                        dao.updateTrackPosition(trackId, 0L)

                        val tracks = dao.getTracksForAudiobook(track.audiobookId)
                        val idx = tracks.indexOfFirst { it.id == trackId }
                        val nextTrack = if (idx >= 0 && idx + 1 < tracks.size) tracks[idx + 1] else null

                        if (nextTrack != null && !stopForSleep) {
                            withContext(Dispatchers.Main) {
                                playTrackDirect(nextTrack.id, resumeWithRewind = false)
                            }
                        } else if (nextTrack != null && stopForSleep) {
                            lastTrackPrefs().edit().putLong(KEY_LAST_TRACK_ID, nextTrack.id).apply()
                            dao.updateLastTrack(track.audiobookId, nextTrack.id, now)
                            withContext(Dispatchers.Main) {
                                prepareTrackPaused(nextTrack.id)
                            }
                        } else if (nextTrack == null) {
                            dao.setAudiobookFinished(track.audiobookId, true, now)
                            lastTrackPrefs().edit().remove(KEY_LAST_TRACK_ID).apply()
                            val autoTrashDays = app.preferences.settings.value.autoMoveFinishedToTrashDays
                            if (autoTrashDays == 0) {
                                dao.setDeletedAt(track.audiobookId, now)
                                withContext(Dispatchers.Main) {
                                    player.stop()
                                    player.clearMediaItems()
                                }
                            }
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                lastTrackPrefs().edit().putLong(KEY_LAST_TRACK_ID, id).apply()
            }
        })

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, smartRewindPlayer)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val isTrusted = controller.packageName == packageName ||
                        session.isMediaNotificationController(controller) ||
                        session.isAutoCompanionController(controller) ||
                        controller.packageName in TRUSTED_MEDIA_CONTROLLERS

                    return if (isTrusted) {
                        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                            .buildUpon()
                            .add(SessionCommand(CMD_SET_SKIP_SILENCE, Bundle.EMPTY))
                            .add(SessionCommand(CMD_SET_MONO_MODE, Bundle.EMPTY))
                            .add(SessionCommand(CMD_SET_AUDIO_NORMALIZATION, Bundle.EMPTY))
                            .add(SessionCommand(CMD_SKIP_BOOK_TRACK_NEXT, Bundle.EMPTY))
                            .add(SessionCommand(CMD_SKIP_BOOK_TRACK_PREV, Bundle.EMPTY))
                            .add(SessionCommand(CMD_SET_SLEEP_END_OF_TRACK, Bundle.EMPTY))
                            .add(SessionCommand(CMD_SET_SMART_REWIND, Bundle.EMPTY))
                            .build()
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(sessionCommands)
                            .build()
                    } else {
                        MediaSession.ConnectionResult.reject()
                    }
                }

                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                    serviceScope.launch {
                        try {
                            val id = lastTrackPrefs().getLong(KEY_LAST_TRACK_ID, -1L)
                            val withBook = if (id < 0) null else withContext(Dispatchers.IO) {
                                PureAudiobooksDatabase.getInstance(applicationContext).dao().getTrackWithAudiobook(id)
                            }
                            if (withBook == null) {
                                future.setException(UnsupportedOperationException("Nothing to resume"))
                                return@launch
                            }
                            val item = buildMediaItem(withBook.track, withBook.audiobook)
                            if (item == null) {
                                future.setException(UnsupportedOperationException("Nothing to resume"))
                            } else {
                                val globalSettings = app.preferences.settings.value
                                val effectiveSpeed = SpeedOverride.resolve(
                                    globalSpeed = globalSettings.defaultPlaybackSpeed,
                                    bookOverride = withBook.audiobook.speedOverride
                                )
                                val (effectiveSkipSilence, effectiveIntensity) = SilenceOverride.resolve(
                                    globalEnabled = globalSettings.defaultSkipSilence,
                                    globalIntensity = globalSettings.defaultSilenceIntensity,
                                    bookOverride = withBook.audiobook.silenceModeOverride
                                )
                                val effectiveMono = AudioMonoMode.resolveEffective(
                                    globalMonoMode = globalSettings.monoMode,
                                    bookMonoOverride = withBook.audiobook.monoModeOverride,
                                    trackMonoOverride = withBook.track.monoModeOverride
                                )
                                val effectiveNorm = AudioOverride.resolve(
                                    globalEnabled = globalSettings.audioNormalizationEnabled,
                                    bookOverride = withBook.audiobook.audioNormalizationOverride
                                )
                                currentEffectiveSmartRewindSeconds = SmartRewindOverride.resolve(
                                    globalSeconds = globalSettings.smartRewindSeconds,
                                    bookOverride = withBook.audiobook.smartRewindOverride
                                )
                                withContext(Dispatchers.Main) {
                                    player.playbackParameters = PlaybackParameters(effectiveSpeed)
                                    updateSilenceProcessors(effectiveSkipSilence, effectiveIntensity)
                                    monoProcessor.setMode(effectiveMono)
                                    currentEffectiveNormalization = effectiveNorm
                                    applyAudioNormalization(effectiveNorm)
                                }
                                future.set(
                                    MediaSession.MediaItemsWithStartPosition(
                                        listOf(item), 0, withBook.track.positionMs.coerceAtLeast(0L)
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "onPlaybackResumption failed", e)
                            future.setException(e)
                        }
                    }
                    return future
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (controller.packageName == packageName) {
                        when (customCommand.customAction) {
                            CMD_SET_SKIP_SILENCE -> {
                                val enabled = args.getBoolean(ARG_SKIP_SILENCE, false)
                                val intensity = args.getInt(ARG_SILENCE_INTENSITY, 1).coerceIn(0, 2)
                                updateSilenceProcessors(enabled, intensity)
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            CMD_SET_MONO_MODE -> {
                                val mode = AudioMonoMode.normalizeConcrete(args.getInt(ARG_MONO_MODE, AudioMonoMode.OFF))
                                monoProcessor.setMode(mode)
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            CMD_SET_AUDIO_NORMALIZATION -> {
                                val enabled = args.getBoolean(ARG_AUDIO_NORMALIZATION, false)
                                currentEffectiveNormalization = enabled
                                applyAudioNormalization(enabled)
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            CMD_SKIP_BOOK_TRACK_NEXT -> {
                                skipBookTrack(forward = true)
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            CMD_SKIP_BOOK_TRACK_PREV -> {
                                skipBookTrack(forward = false)
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            CMD_SET_SLEEP_END_OF_TRACK -> {
                                sleepAtEndOfTrack = args.getBoolean(ARG_SLEEP_END_OF_TRACK, false)
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            CMD_SET_SMART_REWIND -> {
                                currentEffectiveSmartRewindSeconds =
                                    args.getInt(ARG_SMART_REWIND_SECONDS, 10).coerceIn(0, 120)
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun updateSilenceProcessors(enabled: Boolean, intensity: Int) {
        silenceProcessor.setMode(enabled, intensity)
    }

    private fun resolveAndApplyCurrentAudioSettings() {
        resolveAudioJob?.cancel()
        val app = applicationContext as PureAudiobooksApplication
        val global = app.preferences.settings.value
        val trackId = player.currentMediaItem?.mediaId?.toLongOrNull()
        if (trackId == null) {
            player.playbackParameters = PlaybackParameters(global.defaultPlaybackSpeed.coerceIn(0.50f, 3.00f))
            updateSilenceProcessors(global.defaultSkipSilence, global.defaultSilenceIntensity)
            monoProcessor.setMode(global.monoMode)
            currentEffectiveNormalization = global.audioNormalizationEnabled
            currentEffectiveSmartRewindSeconds = global.smartRewindSeconds
            applyAudioNormalization(currentEffectiveNormalization)
            return
        }
        resolveAudioJob = serviceScope.launch {
            val pair = withContext(Dispatchers.IO) {
                PureAudiobooksDatabase.getInstance(applicationContext).dao().getTrackWithAudiobook(trackId)
            }
            if (player.currentMediaItem?.mediaId?.toLongOrNull() != trackId) return@launch
            val currentGlobal = app.preferences.settings.value
            val effectiveSpeed = if (pair != null) {
                SpeedOverride.resolve(currentGlobal.defaultPlaybackSpeed, pair.audiobook.speedOverride)
            } else {
                currentGlobal.defaultPlaybackSpeed.coerceIn(0.50f, 3.00f)
            }
            val (effectiveSilence, effectiveIntensity) = if (pair != null) {
                SilenceOverride.resolve(
                    globalEnabled = currentGlobal.defaultSkipSilence,
                    globalIntensity = currentGlobal.defaultSilenceIntensity,
                    bookOverride = pair.audiobook.silenceModeOverride
                )
            } else {
                currentGlobal.defaultSkipSilence to currentGlobal.defaultSilenceIntensity.coerceIn(0, 2)
            }
            val effectiveMono = if (pair != null) {
                AudioMonoMode.resolveEffective(
                    globalMonoMode = currentGlobal.monoMode,
                    bookMonoOverride = pair.audiobook.monoModeOverride,
                    trackMonoOverride = pair.track.monoModeOverride
                )
            } else {
                AudioMonoMode.normalizeConcrete(currentGlobal.monoMode)
            }
            val effectiveNorm = if (pair != null) {
                AudioOverride.resolve(
                    globalEnabled = currentGlobal.audioNormalizationEnabled,
                    bookOverride = pair.audiobook.audioNormalizationOverride
                )
            } else {
                currentGlobal.audioNormalizationEnabled
            }
            currentEffectiveSmartRewindSeconds = if (pair != null) {
                SmartRewindOverride.resolve(currentGlobal.smartRewindSeconds, pair.audiobook.smartRewindOverride)
            } else {
                currentGlobal.smartRewindSeconds
            }
            player.playbackParameters = PlaybackParameters(effectiveSpeed)
            updateSilenceProcessors(effectiveSilence, effectiveIntensity)
            monoProcessor.setMode(effectiveMono)
            currentEffectiveNormalization = effectiveNorm
            applyAudioNormalization(effectiveNorm)
        }
    }

    private fun releaseDynamicsProcessing() {
        val dp = dynamicsProcessing ?: return
        try {
            runCatching { dp.enabled = false }
                .onFailure { Log.w(TAG, "Failed to disable DynamicsProcessing", it) }
        } finally {
            runCatching { dp.release() }
                .onFailure { Log.w(TAG, "Failed to release DynamicsProcessing", it) }
            dynamicsProcessing = null
            attachedAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    private fun applyAudioNormalization(enabled: Boolean) {
        runCatching {
            if (!enabled || currentAudioSessionId == C.AUDIO_SESSION_ID_UNSET || currentAudioSessionId == 0) {
                releaseDynamicsProcessing()
                return
            }
            if (dynamicsProcessing != null && attachedAudioSessionId != currentAudioSessionId) {
                releaseDynamicsProcessing()
            }
            if (dynamicsProcessing == null) {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, false, 0, true, 1, false, 0, true
                ).apply {
                    val mbc = DynamicsProcessing.Mbc(true, true, 1).apply {
                        setBand(0, DynamicsProcessing.MbcBand(true, 20000f, 5f, 120f, 3.0f, -20f, 15f, -45f, 2.0f, 0f, 3.5f))
                    }
                    setMbcAllChannelsTo(mbc)
                    val limiter = DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 4.0f, -1.5f, 0f)
                    setLimiterAllChannelsTo(limiter)
                }.build()
                dynamicsProcessing = DynamicsProcessing(0, currentAudioSessionId, config)
                attachedAudioSessionId = currentAudioSessionId
            }
            dynamicsProcessing?.enabled = true
        }.onFailure {
            Log.w(TAG, "Failed to apply audio normalization for session $currentAudioSessionId", it)
            releaseDynamicsProcessing()
        }
    }

    private fun handleBluetoothSkip(forward: Boolean) {
        val seekMode = (applicationContext as PureAudiobooksApplication)
            .preferences.settings.value.bluetoothNextPrevSeeks
        if (seekMode) {
            if (forward) player.seekForward() else player.seekBack()
        } else {
            skipBookTrack(forward)
        }
    }

    private fun skipBookTrack(forward: Boolean) {
        saveCurrentPosition()
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        skipTrackJob?.cancel()
        skipTrackJob = serviceScope.launch {
            val dao = PureAudiobooksDatabase.getInstance(applicationContext).dao()
            val pair = withContext(Dispatchers.IO) { dao.getTrackWithAudiobook(currentId) } ?: return@launch
            val tracks = withContext(Dispatchers.IO) { dao.getTracksForAudiobook(pair.audiobook.id) }
            if (tracks.isEmpty()) return@launch
            val idx = tracks.indexOfFirst { it.id == currentId }
            val nextIdx = if (idx < 0) 0 else if (forward) (idx + 1).coerceAtMost(tracks.lastIndex) else (idx - 1).coerceAtLeast(0)
            if (nextIdx == idx && !forward && player.currentPosition > 3_000L) {
                player.seekTo(0L)
                return@launch
            }
            if (nextIdx != idx) {
                playTrackDirect(tracks[nextIdx].id, resumeWithRewind = false)
            }
        }
    }

    private fun playTrackDirect(trackId: Long, resumeWithRewind: Boolean, autoPlay: Boolean = true) {
        skipTrackJob?.cancel()
        skipTrackJob = serviceScope.launch {
            val dao = PureAudiobooksDatabase.getInstance(applicationContext).dao()
            val pair = withContext(Dispatchers.IO) { dao.getTrackWithAudiobook(trackId) } ?: return@launch
            val item = buildMediaItem(pair.track, pair.audiobook) ?: return@launch
            val globalSettings = (applicationContext as PureAudiobooksApplication).preferences.settings.value
            val effectiveSpeed = SpeedOverride.resolve(
                globalSpeed = globalSettings.defaultPlaybackSpeed,
                bookOverride = pair.audiobook.speedOverride
            )
            val (effectiveSkipSilence, effectiveIntensity) = SilenceOverride.resolve(
                globalEnabled = globalSettings.defaultSkipSilence,
                globalIntensity = globalSettings.defaultSilenceIntensity,
                bookOverride = pair.audiobook.silenceModeOverride
            )
            val effectiveSmartRewind = SmartRewindOverride.resolve(
                globalSeconds = globalSettings.smartRewindSeconds,
                bookOverride = pair.audiobook.smartRewindOverride
            )
            currentEffectiveSmartRewindSeconds = effectiveSmartRewind
            val rewindMs = if (resumeWithRewind && pair.track.positionMs > 0L) {
                effectiveSmartRewind * 1000L
            } else {
                0L
            }
            val startPos = if (autoPlay) (pair.track.positionMs - rewindMs).coerceAtLeast(0L) else 0L
            val effectiveMono = AudioMonoMode.resolveEffective(
                globalMonoMode = globalSettings.monoMode,
                bookMonoOverride = pair.audiobook.monoModeOverride,
                trackMonoOverride = pair.track.monoModeOverride
            )
            val effectiveNorm = AudioOverride.resolve(
                globalEnabled = globalSettings.audioNormalizationEnabled,
                bookOverride = pair.audiobook.audioNormalizationOverride
            )
            lastPausedElapsedMs = 0L
            player.setMediaItem(item, startPos)
            player.playbackParameters = PlaybackParameters(effectiveSpeed)
            updateSilenceProcessors(effectiveSkipSilence, effectiveIntensity)
            monoProcessor.setMode(effectiveMono)
            currentEffectiveNormalization = effectiveNorm
            applyAudioNormalization(effectiveNorm)
            player.prepare()
            if (autoPlay) {
                player.play()
            } else {
                player.pause()
            }
            lastTrackPrefs().edit().putLong(KEY_LAST_TRACK_ID, pair.track.id).apply()
            withContext(Dispatchers.IO) {
                dao.updateLastTrack(pair.audiobook.id, pair.track.id, System.currentTimeMillis())
            }
        }
    }

    private fun prepareTrackPaused(trackId: Long) {
        playTrackDirect(trackId = trackId, resumeWithRewind = false, autoPlay = false)
    }

    private fun buildMediaItem(track: ChapterTrackEntity, book: AudiobookEntity): MediaItem? {
        val uri = if (track.fileUri.startsWith("content://")) {
            Uri.parse(track.fileUri)
        } else {
            val f = File(track.fileUri)
            if (f.exists()) Uri.fromFile(f) else return null
        }
        val artworkUri = book.artworkUrl
            .takeIf { it.startsWith("content://") || it.startsWith("file://") }
            ?.let { Uri.parse(it) }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(book.author.ifBlank { book.title })
            .setAlbumTitle(book.title)
            .setArtworkUri(artworkUri)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun lastTrackPrefs() = getSharedPreferences("playback_state", MODE_PRIVATE)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null ||
            !currentPlayer.playWhenReady ||
            currentPlayer.mediaItemCount == 0 ||
            currentPlayer.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun startPositionSaver() {
        positionSaveJob?.cancel()
        positionSaveJob = serviceScope.launch {
            while (isActive) {
                delay(5_000L)
                saveCurrentPosition()
            }
        }
    }

    private fun saveCurrentPosition() {
        val state = player.playbackState
        if (state != Player.STATE_READY && state != Player.STATE_BUFFERING) return
        val trackId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        serviceScope.launch(Dispatchers.IO) {
            PureAudiobooksDatabase.getInstance(applicationContext).dao().updateTrackPosition(trackId, pos)
        }
    }

    override fun onDestroy() {
        positionSaveJob?.cancel()
        skipTrackJob?.cancel()
        resolveAudioJob?.cancel()
        val state = player.playbackState
        val trackId = if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
            player.currentMediaItem?.mediaId?.toLongOrNull()
        } else {
            null
        }
        val pos = player.currentPosition.coerceAtLeast(0L)

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        releaseDynamicsProcessing()

        if (trackId != null) {
            runCatching {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(1_500L) {
                        PureAudiobooksDatabase.getInstance(applicationContext).dao()
                            .updateTrackPosition(trackId, pos)
                    }
                }
            }.onFailure {
                Log.w(TAG, "Failed to save final position in onDestroy for track $trackId", it)
            }
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
        private val TRUSTED_MEDIA_CONTROLLERS = setOf(
            "com.android.systemui",
            "com.android.bluetooth",
            "com.google.android.bluetooth",
            "com.google.android.projection.gearhead",
            "com.google.android.wearable.app"
        )
        private const val KEY_LAST_TRACK_ID = "last_track_id"
        const val CMD_SET_SKIP_SILENCE = "com.lycoslane.pureaudiobooks.SET_SKIP_SILENCE"
        const val ARG_SKIP_SILENCE = "skip_silence"
        const val ARG_SILENCE_INTENSITY = "silence_intensity"
        const val CMD_SET_MONO_MODE = "com.lycoslane.pureaudiobooks.SET_MONO_MODE"
        const val ARG_MONO_MODE = "mono_mode"
        const val CMD_SET_AUDIO_NORMALIZATION = "com.lycoslane.pureaudiobooks.SET_AUDIO_NORMALIZATION"
        const val ARG_AUDIO_NORMALIZATION = "audio_normalization"
        const val CMD_SKIP_BOOK_TRACK_NEXT = "com.lycoslane.pureaudiobooks.SKIP_BOOK_TRACK_NEXT"
        const val CMD_SKIP_BOOK_TRACK_PREV = "com.lycoslane.pureaudiobooks.SKIP_BOOK_TRACK_PREV"
        const val CMD_SET_SLEEP_END_OF_TRACK = "com.lycoslane.pureaudiobooks.SET_SLEEP_END_OF_TRACK"
        const val ARG_SLEEP_END_OF_TRACK = "sleep_end_of_track"
        const val CMD_SET_SMART_REWIND = "com.lycoslane.pureaudiobooks.SET_SMART_REWIND"
        const val ARG_SMART_REWIND_SECONDS = "smart_rewind_seconds"
    }
}

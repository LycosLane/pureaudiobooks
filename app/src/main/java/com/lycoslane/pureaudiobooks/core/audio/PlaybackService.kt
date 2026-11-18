package com.lycoslane.pureaudiobooks.core.audio

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

class PlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var positionSaveJob: Job? = null
    private var skipTrackJob: Job? = null
    private var lastPausedElapsedMs: Long = 0L

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var attachedAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    @Volatile
    private var sleepAtEndOfTrack: Boolean = false

    @OptIn(UnstableApi::class)
    private lateinit var silenceProcessor: DelegatingSilenceAudioProcessor

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
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
                    .setAudioProcessors(arrayOf(silenceProcessor))
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
        serviceScope.launch {
            app.preferences.settings
                .map { it.audioNormalizationEnabled }
                .distinctUntilChanged()
                .collect { enabled -> applyAudioNormalization(enabled) }
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
                    val rewindSecs = app.preferences.settings.value.smartRewindSeconds
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
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                currentAudioSessionId = audioSessionId
                applyAudioNormalization(app.preferences.settings.value.audioNormalizationEnabled)
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
                        } else if (nextTrack == null) {
                            // Whole audiobook finished!
                            dao.setAudiobookFinished(track.audiobookId, true, now)
                            val autoTrashDays = app.preferences.settings.value.autoMoveFinishedToTrashDays
                            if (autoTrashDays == 0) {
                                dao.setDeletedAt(track.audiobookId, now)
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
                            .add(SessionCommand(CMD_SET_AUDIO_NORMALIZATION, Bundle.EMPTY))
                            .add(SessionCommand(CMD_SKIP_BOOK_TRACK_NEXT, Bundle.EMPTY))
                            .add(SessionCommand(CMD_SKIP_BOOK_TRACK_PREV, Bundle.EMPTY))
                            .add(SessionCommand(CMD_SET_SLEEP_END_OF_TRACK, Bundle.EMPTY))
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
                            val item = withBook?.let { buildMediaItem(it.track, it.audiobook) }
                            if (item == null || withBook == null) {
                                future.setException(UnsupportedOperationException("Nothing to resume"))
                            } else {
                                withContext(Dispatchers.Main) {
                                    player.playbackParameters = PlaybackParameters(
                                        withBook.audiobook.playbackSpeed.coerceIn(0.75f, 3.0f)
                                    )
                                    updateSilenceProcessors(
                                        withBook.audiobook.skipSilence,
                                        withBook.audiobook.silenceIntensity
                                    )
                                }
                                future.set(
                                    MediaSession.MediaItemsWithStartPosition(
                                        listOf(item), 0, withBook.track.positionMs.coerceAtLeast(0L)
                                    )
                                )
                            }
                        } catch (e: Exception) {
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
                            CMD_SET_AUDIO_NORMALIZATION -> {
                                val enabled = args.getBoolean(ARG_AUDIO_NORMALIZATION, false)
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

    private fun releaseDynamicsProcessing() {
        val dp = dynamicsProcessing ?: return
        try {
            runCatching { dp.enabled = false }
        } finally {
            runCatching { dp.release() }
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

    private fun playTrackDirect(trackId: Long, resumeWithRewind: Boolean) {
        skipTrackJob?.cancel()
        skipTrackJob = serviceScope.launch {
            val dao = PureAudiobooksDatabase.getInstance(applicationContext).dao()
            val pair = withContext(Dispatchers.IO) { dao.getTrackWithAudiobook(trackId) } ?: return@launch
            val item = buildMediaItem(pair.track, pair.audiobook) ?: return@launch
            val rewindMs = if (resumeWithRewind && pair.track.positionMs > 0L) {
                (applicationContext as PureAudiobooksApplication)
                    .preferences.settings.value.smartRewindSeconds * 1000L
            } else {
                0L
            }
            val startPos = (pair.track.positionMs - rewindMs).coerceAtLeast(0L)
            lastPausedElapsedMs = 0L
            player.setMediaItem(item, startPos)
            player.playbackParameters = PlaybackParameters(pair.audiobook.playbackSpeed.coerceIn(0.75f, 3.0f))
            updateSilenceProcessors(pair.audiobook.skipSilence, pair.audiobook.silenceIntensity.coerceIn(0, 2))
            player.prepare()
            player.play()
            withContext(Dispatchers.IO) {
                dao.updateLastPlayed(pair.audiobook.id, System.currentTimeMillis())
            }
        }
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
        if (player.playbackState == Player.STATE_ENDED) return
        val trackId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        serviceScope.launch(Dispatchers.IO) {
            PureAudiobooksDatabase.getInstance(applicationContext).dao().updateTrackPosition(trackId, pos)
        }
    }

    override fun onDestroy() {
        positionSaveJob?.cancel()
        skipTrackJob?.cancel()
        val trackId = if (player.playbackState != Player.STATE_ENDED) {
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
            }
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
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
        const val CMD_SET_AUDIO_NORMALIZATION = "com.lycoslane.pureaudiobooks.SET_AUDIO_NORMALIZATION"
        const val ARG_AUDIO_NORMALIZATION = "audio_normalization"
        const val CMD_SKIP_BOOK_TRACK_NEXT = "com.lycoslane.pureaudiobooks.SKIP_BOOK_TRACK_NEXT"
        const val CMD_SKIP_BOOK_TRACK_PREV = "com.lycoslane.pureaudiobooks.SKIP_BOOK_TRACK_PREV"
        const val CMD_SET_SLEEP_END_OF_TRACK = "com.lycoslane.pureaudiobooks.SET_SLEEP_END_OF_TRACK"
        const val ARG_SLEEP_END_OF_TRACK = "sleep_end_of_track"
    }
}

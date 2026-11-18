package com.lycoslane.pureaudiobooks.domain.repository

import android.content.Context
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.core.database.BookmarkEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDao
import com.lycoslane.pureaudiobooks.core.local.LocalAudiobookScanner
import com.lycoslane.pureaudiobooks.core.local.SidecarInfo
import com.lycoslane.pureaudiobooks.core.local.SidecarMetadata
import com.lycoslane.pureaudiobooks.core.network.OnlineMetadataClient
import com.lycoslane.pureaudiobooks.core.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class AudiobookRepository(
    private val context: Context,
    private val dao: PureAudiobooksDao,
    private val preferences: AppPreferences
) {
    fun observeActiveAudiobooks(): Flow<List<AudiobookWithTracks>> = dao.observeActiveAudiobooks()

    fun observeTrashedAudiobooks(): Flow<List<AudiobookWithTracks>> = dao.observeTrashedAudiobooks()

    fun observeAudiobook(audiobookId: Long): Flow<AudiobookWithTracks?> =
        dao.observeAudiobookWithTracks(audiobookId)

    fun observeBookmarks(audiobookId: Long): Flow<List<BookmarkEntity>> =
        dao.observeBookmarks(audiobookId)

    suspend fun syncLocalLibrary(): Int = withContext(Dispatchers.IO) {
        val uri = preferences.settings.value.localFolderUri
        val count = LocalAudiobookScanner.syncLocalFolder(context, dao, uri)
        runTrashMaintenance()
        count
    }

    suspend fun runTrashMaintenance() = withContext(Dispatchers.IO) {
        val settings = preferences.settings.value
        val now = System.currentTimeMillis()

        // 1. Auto-move finished books to trash if configured
        val autoDays = settings.autoMoveFinishedToTrashDays
        if (autoDays >= 0) {
            val cutoff = now - autoDays * 24L * 60L * 60L * 1000L
            val candidates = dao.getFinishedAudiobooksOlderThan(cutoff)
            for (book in candidates) {
                dao.setDeletedAt(book.id, now)
            }
        }

        // 2. Permanently delete expired trash items
        val retentionHours = settings.trashRetentionHours
        val expireCutoff = now - retentionHours * 60L * 60L * 1000L
        val expired = dao.getExpiredTrashedAudiobooks(expireCutoff)
        for (book in expired) {
            permanentlyDeleteAudiobookInternal(book.id)
        }
    }

    suspend fun resolveBookMetadata(
        audiobookId: Long,
        title: String,
        author: String,
        description: String,
        onlineCoverUrl: String? = null,
        writeSidecarFiles: Boolean = true
    ): Unit = withContext(Dispatchers.IO) {
        val book = dao.getAudiobook(audiobookId) ?: return@withContext
        val cleanTitle = title.trim().ifBlank { book.folderName }
        val cleanAuthor = author.trim()
        val cleanDesc = description.trim()

        var nextArtworkUrl = book.artworkUrl
        var coverBytes: ByteArray? = null
        if (!onlineCoverUrl.isNullOrBlank()) {
            coverBytes = OnlineMetadataClient.downloadCoverBytes(onlineCoverUrl)
            if (coverBytes != null && coverBytes.isNotEmpty()) {
                val coversDir = File(context.filesDir, "local_covers").apply { mkdirs() }
                val hash = sha256(book.folderKey)
                val localFile = File(coversDir, "$hash.jpg")
                localFile.writeBytes(coverBytes)
                nextArtworkUrl = localFile.absolutePath
            }
        }

        if (writeSidecarFiles && book.folderUri.isNotBlank() && book.folderDocId.isNotBlank()) {
            val writtenCoverUri = SidecarMetadata.writeSidecarToFolder(
                context = context,
                treeUriString = book.folderUri,
                folderDocId = book.folderDocId,
                info = SidecarInfo(cleanTitle, cleanAuthor, cleanDesc),
                coverBytes = coverBytes
            )
            if (!writtenCoverUri.isNullOrBlank()) {
                nextArtworkUrl = writtenCoverUri
            }
        }

        dao.updateAudiobookMetadata(
            audiobookId = book.id,
            title = cleanTitle,
            author = cleanAuthor,
            description = cleanDesc,
            artworkUrl = nextArtworkUrl,
            hasTagConflict = false,
            conflictSummaryJson = book.conflictSummaryJson,
            metadataLockedByUser = true,
            lastScannedMs = System.currentTimeMillis()
        )
    }

    suspend fun splitAudiobookByAlbumTag(audiobookId: Long): Int = withContext(Dispatchers.IO) {
        val book = dao.getAudiobook(audiobookId) ?: return@withContext 0
        val tracks = dao.getTracksForAudiobook(audiobookId)
        if (tracks.isEmpty()) return@withContext 0

        val groups = tracks.groupBy { it.albumTag.trim().ifBlank { book.folderName } }
        if (groups.size <= 1) return@withContext 0

        val now = System.currentTimeMillis()
        var created = 0
        for ((albumName, groupTracks) in groups) {
            val folderKey = "local://${book.folderDocId}::$albumName"
            val existing = dao.getAudiobookByFolderKey(folderKey)
            val targetId = existing?.id ?: dao.insertAudiobook(
                AudiobookEntity(
                    folderKey = folderKey,
                    folderDocId = book.folderDocId,
                    folderUri = book.folderUri,
                    folderName = book.folderName,
                    parentFolderName = book.parentFolderName,
                    title = albumName,
                    author = groupTracks.firstOrNull { it.artistTag.isNotBlank() }?.artistTag ?: book.author,
                    description = book.description,
                    artworkUrl = book.artworkUrl,
                    playbackSpeed = book.playbackSpeed,
                    skipSilence = book.skipSilence,
                    silenceIntensity = book.silenceIntensity,
                    hasTagConflict = false,
                    conflictSummaryJson = "",
                    metadataLockedByUser = true,
                    isSplitByAlbum = true,
                    lastScannedMs = now
                )
            )
            if (targetId > 0L) {
                val cloned = groupTracks.mapIndexed { idx, t ->
                    t.copy(id = 0L, audiobookId = targetId, sortOrder = idx)
                }
                dao.insertTracks(cloned)
                created++
            }
        }
        dao.deleteAudiobook(audiobookId)
        created
    }

    suspend fun moveToTrash(audiobookId: Long) = withContext(Dispatchers.IO) {
        dao.setDeletedAt(audiobookId, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(audiobookId: Long) = withContext(Dispatchers.IO) {
        dao.setDeletedAt(audiobookId, null)
    }

    suspend fun permanentlyDeleteAudiobook(audiobookId: Long) = withContext(Dispatchers.IO) {
        permanentlyDeleteAudiobookInternal(audiobookId)
    }

    private suspend fun permanentlyDeleteAudiobookInternal(audiobookId: Long) {
        val book = dao.getAudiobook(audiobookId) ?: return
        val tracks = dao.getTracksForAudiobook(audiobookId)
        val hasWrite = LocalAudiobookScanner.hasWritePermission(context, book.folderUri)
        if (hasWrite) {
            tracks.forEach { LocalAudiobookScanner.deleteLocalDocument(context, it.fileUri) }
        }
        dao.deleteAudiobook(audiobookId)
    }

    suspend fun setTrackFinished(trackId: Long, finished: Boolean) = withContext(Dispatchers.IO) {
        val track = dao.getTrack(trackId) ?: return@withContext
        dao.setTrackFinished(trackId, finished)
        if (finished) dao.updateTrackPosition(trackId, 0L)
        val all = dao.getTracksForAudiobook(track.audiobookId)
        val allDone = all.isNotEmpty() && all.all { if (it.id == trackId) finished else it.isFinished }
        dao.setAudiobookFinished(
            audiobookId = track.audiobookId,
            finished = allDone,
            finishedAtMs = if (allDone) System.currentTimeMillis() else 0L
        )
    }

    suspend fun markBookFinished(audiobookId: Long, finished: Boolean) = withContext(Dispatchers.IO) {
        dao.resetAllTracksForBook(audiobookId, finished = finished, positionMs = 0L)
        dao.setAudiobookFinished(
            audiobookId = audiobookId,
            finished = finished,
            finishedAtMs = if (finished) System.currentTimeMillis() else 0L
        )
        if (!finished) {
            dao.updateLastTrack(audiobookId, 0L, 0L)
        }
    }

    suspend fun resetBookProgress(audiobookId: Long) = withContext(Dispatchers.IO) {
        dao.resetAllTracksForBook(audiobookId, finished = false, positionMs = 0L)
        dao.setAudiobookFinished(audiobookId, finished = false, finishedAtMs = 0L)
        dao.updateLastTrack(audiobookId, 0L, 0L)
    }

    suspend fun setBookHidden(audiobookId: Long, hidden: Boolean) = withContext(Dispatchers.IO) {
        dao.setHidden(audiobookId, hidden)
    }

    suspend fun addBookmark(
        audiobookId: Long,
        track: ChapterTrackEntity,
        positionMs: Long,
        note: String
    ) = withContext(Dispatchers.IO) {
        dao.insertBookmark(
            BookmarkEntity(
                audiobookId = audiobookId,
                trackId = track.id,
                trackTitle = track.title,
                positionMs = positionMs.coerceAtLeast(0L),
                note = note.trim(),
                createdAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteBookmark(bookmarkId: Long) = withContext(Dispatchers.IO) {
        dao.deleteBookmark(bookmarkId)
    }

    suspend fun updateBookSpeed(audiobookId: Long, speed: Float) = withContext(Dispatchers.IO) {
        dao.updatePlaybackSpeed(audiobookId, speed.coerceIn(0.75f, 3.0f))
    }

    suspend fun updateBookSilence(audiobookId: Long, enabled: Boolean, intensity: Int) =
        withContext(Dispatchers.IO) {
            dao.updateSkipSilence(audiobookId, enabled, intensity.coerceIn(0, 2))
        }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val books = dao.getAllAudiobooksOnce()
        val bookmarks = dao.getAllBookmarksOnce()
        val s = preferences.settings.value

        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAtMs", System.currentTimeMillis())

        root.put("settings", JSONObject().apply {
            put("onlineLookupEnabled", s.onlineLookupEnabled)
            put("trashRetentionHours", s.trashRetentionHours)
            put("autoMoveFinishedToTrashDays", s.autoMoveFinishedToTrashDays)
            put("smartRewindSeconds", s.smartRewindSeconds)
            put("audioNormalizationEnabled", s.audioNormalizationEnabled)
            put("bluetoothNextPrevSeeks", s.bluetoothNextPrevSeeks)
            put("appLanguage", s.appLanguage)
            put("gridLayout", s.gridLayout)
        })

        val booksArr = JSONArray()
        for (book in books) {
            val tracks = dao.getTracksForAudiobook(book.id)
            val tracksArr = JSONArray()
            for (t in tracks) {
                tracksArr.put(JSONObject().apply {
                    put("guid", t.guid)
                    put("positionMs", t.positionMs)
                    put("isFinished", t.isFinished)
                })
            }
            booksArr.put(JSONObject().apply {
                put("folderKey", book.folderKey)
                put("title", book.title)
                put("author", book.author)
                put("description", book.description)
                put("playbackSpeed", book.playbackSpeed.toDouble())
                put("skipSilence", book.skipSilence)
                put("silenceIntensity", book.silenceIntensity)
                put("metadataLockedByUser", book.metadataLockedByUser)
                put("isFinished", book.isFinished)
                put("lastPlayedAtMs", book.lastPlayedAtMs)
                put("tracks", tracksArr)
            })
        }
        root.put("books", booksArr)

        val bmArr = JSONArray()
        val bookKeyById = books.associate { it.id to it.folderKey }
        for (bm in bookmarks) {
            val fk = bookKeyById[bm.audiobookId] ?: continue
            bmArr.put(JSONObject().apply {
                put("folderKey", fk)
                put("trackTitle", bm.trackTitle)
                put("positionMs", bm.positionMs)
                put("note", bm.note)
                put("createdAtMs", bm.createdAtMs)
            })
        }
        root.put("bookmarks", bmArr)
        root.toString(2)
    }

    suspend fun importBackupJson(rawJson: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(rawJson)
        root.optJSONObject("settings")?.let { s ->
            if (s.has("onlineLookupEnabled")) preferences.setOnlineLookupEnabled(s.optBoolean("onlineLookupEnabled"))
            if (s.has("trashRetentionHours")) preferences.setTrashRetentionHours(s.optInt("trashRetentionHours", 48))
            if (s.has("autoMoveFinishedToTrashDays")) preferences.setAutoMoveFinishedToTrashDays(s.optInt("autoMoveFinishedToTrashDays", -1))
            if (s.has("smartRewindSeconds")) preferences.setSmartRewindSeconds(s.optInt("smartRewindSeconds", 10))
            if (s.has("audioNormalizationEnabled")) preferences.setAudioNormalizationEnabled(s.optBoolean("audioNormalizationEnabled"))
            if (s.has("bluetoothNextPrevSeeks")) preferences.setBluetoothNextPrevSeeks(s.optBoolean("bluetoothNextPrevSeeks"))
            if (s.has("appLanguage")) preferences.setAppLanguage(s.optString("appLanguage", "en"))
            if (s.has("gridLayout")) preferences.setGridLayout(s.optBoolean("gridLayout", true))
        }

        var restored = 0
        val booksArr = root.optJSONArray("books") ?: JSONArray()
        for (i in 0 until booksArr.length()) {
            val obj = booksArr.optJSONObject(i) ?: continue
            val folderKey = obj.optString("folderKey")
            val existing = dao.getAudiobookByFolderKey(folderKey) ?: continue
            dao.updatePlaybackSpeed(existing.id, obj.optDouble("playbackSpeed", 1.0).toFloat())
            dao.updateSkipSilence(existing.id, obj.optBoolean("skipSilence"), obj.optInt("silenceIntensity", 1))
            if (obj.optBoolean("metadataLockedByUser", false)) {
                dao.updateAudiobookMetadata(
                    audiobookId = existing.id,
                    title = obj.optString("title", existing.title),
                    author = obj.optString("author", existing.author),
                    description = obj.optString("description", existing.description),
                    artworkUrl = existing.artworkUrl,
                    hasTagConflict = false,
                    conflictSummaryJson = existing.conflictSummaryJson,
                    metadataLockedByUser = true,
                    lastScannedMs = System.currentTimeMillis()
                )
            }
            val tracksByGuid = dao.getTracksForAudiobook(existing.id).associateBy { it.guid }
            val tracksArr = obj.optJSONArray("tracks") ?: JSONArray()
            for (j in 0 until tracksArr.length()) {
                val tObj = tracksArr.optJSONObject(j) ?: continue
                val guid = tObj.optString("guid")
                val target = tracksByGuid[guid] ?: continue
                dao.updateTrackPosition(target.id, tObj.optLong("positionMs", 0L))
                dao.setTrackFinished(target.id, tObj.optBoolean("isFinished", false))
            }
            restored++
        }
        restored
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

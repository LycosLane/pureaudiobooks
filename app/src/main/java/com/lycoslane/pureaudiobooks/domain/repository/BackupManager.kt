package com.lycoslane.pureaudiobooks.domain.repository

import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.core.audio.AudioOverride
import com.lycoslane.pureaudiobooks.core.audio.SilenceOverride
import com.lycoslane.pureaudiobooks.core.audio.SmartRewindOverride
import com.lycoslane.pureaudiobooks.core.audio.SpeedOverride
import com.lycoslane.pureaudiobooks.core.database.BookmarkEntity
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDao
import com.lycoslane.pureaudiobooks.core.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class BackupManager(
    private val dao: PureAudiobooksDao,
    private val preferences: AppPreferences
) {
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val books = dao.getAllAudiobooksOnce()
        val bookmarks = dao.getAllBookmarksOnce()
        val s = preferences.settings.value

        val root = JSONObject()
        root.put("version", CURRENT_BACKUP_VERSION)
        root.put("exportedAtMs", System.currentTimeMillis())

        root.put(
            "settings",
            JSONObject().apply {
                put("onlineLookupEnabled", s.onlineLookupEnabled)
                put("trashRetentionHours", s.trashRetentionHours)
                put("autoMoveFinishedToTrashDays", s.autoMoveFinishedToTrashDays)
                put("smartRewindSeconds", s.smartRewindSeconds)
                put("defaultPlaybackSpeed", s.defaultPlaybackSpeed.toDouble())
                put("defaultSkipSilence", s.defaultSkipSilence)
                put("defaultSilenceIntensity", s.defaultSilenceIntensity)
                put("audioNormalizationEnabled", s.audioNormalizationEnabled)
                put("monoMode", s.monoMode)
                put("bluetoothNextPrevSeeks", s.bluetoothNextPrevSeeks)
                put("textScale", s.textScale.toDouble())
                put("appLanguage", s.appLanguage)
                put("gridLayout", s.gridLayout)
                put("themeMode", s.themeMode)
                put("lightAccent", s.lightAccent)
                put("darkAccent", s.darkAccent)
                put("showRefreshButton", s.showRefreshButton)
                put("showSearchBar", s.showSearchBar)
            }
        )

        val trackGuidById = HashMap<Long, String>()
        val booksArr = JSONArray()
        for (book in books) {
            val tracks = dao.getTracksForAudiobook(book.id)
            val tracksArr = JSONArray()
            for (t in tracks) {
                trackGuidById[t.id] = t.guid
                tracksArr.put(
                    JSONObject().apply {
                        put("guid", t.guid)
                        put("positionMs", t.positionMs)
                        put("isFinished", t.isFinished)
                        if (t.monoModeOverride != AudioMonoMode.INHERIT) {
                            put("monoModeOverride", t.monoModeOverride)
                        }
                    }
                )
            }
            val lastTrackGuid = if (book.lastTrackId > 0L) trackGuidById[book.lastTrackId].orEmpty() else ""
            booksArr.put(
                JSONObject().apply {
                    put("folderKey", book.folderKey)
                    put("title", book.title)
                    put("author", book.author)
                    put("description", book.description)
                    put("playbackSpeed", book.playbackSpeed.toDouble())
                    put("speedOverride", book.speedOverride.toDouble())
                    put("skipSilence", book.skipSilence)
                    put("silenceIntensity", book.silenceIntensity)
                    put("silenceModeOverride", book.silenceModeOverride)
                    put("monoModeOverride", book.monoModeOverride)
                    put("audioNormalizationOverride", book.audioNormalizationOverride)
                    put("smartRewindOverride", book.smartRewindOverride)
                    put("metadataLockedByUser", book.metadataLockedByUser)
                    put("isFinished", book.isFinished)
                    put("finishedAtMs", book.finishedAtMs)
                    put("isHidden", book.isHidden)
                    put("lastPlayedAtMs", book.lastPlayedAtMs)
                    if (lastTrackGuid.isNotEmpty()) {
                        put("lastTrackGuid", lastTrackGuid)
                    }
                    put("tracks", tracksArr)
                }
            )
        }
        root.put("books", booksArr)

        val bmArr = JSONArray()
        val bookKeyById = books.associate { it.id to it.folderKey }
        for (bm in bookmarks) {
            val fk = bookKeyById[bm.audiobookId] ?: continue
            bmArr.put(
                JSONObject().apply {
                    put("folderKey", fk)
                    val trackGuid = trackGuidById[bm.trackId].orEmpty()
                    if (trackGuid.isNotEmpty()) put("trackGuid", trackGuid)
                    put("trackTitle", bm.trackTitle)
                    put("positionMs", bm.positionMs)
                    put("note", bm.note)
                    put("createdAtMs", bm.createdAtMs)
                }
            )
        }
        root.put("bookmarks", bmArr)
        root.toString(2)
    }

    suspend fun importBackupJson(rawJson: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(rawJson)
        val version = root.optInt("version", 1)
        if (version < 1 || version > CURRENT_BACKUP_VERSION) {
            error("Unsupported backup version: $version")
        }

        root.optJSONObject("settings")?.let { importSettings(it) }

        var restored = 0
        val bookIdByFolderKey = HashMap<String, Long>()
        val booksArr = root.optJSONArray("books") ?: JSONArray()
        for (i in 0 until minOf(booksArr.length(), 1000)) {
            val obj = booksArr.optJSONObject(i) ?: continue
            val folderKey = obj.optString("folderKey")
            if (folderKey.isBlank()) continue
            val existing = dao.getAudiobookByFolderKey(folderKey) ?: continue
            bookIdByFolderKey[folderKey] = existing.id

            val rawSpeed = obj.optDouble("playbackSpeed", existing.playbackSpeed.toDouble())
            val safeSpeed = if (rawSpeed.isFinite()) rawSpeed.toFloat().coerceIn(0.50f, 3.00f) else existing.playbackSpeed
            val importedSpeedOverride = if (obj.has("speedOverride")) {
                val ov = obj.optDouble("speedOverride", SpeedOverride.INHERIT.toDouble())
                if (ov.isFinite()) SpeedOverride.normalize(ov.toFloat()) else SpeedOverride.INHERIT
            } else if (kotlin.math.abs(safeSpeed - 1.0f) >= 0.01f) {
                safeSpeed
            } else {
                SpeedOverride.INHERIT
            }
            dao.setBookSpeedOverride(existing.id, importedSpeedOverride, safeSpeed)

            val skipSilence = obj.optBoolean("skipSilence", existing.skipSilence)
            val silenceIntensity = obj.optInt("silenceIntensity", existing.silenceIntensity).coerceIn(0, 2)
            val importedSilenceOverride = if (obj.has("silenceModeOverride")) {
                SilenceOverride.normalize(obj.optInt("silenceModeOverride", SilenceOverride.INHERIT))
            } else if (skipSilence) {
                SilenceOverride.fromEnabledAndIntensity(true, silenceIntensity)
            } else {
                SilenceOverride.INHERIT
            }
            dao.setBookSilenceModeOverride(existing.id, importedSilenceOverride, skipSilence, silenceIntensity)

            if (obj.has("smartRewindOverride")) {
                dao.setBookSmartRewindOverride(
                    existing.id,
                    SmartRewindOverride.normalize(obj.optInt("smartRewindOverride", SmartRewindOverride.INHERIT))
                )
            }
            if (obj.has("monoModeOverride")) {
                dao.setBookMonoModeOverride(
                    existing.id,
                    AudioMonoMode.normalizeOverride(obj.optInt("monoModeOverride", existing.monoModeOverride))
                )
            }
            if (obj.has("audioNormalizationOverride")) {
                dao.setBookAudioNormalizationOverride(
                    existing.id,
                    AudioOverride.normalize(obj.optInt("audioNormalizationOverride", existing.audioNormalizationOverride))
                )
            }
            if (obj.has("isHidden")) {
                dao.setHidden(existing.id, obj.optBoolean("isHidden", existing.isHidden))
            }
            if (obj.has("isFinished")) {
                val fin = obj.optBoolean("isFinished", existing.isFinished)
                val finAt = if (fin) {
                    obj.optLong("finishedAtMs", 0L).takeIf { it > 0L }
                        ?: existing.finishedAtMs.takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                } else 0L
                dao.setAudiobookFinished(existing.id, fin, finAt)
            }
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

            val tracks = dao.getTracksForAudiobook(existing.id)
            val tracksByGuid = tracks.associateBy { it.guid }
            val tracksArr = obj.optJSONArray("tracks") ?: JSONArray()
            for (j in 0 until minOf(tracksArr.length(), 5000)) {
                val tObj = tracksArr.optJSONObject(j) ?: continue
                val guid = tObj.optString("guid")
                val target = tracksByGuid[guid] ?: continue
                dao.updateTrackPosition(target.id, tObj.optLong("positionMs", target.positionMs).coerceAtLeast(0L))
                dao.setTrackFinished(target.id, tObj.optBoolean("isFinished", target.isFinished))
                if (tObj.has("monoModeOverride")) {
                    dao.setTrackMonoModeOverride(
                        target.id,
                        AudioMonoMode.normalizeOverride(tObj.optInt("monoModeOverride", target.monoModeOverride))
                    )
                }
            }

            val lastTrackGuid = obj.optString("lastTrackGuid", "")
            val matchedLastTrack = if (lastTrackGuid.isNotEmpty()) tracksByGuid[lastTrackGuid] else null
            val lastPlayedAt = obj.optLong("lastPlayedAtMs", existing.lastPlayedAtMs).coerceAtLeast(0L)
            if (matchedLastTrack != null) {
                dao.updateLastTrack(existing.id, matchedLastTrack.id, lastPlayedAt)
            } else if (lastPlayedAt > 0L) {
                dao.updateLastPlayed(existing.id, lastPlayedAt)
            }

            restored++
        }

        val bmArr = root.optJSONArray("bookmarks")
        if (bmArr != null && bmArr.length() > 0) {
            val existingBookmarks = dao.getAllBookmarksOnce()
            val tracksByBook = HashMap<Long, List<com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity>>()
            for (k in 0 until minOf(bmArr.length(), 5000)) {
                val bObj = bmArr.optJSONObject(k) ?: continue
                val fk = bObj.optString("folderKey")
                val bookId = bookIdByFolderKey[fk] ?: dao.getAudiobookByFolderKey(fk)?.id ?: continue
                val bookTracks = tracksByBook.getOrPut(bookId) { dao.getTracksForAudiobook(bookId) }
                val trackGuid = bObj.optString("trackGuid", "")
                val trackTitle = bObj.optString("trackTitle", "")
                val matchedTrack = (if (trackGuid.isNotEmpty()) bookTracks.firstOrNull { it.guid == trackGuid } else null)
                    ?: bookTracks.firstOrNull { it.title == trackTitle }
                    ?: continue
                val posMs = bObj.optLong("positionMs", 0L).coerceAtLeast(0L)
                val note = bObj.optString("note", "").trim()
                val alreadyExists = existingBookmarks.any {
                    it.audiobookId == bookId &&
                        it.trackId == matchedTrack.id &&
                        it.positionMs == posMs &&
                        it.note == note
                }
                if (!alreadyExists) {
                    dao.insertBookmark(
                        BookmarkEntity(
                            audiobookId = bookId,
                            trackId = matchedTrack.id,
                            trackTitle = trackTitle.ifBlank { matchedTrack.title },
                            positionMs = posMs,
                            note = note,
                            createdAtMs = bObj.optLong("createdAtMs", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
        restored
    }

    private fun importSettings(s: JSONObject) {
        if (s.has("onlineLookupEnabled")) preferences.setOnlineLookupEnabled(s.optBoolean("onlineLookupEnabled"))
        if (s.has("trashRetentionHours")) preferences.setTrashRetentionHours(s.optInt("trashRetentionHours", 48))
        if (s.has("autoMoveFinishedToTrashDays")) preferences.setAutoMoveFinishedToTrashDays(s.optInt("autoMoveFinishedToTrashDays", -1))
        if (s.has("smartRewindSeconds")) preferences.setSmartRewindSeconds(s.optInt("smartRewindSeconds", 10))
        if (s.has("defaultPlaybackSpeed")) {
            val sp = s.optDouble("defaultPlaybackSpeed", 1.0)
            if (sp.isFinite()) preferences.setDefaultPlaybackSpeed(sp.toFloat())
        }
        if (s.has("defaultSkipSilence") || s.has("defaultSilenceIntensity")) {
            preferences.setDefaultSilenceMode(
                enabled = s.optBoolean("defaultSkipSilence", false),
                intensity = s.optInt("defaultSilenceIntensity", 1)
            )
        }
        if (s.has("audioNormalizationEnabled")) preferences.setAudioNormalizationEnabled(s.optBoolean("audioNormalizationEnabled"))
        if (s.has("monoMode")) preferences.setMonoMode(s.optInt("monoMode", AudioMonoMode.OFF))
        if (s.has("bluetoothNextPrevSeeks")) preferences.setBluetoothNextPrevSeeks(s.optBoolean("bluetoothNextPrevSeeks"))
        if (s.has("textScale")) {
            val scale = s.optDouble("textScale", 1.0)
            if (scale.isFinite()) preferences.setTextScale(scale.toFloat())
        }
        if (s.has("appLanguage")) preferences.setAppLanguage(s.optString("appLanguage", "en"))
        if (s.has("gridLayout")) preferences.setGridLayout(s.optBoolean("gridLayout", true))
        if (s.has("themeMode")) preferences.setThemeMode(s.optString("themeMode", AppPreferences.THEME_SYSTEM))
        if (s.has("lightAccent")) preferences.setLightAccent(s.optString("lightAccent", AppPreferences.DEFAULT_ACCENT))
        if (s.has("darkAccent")) preferences.setDarkAccent(s.optString("darkAccent", AppPreferences.DEFAULT_ACCENT))
        if (s.has("showRefreshButton")) preferences.setShowRefreshButton(s.optBoolean("showRefreshButton", true))
        if (s.has("showSearchBar")) preferences.setShowSearchBar(s.optBoolean("showSearchBar", true))
    }

    companion object {
        const val CURRENT_BACKUP_VERSION = 2
    }
}

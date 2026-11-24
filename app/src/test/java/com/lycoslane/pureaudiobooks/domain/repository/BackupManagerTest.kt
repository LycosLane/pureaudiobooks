package com.lycoslane.pureaudiobooks.domain.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.core.audio.AudioOverride
import com.lycoslane.pureaudiobooks.core.audio.SilenceOverride
import com.lycoslane.pureaudiobooks.core.audio.SmartRewindOverride
import com.lycoslane.pureaudiobooks.core.audio.SpeedOverride
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.database.BookmarkEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDao
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDatabase
import com.lycoslane.pureaudiobooks.core.preferences.AppPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: PureAudiobooksDatabase
    private lateinit var dao: PureAudiobooksDao
    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, PureAudiobooksDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
        preferences = AppPreferences(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun backupRoundTripRestoresThemesMonoOverridesHiddenAndBookmarks() = runTest {
        preferences.setThemeMode(AppPreferences.THEME_DARK)
        preferences.setLightAccent("indigo")
        preferences.setDarkAccent("coral")
        preferences.setMonoMode(AudioMonoMode.AUTO)
        preferences.setSmartRewindSeconds(20)
        preferences.setTextScale(1.25f)
        preferences.setUiScale(1.25f)
        preferences.setDefaultPlaybackSpeed(1.32f)
        preferences.setDefaultSilenceMode(true, 2)
        preferences.setShowRefreshButton(false)
        preferences.setShowSearchBar(false)

        val bookId = dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://dune",
                folderName = "Dune",
                title = "Dune",
                author = "Frank Herbert",
                playbackSpeed = 1.42f,
                speedOverride = 1.42f,
                skipSilence = true,
                silenceIntensity = 2,
                silenceModeOverride = SilenceOverride.STRONG,
                monoModeOverride = AudioMonoMode.LEFT,
                audioNormalizationOverride = AudioOverride.ON,
                smartRewindOverride = 15,
                isHidden = true,
                metadataLockedByUser = true,
                lastPlayedAtMs = 123_456L
            )
        )
        dao.insertTracks(
            listOf(
                ChapterTrackEntity(
                    audiobookId = bookId,
                    guid = "dune-ch1",
                    title = "Chapter 1",
                    fileUri = "content://dune/1",
                    durationMs = 300_000L,
                    positionMs = 45_000L,
                    monoModeOverride = AudioMonoMode.RIGHT
                )
            )
        )
        val track = dao.getTracksForAudiobook(bookId).first()
        dao.updateLastTrack(bookId, track.id, 123_456L)
        dao.insertBookmark(
            BookmarkEntity(
                audiobookId = bookId,
                trackId = track.id,
                trackTitle = "Chapter 1",
                positionMs = 12_000L,
                note = "Intro",
                createdAtMs = 999L
            )
        )

        val manager = BackupManager(dao, preferences)
        val exported = manager.exportBackupJson()

        // Reset prefs and database state
        preferences.setThemeMode(AppPreferences.THEME_SYSTEM)
        preferences.setLightAccent(AppPreferences.DEFAULT_ACCENT)
        preferences.setDarkAccent(AppPreferences.DEFAULT_ACCENT)
        preferences.setMonoMode(AudioMonoMode.OFF)
        preferences.setSmartRewindSeconds(10)
        preferences.setTextScale(1.0f)
        preferences.setUiScale(1.0f)
        preferences.setDefaultPlaybackSpeed(1.0f)
        preferences.setDefaultSilenceMode(false, 1)
        preferences.setShowRefreshButton(true)
        preferences.setShowSearchBar(true)
        dao.setBookSpeedOverride(bookId, SpeedOverride.INHERIT, 1.0f)
        dao.setBookSilenceModeOverride(bookId, SilenceOverride.INHERIT, false, 1)
        dao.setBookMonoModeOverride(bookId, AudioMonoMode.INHERIT)
        dao.setBookAudioNormalizationOverride(bookId, AudioOverride.INHERIT)
        dao.setBookSmartRewindOverride(bookId, SmartRewindOverride.INHERIT)
        dao.setHidden(bookId, false)
        dao.updateTrackPosition(track.id, 0L)
        dao.setTrackMonoModeOverride(track.id, AudioMonoMode.INHERIT)
        dao.deleteBookmark(dao.getAllBookmarksOnce().first().id)

        val restoredCount = manager.importBackupJson(exported)
        assertEquals(1, restoredCount)

        val restoredPrefs = preferences.settings.value
        assertEquals(AppPreferences.THEME_DARK, restoredPrefs.themeMode)
        assertEquals("indigo", restoredPrefs.lightAccent)
        assertEquals("coral", restoredPrefs.darkAccent)
        assertEquals(AudioMonoMode.AUTO, restoredPrefs.monoMode)
        assertEquals(20, restoredPrefs.smartRewindSeconds)
        assertEquals(1.25f, restoredPrefs.textScale, 0.001f)
        assertEquals(1.25f, restoredPrefs.uiScale, 0.001f)
        assertEquals(1.32f, restoredPrefs.defaultPlaybackSpeed, 0.001f)
        assertTrue(restoredPrefs.defaultSkipSilence)
        assertEquals(2, restoredPrefs.defaultSilenceIntensity)
        assertFalse(restoredPrefs.showRefreshButton)
        assertFalse(restoredPrefs.showSearchBar)

        val restoredBook = dao.getAudiobook(bookId)!!
        assertEquals(1.42f, restoredBook.playbackSpeed, 0.001f)
        assertEquals(1.42f, restoredBook.speedOverride, 0.001f)
        assertEquals(SilenceOverride.STRONG, restoredBook.silenceModeOverride)
        assertEquals(AudioMonoMode.LEFT, restoredBook.monoModeOverride)
        assertEquals(AudioOverride.ON, restoredBook.audioNormalizationOverride)
        assertEquals(15, restoredBook.smartRewindOverride)
        assertTrue(restoredBook.isHidden)
        assertEquals(track.id, restoredBook.lastTrackId)

        val restoredTrack = dao.getTrack(track.id)!!
        assertEquals(45_000L, restoredTrack.positionMs)
        assertEquals(AudioMonoMode.RIGHT, restoredTrack.monoModeOverride)

        val restoredBookmarks = dao.getAllBookmarksOnce()
        assertEquals(1, restoredBookmarks.size)
        assertEquals("Intro", restoredBookmarks.first().note)
    }

    @Test
    fun importsLegacyV1BackupGracefully() = runTest {
        val bookId = dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://v1",
                folderName = "V1",
                title = "V1"
            )
        )
        dao.insertTracks(
            listOf(
                ChapterTrackEntity(
                    audiobookId = bookId,
                    guid = "v1-tr1",
                    title = "Track 1",
                    fileUri = "content://v1/1"
                )
            )
        )
        val v1Json = """
            {
              "version": 1,
              "settings": {
                "smartRewindSeconds": 15,
                "gridLayout": false
              },
              "books": [
                {
                  "folderKey": "local://v1",
                  "playbackSpeed": 1.5,
                  "skipSilence": true,
                  "silenceIntensity": 2,
                  "tracks": [
                    { "guid": "v1-tr1", "positionMs": 22000, "isFinished": false }
                  ]
                }
              ]
            }
        """.trimIndent()

        val manager = BackupManager(dao, preferences)
        val count = manager.importBackupJson(v1Json)
        assertEquals(1, count)
        assertEquals(15, preferences.settings.value.smartRewindSeconds)
        assertFalse(preferences.settings.value.gridLayout)
        assertEquals(1.5f, dao.getAudiobook(bookId)!!.playbackSpeed, 0.001f)
        assertEquals(22_000L, dao.getTracksForAudiobook(bookId).first().positionMs)
    }

    @Test
    fun backupPreservesAutoDeleteTrashOffAndEnabledStates() = runTest {
        val manager = BackupManager(dao, preferences)

        preferences.setTrashRetentionHours(48)
        val enabledJson = manager.exportBackupJson()
        preferences.setTrashRetentionHours(-1)
        manager.importBackupJson(enabledJson)
        assertEquals(48, preferences.settings.value.trashRetentionHours)

        preferences.setTrashRetentionHours(-1)
        val disabledJson = manager.exportBackupJson()
        preferences.setTrashRetentionHours(24)
        manager.importBackupJson(disabledJson)
        assertEquals(-1, preferences.settings.value.trashRetentionHours)
    }

    @Test
    fun importClearsLastTrackIdWhenBackupBookIsUnplayed() = runTest {
        val bookId = dao.insertAudiobook(
            AudiobookEntity(
                folderKey = "local://reset-last",
                folderName = "Reset Last",
                title = "Reset Last"
            )
        )
        dao.insertTracks(
            listOf(
                ChapterTrackEntity(
                    audiobookId = bookId,
                    guid = "rl-tr1",
                    title = "Track 1",
                    fileUri = "content://rl/1"
                )
            )
        )
        val track = dao.getTracksForAudiobook(bookId).first()
        dao.updateLastTrack(bookId, track.id, 99_999L)

        val unplayedBackup = """
            {
              "version": 2,
              "books": [
                {
                  "folderKey": "local://reset-last",
                  "lastPlayedAtMs": 0,
                  "tracks": [
                    { "guid": "rl-tr1", "positionMs": 0, "isFinished": false }
                  ]
                }
              ]
            }
        """.trimIndent()

        val manager = BackupManager(dao, preferences)
        val count = manager.importBackupJson(unplayedBackup)
        assertEquals(1, count)
        val updated = dao.getAudiobook(bookId)!!
        assertEquals(0L, updated.lastTrackId)
        assertEquals(0L, updated.lastPlayedAtMs)
    }
}

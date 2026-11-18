package com.lycoslane.pureaudiobooks.core.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppSettings(
    val localFolderUri: String = "",
    val onlineLookupEnabled: Boolean = false, // Opt-in only; offline-first by default
    val trashRetentionHours: Int = 48, // 0 = immediately, 24 = 24h, 48 = 48h, 72 = 72h
    val autoMoveFinishedToTrashDays: Int = -1, // -1 = never, 0 = immediately, 2 = 2 days, 7 = 7 days
    val smartRewindSeconds: Int = 10, // 0 = off
    val audioNormalizationEnabled: Boolean = false,
    val bluetoothNextPrevSeeks: Boolean = true, // true = -10s/+30s, false = prev/next track
    val textScale: Float = 1.0f, // 0.8f..1.8f
    val appLanguage: String = "en", // "en" or "de"
    val gridLayout: Boolean = true
)

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        localFolderUri = prefs.getString(KEY_LOCAL_FOLDER_URI, "") ?: "",
        onlineLookupEnabled = prefs.getBoolean(KEY_ONLINE_LOOKUP_ENABLED, false),
        trashRetentionHours = prefs.getInt(KEY_TRASH_RETENTION_HOURS, 48).let {
            if (it in listOf(0, 24, 48, 72)) it else 48
        },
        autoMoveFinishedToTrashDays = prefs.getInt(KEY_AUTO_TRASH_FINISHED_DAYS, -1).let {
            if (it in listOf(-1, 0, 2, 7)) it else -1
        },
        smartRewindSeconds = prefs.getInt(KEY_SMART_REWIND_SECONDS, 10).coerceIn(0, 120),
        audioNormalizationEnabled = prefs.getBoolean(KEY_AUDIO_NORMALIZATION, false),
        bluetoothNextPrevSeeks = prefs.getBoolean(KEY_BLUETOOTH_NEXT_PREV_SEEKS, true),
        textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.0f).coerceIn(0.8f, 1.8f),
        appLanguage = prefs.getString(KEY_APP_LANGUAGE, "en") ?: "en",
        gridLayout = prefs.getBoolean(KEY_GRID_LAYOUT, true)
    )

    fun setLocalFolderUri(uri: String) {
        prefs.edit().putString(KEY_LOCAL_FOLDER_URI, uri).apply()
        _settings.update { it.copy(localFolderUri = uri) }
    }

    fun setOnlineLookupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_LOOKUP_ENABLED, enabled).apply()
        _settings.update { it.copy(onlineLookupEnabled = enabled) }
    }

    fun setTrashRetentionHours(hours: Int) {
        val normalized = if (hours in listOf(0, 24, 48, 72)) hours else 48
        prefs.edit().putInt(KEY_TRASH_RETENTION_HOURS, normalized).apply()
        _settings.update { it.copy(trashRetentionHours = normalized) }
    }

    fun setAutoMoveFinishedToTrashDays(days: Int) {
        val normalized = if (days in listOf(-1, 0, 2, 7)) days else -1
        prefs.edit().putInt(KEY_AUTO_TRASH_FINISHED_DAYS, normalized).apply()
        _settings.update { it.copy(autoMoveFinishedToTrashDays = normalized) }
    }

    fun setSmartRewindSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 120)
        prefs.edit().putInt(KEY_SMART_REWIND_SECONDS, clamped).apply()
        _settings.update { it.copy(smartRewindSeconds = clamped) }
    }

    fun setAudioNormalizationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_NORMALIZATION, enabled).apply()
        _settings.update { it.copy(audioNormalizationEnabled = enabled) }
    }

    fun setBluetoothNextPrevSeeks(seeks: Boolean) {
        prefs.edit().putBoolean(KEY_BLUETOOTH_NEXT_PREV_SEEKS, seeks).apply()
        _settings.update { it.copy(bluetoothNextPrevSeeks = seeks) }
    }

    fun setTextScale(scale: Float) {
        val clamped = scale.coerceIn(0.8f, 1.8f)
        prefs.edit().putFloat(KEY_TEXT_SCALE, clamped).apply()
        _settings.update { it.copy(textScale = clamped) }
    }

    fun setAppLanguage(languageTag: String) {
        val normalized = if (languageTag == "de") "de" else "en"
        prefs.edit().putString(KEY_APP_LANGUAGE, normalized).apply()
        _settings.update { it.copy(appLanguage = normalized) }
    }

    fun setGridLayout(grid: Boolean) {
        prefs.edit().putBoolean(KEY_GRID_LAYOUT, grid).apply()
        _settings.update { it.copy(gridLayout = grid) }
    }

    companion object {
        const val PREFS_NAME = "pureaudiobooks_prefs"
        const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_LOCAL_FOLDER_URI = "local_folder_uri"
        private const val KEY_ONLINE_LOOKUP_ENABLED = "online_lookup_enabled"
        private const val KEY_TRASH_RETENTION_HOURS = "trash_retention_hours"
        private const val KEY_AUTO_TRASH_FINISHED_DAYS = "auto_trash_finished_days"
        private const val KEY_SMART_REWIND_SECONDS = "smart_rewind_seconds"
        private const val KEY_AUDIO_NORMALIZATION = "audio_normalization_enabled"
        private const val KEY_BLUETOOTH_NEXT_PREV_SEEKS = "bluetooth_next_prev_seeks"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_GRID_LAYOUT = "grid_layout"
    }
}

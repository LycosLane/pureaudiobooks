package com.lycoslane.pureaudiobooks.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.core.local.LocalAudiobookScanner
import com.lycoslane.pureaudiobooks.core.preferences.AppPreferences
import com.lycoslane.pureaudiobooks.core.preferences.AppSettings
import com.lycoslane.pureaudiobooks.core.preferences.BatteryOptimizationHelper
import com.lycoslane.pureaudiobooks.ui.common.MonoModeSelector
import com.lycoslane.pureaudiobooks.ui.common.PureSegmentedControl
import com.lycoslane.pureaudiobooks.ui.player.SpeedControlPanel
import com.lycoslane.pureaudiobooks.ui.theme.PureAccent
import com.lycoslane.pureaudiobooks.ui.theme.isDarkThemeEffective
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onChangeThemeMode: (String) -> Unit,
    onChangeLightAccent: (String) -> Unit,
    onChangeDarkAccent: (String) -> Unit,
    onChangeUiScale: (Float) -> Unit,
    onChangeDefaultSpeed: (Float) -> Unit,
    onChangeDefaultSilenceMode: (Boolean, Int) -> Unit,
    onToggleAudioNormalization: (Boolean) -> Unit,
    onChangeMonoMode: (Int) -> Unit,
    onChangeSmartRewind: (Int) -> Unit,
    onChangeBluetoothNextPrevSeeks: (Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onRescanFolder: () -> Unit,
    onToggleShowRefreshButton: (Boolean) -> Unit,
    onToggleShowSearchBar: (Boolean) -> Unit,
    onToggleOnlineLookup: (Boolean) -> Unit,
    onChangeTrashRetentionHours: (Int) -> Unit,
    onChangeAutoTrashFinishedDays: (Int) -> Unit,
    onChangeAppLanguage: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showOnlineInfo by rememberSaveable { mutableStateOf(false) }
    var isIgnoringBatteryOpt by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOpt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val effectiveDark = isDarkThemeEffective(settings.themeMode)
    val activeAccentId = if (effectiveDark) settings.darkAccent else settings.lightAccent
    val activeAccent = PureAccent.fromId(activeAccentId)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Appearance (Light / Dark + 6 Curated Accents per Mode)
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                PureSegmentedControl(
                    options = listOf(
                        AppPreferences.THEME_SYSTEM to stringResource(R.string.settings_theme_system),
                        AppPreferences.THEME_LIGHT to stringResource(R.string.settings_theme_light),
                        AppPreferences.THEME_DARK to stringResource(R.string.settings_theme_dark)
                    ),
                    selected = settings.themeMode,
                    onSelect = onChangeThemeMode
                )

                SectionDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_accent_color),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(activeAccent.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PureAccent.entries.forEach { accent ->
                        val swatchColor = if (effectiveDark) accent.darkPrimary else accent.lightPrimary
                        val isSelected = accent.id == activeAccent.id
                        val accentLabel = stringResource(accent.labelRes)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .semantics {
                                    role = Role.RadioButton
                                    selected = isSelected
                                    contentDescription = accentLabel
                                }
                                .clickable {
                                    if (effectiveDark) {
                                        onChangeDarkAccent(accent.id)
                                    } else {
                                        onChangeLightAccent(accent.id)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    val checkTint = if (swatchColor.luminance() > 0.45f) {
                                        Color(0xFF101416)
                                    } else {
                                        Color.White
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = checkTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                SectionDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_ui_zoom),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${(settings.uiScale * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (abs(settings.uiScale - 1.0f) >= 0.02f) {
                            TextButton(onClick = { onChangeUiScale(1.0f) }) {
                                Text(stringResource(R.string.action_reset))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val next = (((settings.uiScale - 0.05f) * 20f).roundToInt() / 20f).coerceIn(0.80f, 1.60f)
                            onChangeUiScale(next)
                        },
                        enabled = settings.uiScale > 0.805f
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = stringResource(R.string.cd_decrease_zoom)
                        )
                    }
                    Slider(
                        value = settings.uiScale,
                        onValueChange = { raw ->
                            val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(0.80f, 1.60f)
                            onChangeUiScale(snapped)
                        },
                        valueRange = 0.80f..1.60f,
                        steps = 15,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val next = (((settings.uiScale + 0.05f) * 20f).roundToInt() / 20f).coerceIn(0.80f, 1.60f)
                            onChangeUiScale(next)
                        },
                        enabled = settings.uiScale < 1.595f
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_increase_zoom)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_ui_zoom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. Playback
            SettingsSection(title = stringResource(R.string.settings_playback_section)) {
                SpeedControlPanel(
                    targetId = 0L,
                    currentSpeed = settings.defaultPlaybackSpeed,
                    onSetSpeed = onChangeDefaultSpeed
                )
                SectionDivider()
                SettingSwitchRow(
                    title = stringResource(R.string.skip_silence),
                    checked = settings.defaultSkipSilence,
                    onCheckedChange = { enabled ->
                        onChangeDefaultSilenceMode(enabled, settings.defaultSilenceIntensity)
                    }
                )
                if (settings.defaultSkipSilence) {
                    Spacer(Modifier.height(8.dp))
                    PureSegmentedControl(
                        options = listOf(
                            0 to stringResource(R.string.silence_soft),
                            1 to stringResource(R.string.silence_medium),
                            2 to stringResource(R.string.silence_strong)
                        ),
                        selected = settings.defaultSilenceIntensity.coerceIn(0, 2),
                        onSelect = { onChangeDefaultSilenceMode(true, it) }
                    )
                }
                SectionDivider()
                SettingSwitchRow(
                    title = stringResource(R.string.settings_audio_normalization),
                    checked = settings.audioNormalizationEnabled,
                    onCheckedChange = onToggleAudioNormalization
                )
                SectionDivider()
                val isGlobalMonoOn = settings.monoMode != AudioMonoMode.OFF
                SettingSwitchRow(
                    title = stringResource(R.string.settings_mono_audio),
                    checked = isGlobalMonoOn,
                    onCheckedChange = { enabled ->
                        onChangeMonoMode(if (enabled) AudioMonoMode.MIX else AudioMonoMode.OFF)
                    }
                )
                if (isGlobalMonoOn) {
                    Spacer(Modifier.height(8.dp))
                    MonoModeSelector(
                        selectedMode = settings.monoMode,
                        onSelectMode = onChangeMonoMode
                    )
                }
                SectionDivider()
                val isSmartRewindOn = settings.smartRewindSeconds > 0
                SettingSwitchRow(
                    title = stringResource(R.string.settings_smart_rewind),
                    checked = isSmartRewindOn,
                    onCheckedChange = { enabled ->
                        onChangeSmartRewind(if (enabled) 10 else 0)
                    }
                )
                if (isSmartRewindOn) {
                    Spacer(Modifier.height(8.dp))
                    PureSegmentedControl(
                        options = listOf(
                            5 to stringResource(R.string.seconds_short, 5),
                            10 to stringResource(R.string.seconds_short, 10),
                            15 to stringResource(R.string.seconds_short, 15),
                            30 to stringResource(R.string.seconds_short, 30)
                        ),
                        selected = settings.smartRewindSeconds,
                        onSelect = onChangeSmartRewind
                    )
                }
                SectionDivider()
                Text(
                    text = stringResource(R.string.settings_bt_seek),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                PureSegmentedControl(
                    options = listOf(
                        true to stringResource(R.string.settings_bt_seek_short),
                        false to stringResource(R.string.settings_bt_chapter_short)
                    ),
                    selected = settings.bluetoothNextPrevSeeks,
                    onSelect = onChangeBluetoothNextPrevSeeks,
                    maxLines = 2
                )
                SectionDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isIgnoringBatteryOpt) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (isIgnoringBatteryOpt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.battery_opt_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (isIgnoringBatteryOpt) R.string.battery_opt_status_unrestricted
                        else R.string.battery_opt_status_restricted
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isIgnoringBatteryOpt) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { BatteryOptimizationHelper.openBatteryOptimizationSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.battery_opt_open_settings))
                    }
                }
            }

            // 3. Library & Online Lookup
            SettingsSection(title = stringResource(R.string.settings_folder_section)) {
                val hasFolder = settings.localFolderUri.isNotBlank()
                val folderStatusText = if (hasFolder) {
                    val canWrite = LocalAudiobookScanner.hasWritePermission(context, settings.localFolderUri)
                    if (canWrite) stringResource(R.string.settings_local_folder_rw)
                    else stringResource(R.string.settings_local_folder_ro)
                } else {
                    stringResource(R.string.settings_local_folder_none)
                }
                Text(
                    text = folderStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickFolder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.pick_audiobooks_folder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (hasFolder) {
                        OutlinedButton(onClick = onRescanFolder) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.rescan))
                        }
                    }
                }
                SectionDivider()
                SettingSwitchRow(
                    title = stringResource(R.string.settings_show_refresh_button),
                    checked = settings.showRefreshButton,
                    onCheckedChange = onToggleShowRefreshButton
                )
                SectionDivider()
                SettingSwitchRow(
                    title = stringResource(R.string.settings_show_search_bar),
                    checked = settings.showSearchBar,
                    onCheckedChange = onToggleShowSearchBar
                )
                SectionDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_online_lookup),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showOnlineInfo = !showOnlineInfo },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.cd_online_lookup_info),
                            tint = if (showOnlineInfo) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = settings.onlineLookupEnabled,
                        onCheckedChange = onToggleOnlineLookup
                    )
                }
                if (showOnlineInfo) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.settings_online_lookup_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // 4. Trash & Auto-cleanup
            SettingsSection(title = stringResource(R.string.settings_trash_section)) {
                val autoTrashEnabled = settings.autoMoveFinishedToTrashDays >= 0
                SettingSwitchRow(
                    title = stringResource(R.string.settings_auto_trash_finished),
                    checked = autoTrashEnabled,
                    onCheckedChange = { enabled ->
                        onChangeAutoTrashFinishedDays(if (enabled) 2 else -1)
                    }
                )
                if (autoTrashEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_auto_trash_delay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PureSegmentedControl(
                        options = listOf(
                            0 to stringResource(R.string.auto_trash_immediate),
                            2 to stringResource(R.string.auto_trash_2d),
                            7 to stringResource(R.string.auto_trash_7d)
                        ),
                        selected = settings.autoMoveFinishedToTrashDays,
                        onSelect = onChangeAutoTrashFinishedDays
                    )
                }
                SectionDivider()
                val autoDeleteTrashEnabled = settings.trashRetentionHours >= 0
                SettingSwitchRow(
                    title = stringResource(R.string.settings_auto_delete_trash),
                    checked = autoDeleteTrashEnabled,
                    onCheckedChange = { enabled ->
                        onChangeTrashRetentionHours(if (enabled) 48 else -1)
                    }
                )
                if (autoDeleteTrashEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_auto_delete_trash_delay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PureSegmentedControl(
                        options = listOf(
                            0 to stringResource(R.string.retention_immediate),
                            24 to stringResource(R.string.retention_24h),
                            48 to stringResource(R.string.retention_48h),
                            72 to stringResource(R.string.retention_72h)
                        ),
                        selected = settings.trashRetentionHours,
                        onSelect = onChangeTrashRetentionHours
                    )
                }
            }

            // 5. Language & Backup
            SettingsSection(title = stringResource(R.string.settings_section_language_backup)) {
                PureSegmentedControl(
                    options = listOf(
                        "en" to stringResource(R.string.settings_language_en),
                        "de" to stringResource(R.string.settings_language_de)
                    ),
                    selected = settings.appLanguage,
                    onSelect = onChangeAppLanguage
                )
                SectionDivider()
                Text(
                    text = stringResource(R.string.settings_backup_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.backup_export),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.backup_import),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 6. Version Footer
            val (versionName, versionCode) = remember(context) {
                runCatching {
                    val info = context.packageManager.getPackageInfo(context.packageName, 0)
                    val name = info.versionName ?: "1.1.0"
                    val code = PackageInfoCompat.getLongVersionCode(info).toInt()
                    name to code
                }.getOrDefault("1.1.0" to 2)
            }
            Text(
                text = stringResource(R.string.settings_version_format, versionName, versionCode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp)
            )
        }
    }
}

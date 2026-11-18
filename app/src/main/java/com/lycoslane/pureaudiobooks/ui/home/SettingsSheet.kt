package com.lycoslane.pureaudiobooks.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.preferences.AppPreferences
import com.lycoslane.pureaudiobooks.core.preferences.AppSettings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    preferences: AppPreferences,
    onPickFolder: () -> Unit,
    onRescanFolder: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Library folder
            Text(
                text = stringResource(R.string.settings_folder_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onPickFolder, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pick_audiobooks_folder))
                }
                if (settings.localFolderUri.isNotBlank()) {
                    OutlinedButton(onClick = onRescanFolder) {
                        Text(stringResource(R.string.rescan))
                    }
                }
            }

            HorizontalDivider()

            // Online lookup (Opt-in)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_online_lookup),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_online_lookup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.onlineLookupEnabled,
                    onCheckedChange = { preferences.setOnlineLookupEnabled(it) }
                )
            }

            HorizontalDivider()

            // Trash & Auto-cleanup
            Text(
                text = stringResource(R.string.settings_trash_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.settings_trash_retention),
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0 to R.string.retention_immediate,
                    24 to R.string.retention_24h,
                    48 to R.string.retention_48h,
                    72 to R.string.retention_72h
                ).forEach { (hours, resId) ->
                    FilterChip(
                        selected = settings.trashRetentionHours == hours,
                        onClick = { preferences.setTrashRetentionHours(hours) },
                        label = { Text(stringResource(resId)) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_auto_trash_finished),
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    -1 to R.string.auto_trash_never,
                    0 to R.string.auto_trash_immediate,
                    2 to R.string.auto_trash_2d,
                    7 to R.string.auto_trash_7d
                ).forEach { (days, resId) ->
                    FilterChip(
                        selected = settings.autoMoveFinishedToTrashDays == days,
                        onClick = { preferences.setAutoMoveFinishedToTrashDays(days) },
                        label = { Text(stringResource(resId)) }
                    )
                }
            }

            HorizontalDivider()

            // Playback
            Text(
                text = stringResource(R.string.settings_playback_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_audio_normalization), modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.audioNormalizationEnabled,
                    onCheckedChange = { preferences.setAudioNormalizationEnabled(it) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_bt_seek), modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.bluetoothNextPrevSeeks,
                    onCheckedChange = { preferences.setBluetoothNextPrevSeeks(it) }
                )
            }
            Text(stringResource(R.string.settings_smart_rewind), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 20, 30).forEach { secs ->
                    FilterChip(
                        selected = settings.smartRewindSeconds == secs,
                        onClick = { preferences.setSmartRewindSeconds(secs) },
                        label = {
                            Text(
                                if (secs == 0) stringResource(R.string.off) else "${secs}s"
                            )
                        }
                    )
                }
            }

            HorizontalDivider()

            // Language & Backup
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.appLanguage == "en",
                    onClick = { onLanguageChanged("en") },
                    label = { Text("English") }
                )
                FilterChip(
                    selected = settings.appLanguage == "de",
                    onClick = { onLanguageChanged("de") },
                    label = { Text("Deutsch") }
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_import))
                }
            }
        }
    }
}

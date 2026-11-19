package com.lycoslane.pureaudiobooks.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.core.audio.AudioOverride
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.ui.common.MonoModeSelector
import com.lycoslane.pureaudiobooks.ui.common.PureSegmentedControl
import com.lycoslane.pureaudiobooks.ui.common.monoModeLabel
import com.lycoslane.pureaudiobooks.ui.player.SilenceControlPanel
import com.lycoslane.pureaudiobooks.ui.player.SpeedControlPanel
import com.lycoslane.pureaudiobooks.ui.settings.SectionDivider
import com.lycoslane.pureaudiobooks.ui.settings.SettingSwitchRow
import com.lycoslane.pureaudiobooks.ui.settings.SettingsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookSettingsSheet(
    book: AudiobookEntity,
    remainingRawMs: Long?,
    globalMonoMode: Int,
    globalAudioNormalization: Boolean,
    onDismiss: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSilenceMode: (Boolean, Int) -> Unit,
    onSetMonoModeOverride: (Int) -> Unit,
    onSetNormalizationOverride: (Int) -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onMarkFinished: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onMoveToTrash: () -> Unit,
    onEditMetadata: (() -> Unit)? = null
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = stringResource(R.string.audiobook_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onEditMetadata != null) {
                SettingsSection(title = stringResource(R.string.audiobook_settings_section_metadata)) {
                    if (book.hasTagConflict) {
                        Text(
                            text = stringResource(R.string.tag_conflict_banner_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = onEditMetadata,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_metadata_and_cover))
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.audiobook_settings_section_audio)) {
                SpeedControlPanel(
                    targetId = book.id,
                    currentSpeed = book.playbackSpeed,
                    remainingRawMs = remainingRawMs,
                    onSetSpeed = onSetSpeed
                )

                SectionDivider()

                SilenceControlPanel(
                    skipSilence = book.skipSilence,
                    silenceIntensity = book.silenceIntensity,
                    onSetSilenceMode = onSetSilenceMode
                )

                SectionDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_audio_normalization),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (book.audioNormalizationOverride == AudioOverride.INHERIT) {
                        val globalState = stringResource(
                            if (globalAudioNormalization) R.string.override_on else R.string.override_off
                        )
                        Text(
                            text = stringResource(R.string.override_global_state, globalState),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                PureSegmentedControl(
                    options = listOf(
                        AudioOverride.INHERIT to stringResource(R.string.override_global),
                        AudioOverride.OFF to stringResource(R.string.override_off),
                        AudioOverride.ON to stringResource(R.string.override_on)
                    ),
                    selected = AudioOverride.normalize(book.audioNormalizationOverride),
                    onSelect = onSetNormalizationOverride
                )

                SectionDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_mono_audio),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (book.monoModeOverride == AudioMonoMode.INHERIT) {
                        Text(
                            text = stringResource(R.string.override_global_state, monoModeLabel(globalMonoMode)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val monoTriState = when (AudioMonoMode.normalizeOverride(book.monoModeOverride)) {
                    AudioMonoMode.INHERIT -> -1
                    AudioMonoMode.OFF -> 0
                    else -> 1
                }
                PureSegmentedControl(
                    options = listOf(
                        -1 to stringResource(R.string.override_global),
                        0 to stringResource(R.string.override_off),
                        1 to stringResource(R.string.override_on)
                    ),
                    selected = monoTriState,
                    onSelect = { choice ->
                        when (choice) {
                            -1 -> onSetMonoModeOverride(AudioMonoMode.INHERIT)
                            0 -> onSetMonoModeOverride(AudioMonoMode.OFF)
                            1 -> {
                                val nextMode = if (book.monoModeOverride in AudioMonoMode.MIX..AudioMonoMode.RIGHT) {
                                    book.monoModeOverride
                                } else if (globalMonoMode != AudioMonoMode.OFF) {
                                    AudioMonoMode.normalizeConcrete(globalMonoMode)
                                } else {
                                    AudioMonoMode.MIX
                                }
                                onSetMonoModeOverride(nextMode)
                            }
                        }
                    }
                )
                if (monoTriState == 1) {
                    Spacer(Modifier.height(10.dp))
                    MonoModeSelector(
                        selectedMode = book.monoModeOverride,
                        onSelectMode = onSetMonoModeOverride
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.audiobook_settings_section_library)) {
                SettingSwitchRow(
                    title = stringResource(R.string.hide_book),
                    checked = book.isHidden,
                    onCheckedChange = onSetHidden
                )
                SectionDivider()
                SettingSwitchRow(
                    title = stringResource(R.string.mark_finished),
                    checked = book.isFinished,
                    onCheckedChange = onMarkFinished
                )
                SectionDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onResetProgress,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.reset_progress_short),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onMoveToTrash,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.move_to_trash),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

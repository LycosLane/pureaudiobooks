package com.lycoslane.pureaudiobooks.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.core.audio.AudioOverride
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.preferences.AppSettings
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import com.lycoslane.pureaudiobooks.ui.common.MonoModeSelector
import com.lycoslane.pureaudiobooks.ui.common.PureSegmentedControl
import com.lycoslane.pureaudiobooks.ui.common.monoModeLabel
import com.lycoslane.pureaudiobooks.ui.player.SilenceControlPanel
import com.lycoslane.pureaudiobooks.ui.player.SpeedControlPanel
import com.lycoslane.pureaudiobooks.ui.settings.SectionDivider
import com.lycoslane.pureaudiobooks.ui.settings.SettingsSection
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookSettingsSheet(
    book: AudiobookEntity,
    remainingRawMs: Long?,
    globalSettings: AppSettings,
    onDismiss: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSilenceMode: (Boolean, Int) -> Unit,
    onSetMonoModeOverride: (Int) -> Unit,
    onSetNormalizationOverride: (Int) -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onMarkFinished: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onMoveToTrash: () -> Unit,
    onApplyToAllAudiobooks: () -> Unit,
    onEditMetadata: (() -> Unit)? = null,
    progressFraction: Float? = null,
    startInQuickMode: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = !startInQuickMode)
    val scope = rememberCoroutineScope()

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
            if (startInQuickMode && sheetState.currentValue != SheetValue.Expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { scope.launch { sheetState.expand() } }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.pull_up_all_settings_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Header with cover + book title + optional progress badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverImage(
                    url = book.artworkUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (startInQuickMode) book.title else stringResource(R.string.audiobook_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = if (startInQuickMode) {
                        book.author.ifBlank { stringResource(R.string.audiobook_settings_title) }
                    } else {
                        book.title
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progressFraction != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${(progressFraction * 100f).roundToInt().coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Quick Mode puts unified status card first so pulling up seamlessly exposes the rest below
            if (startInQuickMode) {
                AudiobookStatusActionsCard(
                    book = book,
                    onMarkFinished = onMarkFinished,
                    onResetProgress = onResetProgress,
                    onSetHidden = onSetHidden,
                    onMoveToTrash = onMoveToTrash
                )

                if (sheetState.currentValue != SheetValue.Expanded) {
                    FilledTonalButton(
                        onClick = { scope.launch { sheetState.expand() } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.open_all_audiobook_settings))
                    }
                }
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

            AudiobookAudioSettingsSection(
                book = book,
                remainingRawMs = remainingRawMs,
                globalSettings = globalSettings,
                onSetSpeed = onSetSpeed,
                onSetSilenceMode = onSetSilenceMode,
                onSetMonoModeOverride = onSetMonoModeOverride,
                onSetNormalizationOverride = onSetNormalizationOverride,
                onApplyToAllAudiobooks = onApplyToAllAudiobooks
            )

            if (!startInQuickMode) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.audiobook_settings_section_library),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    AudiobookStatusActionsCard(
                        book = book,
                        onMarkFinished = onMarkFinished,
                        onResetProgress = onResetProgress,
                        onSetHidden = onSetHidden,
                        onMoveToTrash = onMoveToTrash
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookAudioSettingsSection(
    book: AudiobookEntity,
    remainingRawMs: Long?,
    globalSettings: AppSettings,
    onSetSpeed: (Float) -> Unit,
    onSetSilenceMode: (Boolean, Int) -> Unit,
    onSetMonoModeOverride: (Int) -> Unit,
    onSetNormalizationOverride: (Int) -> Unit,
    onApplyToAllAudiobooks: () -> Unit
) {
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
                    if (globalSettings.audioNormalizationEnabled) R.string.override_on else R.string.override_off
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
                    text = stringResource(R.string.override_global_state, monoModeLabel(globalSettings.monoMode)),
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
                        } else if (globalSettings.monoMode != AudioMonoMode.OFF) {
                            AudioMonoMode.normalizeConcrete(globalSettings.monoMode)
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

        val speedDiffers = abs(book.playbackSpeed - globalSettings.defaultPlaybackSpeed) >= 0.01f
        val silenceDiffers = book.skipSilence != globalSettings.defaultSkipSilence ||
            (book.skipSilence && book.silenceIntensity.coerceIn(0, 2) != globalSettings.defaultSilenceIntensity.coerceIn(0, 2))
        val effNorm = AudioOverride.resolve(globalSettings.audioNormalizationEnabled, book.audioNormalizationOverride)
        val normDiffers = effNorm != globalSettings.audioNormalizationEnabled
        val effMono = AudioMonoMode.resolveEffective(globalSettings.monoMode, book.monoModeOverride, AudioMonoMode.INHERIT)
        val monoDiffers = effMono != AudioMonoMode.normalizeConcrete(globalSettings.monoMode)

        val diffLabels = buildList {
            if (speedDiffers) add(Formatters.formatPlaybackSpeed(book.playbackSpeed))
            if (silenceDiffers) {
                add(
                    if (!book.skipSilence) stringResource(R.string.off)
                    else stringResource(
                        when (book.silenceIntensity.coerceIn(0, 2)) {
                            0 -> R.string.silence_soft
                            1 -> R.string.silence_medium
                            else -> R.string.silence_strong
                        }
                    )
                )
            }
            if (normDiffers) add(stringResource(R.string.settings_audio_normalization))
            if (monoDiffers) add(monoModeLabel(effMono))
        }

        if (diffLabels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.differs_from_global_summary, diffLabels.joinToString(", ")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        onClick = onApplyToAllAudiobooks,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_to_all_audiobooks))
                    }
                }
            }
        }
    }
}

@Composable
fun AudiobookStatusActionsCard(
    book: AudiobookEntity,
    onMarkFinished: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onMoveToTrash: () -> Unit
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            StatusActionRow(
                icon = if (book.isFinished) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                label = stringResource(
                    if (book.isFinished) R.string.mark_unfinished else R.string.mark_finished
                ),
                tint = if (book.isFinished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { onMarkFinished(!book.isFinished) }
            )
            HorizontalDivider(color = dividerColor)
            StatusActionRow(
                icon = Icons.Default.Replay,
                label = stringResource(R.string.reset_progress),
                onClick = onResetProgress
            )
            HorizontalDivider(color = dividerColor)
            StatusActionRow(
                icon = if (book.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                label = stringResource(
                    if (book.isHidden) R.string.unhide_book else R.string.hide_book
                ),
                tint = if (book.isHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { onSetHidden(!book.isHidden) }
            )
            HorizontalDivider(color = dividerColor)
            StatusActionRow(
                icon = Icons.Default.DeleteOutline,
                label = stringResource(R.string.move_to_trash),
                tint = MaterialTheme.colorScheme.error,
                onClick = onMoveToTrash
            )
        }
    }
}

@Composable
private fun StatusActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

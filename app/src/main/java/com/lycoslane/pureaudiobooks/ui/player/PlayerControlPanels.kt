package com.lycoslane.pureaudiobooks.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.AudioMonoMode
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import com.lycoslane.pureaudiobooks.ui.common.PureSegmentedControl
import com.lycoslane.pureaudiobooks.ui.common.monoModeLabel
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerControlPanel {
    NONE,
    SPEED,
    SILENCE,
    AUDIO_FIX,
    SLEEP
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerActionTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    stateText: String? = null
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = border,
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                role = Role.Button
                if (stateText != null) stateDescription = stateText
            }
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PlayerSecondaryTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = border,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlayerPrimaryControlsBar(
    bookId: Long,
    trackId: Long,
    playbackSpeed: Float,
    skipSilence: Boolean,
    silenceIntensity: Int,
    globalMonoMode: Int,
    bookMonoOverride: Int,
    trackMonoOverride: Int,
    effectiveMonoMode: Int,
    sleepTimerRemainingMs: Long,
    sleepTimerPresetMinutes: Int,
    sleepAtEndOfTrack: Boolean,
    remainingRawMs: Long?,
    activePanel: PlayerControlPanel,
    panelAboveTiles: Boolean = false,
    onTogglePanel: (PlayerControlPanel) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSilenceMode: (Boolean, Int) -> Unit,
    onSetTrackMonoOverride: (Int) -> Unit,
    onSetBookMonoOverride: (Int) -> Unit,
    onSetSleepMinutes: (Int) -> Unit,
    onSetSleepEndOfTrack: (Boolean) -> Unit
) {
    val speedLabel = Formatters.formatPlaybackSpeed(playbackSpeed)
    val silenceStateText = stringResource(if (skipSilence) R.string.state_on else R.string.state_off)
    val silenceLongClickLabel = stringResource(R.string.player_silence)
    val monoTileLabel = if (effectiveMonoMode == AudioMonoMode.OFF) {
        stringResource(R.string.player_mono_short)
    } else {
        monoModeLabel(effectiveMonoMode)
    }
    val sleepLabel = when {
        sleepAtEndOfTrack -> stringResource(R.string.sleep_end_of_chapter_short)
        sleepTimerRemainingMs > 0L -> Formatters.formatDuration(sleepTimerRemainingMs)
        else -> stringResource(R.string.player_sleep)
    }

    val panelContent: @Composable () -> Unit = {
        if (activePanel != PlayerControlPanel.NONE) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    when (activePanel) {
                        PlayerControlPanel.SPEED -> SpeedControlPanel(
                            targetId = bookId,
                            currentSpeed = playbackSpeed,
                            remainingRawMs = remainingRawMs,
                            onSetSpeed = onSetSpeed
                        )
                        PlayerControlPanel.SILENCE -> SilenceControlPanel(
                            skipSilence = skipSilence,
                            silenceIntensity = silenceIntensity,
                            onSetSilenceMode = onSetSilenceMode
                        )
                        PlayerControlPanel.AUDIO_FIX -> AudioFixControlPanel(
                            trackId = trackId,
                            globalMonoMode = globalMonoMode,
                            bookMonoOverride = bookMonoOverride,
                            trackMonoOverride = trackMonoOverride,
                            onSetTrackMonoOverride = onSetTrackMonoOverride,
                            onSetBookMonoOverride = onSetBookMonoOverride
                        )
                        PlayerControlPanel.SLEEP -> SleepControlPanel(
                            remainingMs = sleepTimerRemainingMs,
                            presetMinutes = sleepTimerPresetMinutes,
                            sleepAtEndOfTrack = sleepAtEndOfTrack,
                            onSetSleepTimer = onSetSleepMinutes,
                            onSetSleepEndOfTrack = onSetSleepEndOfTrack
                        )
                        PlayerControlPanel.NONE -> Unit
                    }
                }
            }
        }
    }

    if (panelAboveTiles && activePanel != PlayerControlPanel.NONE) {
        panelContent()
        Spacer(Modifier.height(10.dp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlayerActionTile(
            icon = Icons.Default.Speed,
            label = speedLabel,
            active = activePanel == PlayerControlPanel.SPEED || Formatters.hasCustomSpeed(playbackSpeed),
            onClick = { onTogglePanel(PlayerControlPanel.SPEED) },
            modifier = Modifier.weight(1f)
        )

        PlayerActionTile(
            icon = Icons.Default.GraphicEq,
            label = stringResource(R.string.player_silence_short),
            active = skipSilence || activePanel == PlayerControlPanel.SILENCE,
            stateText = silenceStateText,
            onLongClickLabel = silenceLongClickLabel,
            onClick = { onTogglePanel(PlayerControlPanel.SILENCE) },
            onLongClick = { onSetSilenceMode(!skipSilence, silenceIntensity) },
            modifier = Modifier.weight(1f)
        )

        PlayerActionTile(
            icon = Icons.Default.Headphones,
            label = monoTileLabel,
            active = effectiveMonoMode != AudioMonoMode.OFF ||
                trackMonoOverride != AudioMonoMode.INHERIT ||
                bookMonoOverride != AudioMonoMode.INHERIT ||
                activePanel == PlayerControlPanel.AUDIO_FIX,
            onClick = { onTogglePanel(PlayerControlPanel.AUDIO_FIX) },
            modifier = Modifier.weight(1f)
        )

        PlayerActionTile(
            icon = Icons.Default.Bedtime,
            label = sleepLabel,
            active = sleepTimerRemainingMs > 0L || sleepAtEndOfTrack || activePanel == PlayerControlPanel.SLEEP,
            onClick = { onTogglePanel(PlayerControlPanel.SLEEP) },
            modifier = Modifier.weight(1f)
        )
    }

    if (!panelAboveTiles && activePanel != PlayerControlPanel.NONE) {
        Spacer(Modifier.height(10.dp))
        panelContent()
    }
}

@Composable
fun SpeedControlPanel(
    targetId: Long,
    currentSpeed: Float,
    onSetSpeed: (Float) -> Unit,
    remainingRawMs: Long? = null
) {
    var sliderSpeed by remember(targetId, currentSpeed) {
        mutableFloatStateOf(currentSpeed.coerceIn(0.50f, 3.00f))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.player_speed_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (remainingRawMs != null && remainingRawMs > 0L) {
                val effRemaining = Formatters.effectiveDurationMs(remainingRawMs, sliderSpeed)
                Text(
                    text = stringResource(
                        R.string.remaining_time_compact,
                        Formatters.formatCompactHoursMinutes(effRemaining)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    val next = (((sliderSpeed - 0.02f) * 100f).roundToInt().coerceIn(50, 300)) / 100f
                    sliderSpeed = next
                    onSetSpeed(next)
                }
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = stringResource(R.string.cd_speed_decrease)
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        sliderSpeed = 1.0f
                        onSetSpeed(1.0f)
                    }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Formatters.formatPlaybackSpeed(sliderSpeed),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            IconButton(
                onClick = {
                    val next = (((sliderSpeed + 0.02f) * 100f).roundToInt().coerceIn(50, 300)) / 100f
                    sliderSpeed = next
                    onSetSpeed(next)
                }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_speed_increase)
                )
            }
        }
    }

    Slider(
        value = sliderSpeed,
        onValueChange = { raw ->
            sliderSpeed = (raw * 100f).roundToInt().coerceIn(50, 300) / 100f
        },
        onValueChangeFinished = { onSetSpeed(sliderSpeed) },
        valueRange = 0.50f..3.00f,
        steps = 0,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f).forEach { preset ->
            val selected = abs(sliderSpeed - preset) < 0.01f
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        sliderSpeed = preset
                        onSetSpeed(preset)
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Formatters.formatPlaybackSpeed(preset),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SilenceControlPanel(
    skipSilence: Boolean,
    silenceIntensity: Int,
    onSetSilenceMode: (Boolean, Int) -> Unit
) {
    val selectedOption = if (skipSilence) silenceIntensity.coerceIn(0, 2) else -1
    val statusText = when (selectedOption) {
        0 -> stringResource(R.string.silence_soft)
        1 -> stringResource(R.string.silence_medium)
        2 -> stringResource(R.string.silence_strong)
        else -> stringResource(R.string.off)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.skip_silence),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Spacer(Modifier.height(10.dp))
    PureSegmentedControl(
        options = listOf(
            -1 to stringResource(R.string.off),
            0 to stringResource(R.string.silence_soft),
            1 to stringResource(R.string.silence_medium),
            2 to stringResource(R.string.silence_strong)
        ),
        selected = selectedOption,
        onSelect = { level ->
            if (level < 0) {
                onSetSilenceMode(false, silenceIntensity)
            } else {
                onSetSilenceMode(true, level)
            }
        }
    )
}

@Composable
internal fun AudioFixControlPanel(
    trackId: Long,
    globalMonoMode: Int,
    bookMonoOverride: Int,
    trackMonoOverride: Int,
    onSetTrackMonoOverride: (Int) -> Unit,
    onSetBookMonoOverride: (Int) -> Unit
) {
    var selectedScope by rememberSaveable(trackId) {
        val defaultScope = if (
            trackMonoOverride == AudioMonoMode.INHERIT &&
            bookMonoOverride != AudioMonoMode.INHERIT
        ) 1 else 0
        mutableIntStateOf(defaultScope)
    }
    var showInfo by rememberSaveable { mutableStateOf(false) }

    val hasOverride = if (selectedScope == 0) {
        trackMonoOverride != AudioMonoMode.INHERIT
    } else {
        bookMonoOverride != AudioMonoMode.INHERIT
    }
    val inheritedMode = if (selectedScope == 0) {
        if (bookMonoOverride != AudioMonoMode.INHERIT) bookMonoOverride
        else AudioMonoMode.normalizeConcrete(globalMonoMode)
    } else {
        AudioMonoMode.normalizeConcrete(globalMonoMode)
    }
    val displayedMode = if (hasOverride) {
        AudioMonoMode.normalizeConcrete(if (selectedScope == 0) trackMonoOverride else bookMonoOverride)
    } else {
        inheritedMode
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.player_audio_fix_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasOverride) {
                TextButton(
                    onClick = {
                        if (selectedScope == 0) onSetTrackMonoOverride(AudioMonoMode.INHERIT)
                        else onSetBookMonoOverride(AudioMonoMode.INHERIT)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_reset),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            IconButton(
                onClick = { showInfo = !showInfo },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.cd_mono_info),
                    tint = if (showInfo) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showInfo) {
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.mono_mode_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    PureSegmentedControl(
        options = listOf(
            0 to stringResource(R.string.player_scope_chapter),
            1 to stringResource(R.string.player_scope_book)
        ),
        selected = selectedScope,
        onSelect = { selectedScope = it }
    )

    Spacer(Modifier.height(8.dp))
    val inheritedLabel = monoModeLabel(inheritedMode)
    val statusLine = when {
        hasOverride && selectedScope == 0 -> stringResource(R.string.player_audio_fix_active_chapter)
        hasOverride -> stringResource(R.string.player_audio_fix_active_book)
        else -> stringResource(R.string.player_audio_fix_inherited, inheritedLabel)
    }
    Text(
        text = statusLine,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (hasOverride) FontWeight.SemiBold else FontWeight.Normal,
        color = if (hasOverride) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(8.dp))
    PureSegmentedControl(
        options = listOf(
            AudioMonoMode.OFF to stringResource(R.string.mono_mode_off),
            AudioMonoMode.MIX to stringResource(R.string.mono_mode_mix),
            AudioMonoMode.AUTO to stringResource(R.string.mono_mode_smart),
            AudioMonoMode.LEFT to stringResource(R.string.mono_mode_left),
            AudioMonoMode.RIGHT to stringResource(R.string.mono_mode_right)
        ),
        selected = displayedMode,
        onSelect = { chosen ->
            if (selectedScope == 0) {
                if (hasOverride || chosen != inheritedMode) {
                    onSetTrackMonoOverride(chosen)
                }
            } else {
                if (hasOverride || chosen != inheritedMode) {
                    onSetBookMonoOverride(chosen)
                }
            }
        }
    )
}

@Composable
internal fun SleepControlPanel(
    remainingMs: Long,
    presetMinutes: Int,
    sleepAtEndOfTrack: Boolean,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepEndOfTrack: (Boolean) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var sleepStep by remember(presetMinutes, remainingMs) {
        val currentMins = if (remainingMs <= 0L) 0 else presetMinutes.coerceIn(0, 120)
        mutableIntStateOf(((currentMins + 2) / 5).coerceIn(0, 24))
    }

    val selectedMinutes = sleepStep * 5
    val formattedSelected = formatSleepMinutesLabel(selectedMinutes)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.player_sleep_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    sleepAtEndOfTrack -> stringResource(R.string.sleep_end_of_chapter)
                    !isDragging && remainingMs > 0L -> Formatters.formatDuration(remainingMs)
                    else -> formattedSelected
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (remainingMs > 0L || sleepAtEndOfTrack || selectedMinutes > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (remainingMs > 0L || sleepAtEndOfTrack) {
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = {
                        isDragging = false
                        sleepStep = 0
                        onSetSleepEndOfTrack(false)
                        onSetSleepTimer(0)
                    }
                ) {
                    Text(stringResource(R.string.off))
                }
            }
        }
    }

    Slider(
        value = sleepStep.toFloat(),
        onValueChange = { raw ->
            isDragging = true
            sleepStep = raw.roundToInt().coerceIn(0, 24)
        },
        onValueChangeFinished = {
            isDragging = false
            onSetSleepTimer(sleepStep * 5)
        },
        valueRange = 0f..24f,
        steps = 23,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val presets = listOf(
            0 to stringResource(R.string.off),
            15 to stringResource(R.string.sleep_minutes_compact, 15),
            30 to stringResource(R.string.sleep_minutes_compact, 30),
            60 to stringResource(R.string.sleep_minutes_compact, 60),
            120 to stringResource(R.string.sleep_hours_exact, 2)
        )
        presets.forEach { (mins, label) ->
            val selected = if (sleepAtEndOfTrack) {
                false
            } else if (remainingMs <= 0L && !isDragging) {
                mins == 0
            } else {
                selectedMinutes == mins
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        isDragging = false
                        sleepStep = mins / 5
                        onSetSleepTimer(mins)
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (sleepAtEndOfTrack) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (sleepAtEndOfTrack) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onSetSleepEndOfTrack(!sleepAtEndOfTrack) }
    ) {
        Text(
            text = stringResource(R.string.sleep_end_of_chapter),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (sleepAtEndOfTrack) FontWeight.SemiBold else FontWeight.Medium,
            color = if (sleepAtEndOfTrack) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
        )
    }
}

@Composable
private fun formatSleepMinutesLabel(minutes: Int): String {
    if (minutes <= 0) return stringResource(R.string.off)
    val hours = minutes / 60
    val remMins = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.sleep_minutes_short, remMins)
        remMins == 0 -> stringResource(R.string.sleep_hours_exact, hours)
        else -> stringResource(R.string.sleep_hours_minutes, hours, remMins)
    }
}

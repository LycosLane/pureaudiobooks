package com.lycoslane.pureaudiobooks.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(
    tracks: List<ChapterTrackEntity>,
    activeTrackId: Long?,
    fallbackTrackId: Long,
    activePositionMs: Long,
    onSelectTrack: (ChapterTrackEntity) -> Unit,
    onToggleTrackFinished: (Long, Boolean) -> Unit,
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.chapters_header, tracks.size),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val isCurrent = track.id == activeTrackId || (activeTrackId == null && track.id == fallbackTrackId)
                    TrackSwipeRow(
                        track = track,
                        isCurrent = isCurrent,
                        currentPosMs = if (track.id == activeTrackId) activePositionMs else track.positionMs,
                        onClick = { onSelectTrack(track) },
                        onDoubleTap = { onToggleTrackFinished(track.id, !track.isFinished) },
                        onSwipeToggleFinished = { onToggleTrackFinished(track.id, !track.isFinished) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackSwipeRow(
    track: ChapterTrackEntity,
    isCurrent: Boolean,
    currentPosMs: Long,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeToggleFinished: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var rowWidth by remember { mutableFloatStateOf(1f) }
    var triggeredHaptic by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidth = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        Surface(
            tonalElevation = if (isCurrent) 4.dp else 1.dp,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(track.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX / rowWidth >= 0.45f) {
                                onSwipeToggleFinished()
                            }
                            offsetX = 0f
                            triggeredHaptic = false
                        },
                        onDragCancel = {
                            offsetX = 0f
                            triggeredHaptic = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(0f, rowWidth * 0.6f)
                            val reached = offsetX / rowWidth >= 0.45f
                            if (reached && !triggeredHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                triggeredHaptic = true
                            }
                        }
                    )
                }
                .combinedClickable(
                    onClick = onClick,
                    onDoubleClick = onDoubleTap
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (track.isFinished) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val durText = Formatters.formatDuration(track.durationMs)
                    val posText = if (currentPosMs > 0L && !track.isFinished) {
                        "${Formatters.formatDuration(currentPosMs)} / $durText"
                    } else {
                        durText
                    }
                    Text(
                        text = posText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (track.isFinished) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.finished),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isCurrent) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

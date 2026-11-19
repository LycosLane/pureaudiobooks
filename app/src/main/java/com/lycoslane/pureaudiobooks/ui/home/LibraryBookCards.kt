package com.lycoslane.pureaudiobooks.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.SpeedOverride
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage
import com.lycoslane.pureaudiobooks.ui.common.Formatters

@Composable
fun LibrarySectionHeader(
    title: String,
    count: Int,
    trailing: String? = null,
    highlighted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InProgressBookListCard(
    item: AudiobookWithTracks,
    defaultPlaybackSpeed: Float,
    activeTrackId: Long?,
    activePositionMs: Long,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onPlayToggle: () -> Unit
) {
    val book = item.audiobook
    val sortedTracks = remember(item.tracks) { Formatters.sortedTracks(item.tracks) }
    val summary = Formatters.calculateBookProgress(
        tracks = sortedTracks,
        activeTrackId = activeTrackId,
        activePositionMs = activePositionMs,
        fallbackTrackId = book.lastTrackId
    )
    val isThisPlaying = isPlaying && activeTrackId != null && sortedTracks.any { it.id == activeTrackId }
    val effectiveSpeed = SpeedOverride.resolve(defaultPlaybackSpeed, book.speedOverride)
    val effectiveRemaining = Formatters.effectiveDurationMs(summary.remainingMs, effectiveSpeed)
    val remainingLabel = if (Formatters.hasCustomSpeed(effectiveSpeed)) {
        stringResource(
            R.string.remaining_time_at_speed,
            Formatters.formatCompactHoursMinutes(effectiveRemaining),
            Formatters.formatPlaybackSpeed(effectiveSpeed)
        )
    } else {
        stringResource(
            R.string.remaining_time_compact,
            Formatters.formatCompactHoursMinutes(summary.remainingMs)
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCoverImage(
                    url = book.artworkUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (book.hasTagConflict) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = stringResource(R.string.filter_conflicts),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    val subtitle = buildString {
                        if (book.author.isNotBlank()) {
                            append(book.author)
                            if (summary.totalTracks > 1) append(" · ")
                        }
                        if (summary.totalTracks > 1) {
                            append(stringResource(R.string.chapter_short, summary.currentTrackIndex + 1, summary.totalTracks))
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = remainingLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isThisPlaying) R.string.cd_pause else R.string.cd_play)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { summary.fraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Text(
                    text = "${(summary.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InProgressBookGridCard(
    item: AudiobookWithTracks,
    defaultPlaybackSpeed: Float,
    activeTrackId: Long?,
    activePositionMs: Long,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onPlayToggle: () -> Unit
) {
    val book = item.audiobook
    val sortedTracks = remember(item.tracks) { Formatters.sortedTracks(item.tracks) }
    val summary = Formatters.calculateBookProgress(
        tracks = sortedTracks,
        activeTrackId = activeTrackId,
        activePositionMs = activePositionMs,
        fallbackTrackId = book.lastTrackId
    )
    val isThisPlaying = isPlaying && activeTrackId != null && sortedTracks.any { it.id == activeTrackId }
    val effectiveSpeed = SpeedOverride.resolve(defaultPlaybackSpeed, book.speedOverride)
    val effectiveRemaining = Formatters.effectiveDurationMs(summary.remainingMs, effectiveSpeed)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                BookCoverImage(
                    url = book.artworkUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                )
                if (book.hasTagConflict) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = stringResource(R.string.filter_conflicts),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
                FilledIconButton(
                    onClick = onPlayToggle,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isThisPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author.ifBlank { stringResource(R.string.tracks_count, item.tracks.size) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.remaining_time_compact,
                        Formatters.formatCompactHoursMinutes(effectiveRemaining)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(summary.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { summary.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnstartedBookListCard(
    item: AudiobookWithTracks,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val book = item.audiobook
    val totalMs = remember(item.tracks) { item.tracks.sumOf { it.durationMs.coerceAtLeast(0L) } }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverImage(
                url = book.artworkUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (book.hasTagConflict) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = stringResource(R.string.filter_conflicts),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                val sub = buildString {
                    if (book.author.isNotBlank()) {
                        append(book.author)
                        append(" · ")
                    }
                    append(stringResource(R.string.tracks_count, item.tracks.size))
                }
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (totalMs > 0L) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = Formatters.formatCompactHoursMinutes(totalMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnstartedBookGridCard(
    item: AudiobookWithTracks,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val book = item.audiobook
    val totalMs = remember(item.tracks) { item.tracks.sumOf { it.durationMs.coerceAtLeast(0L) } }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                BookCoverImage(
                    url = book.artworkUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                )
                if (book.hasTagConflict) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = stringResource(R.string.filter_conflicts),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
                if (totalMs > 0L) {
                    Text(
                        text = Formatters.formatCompactHoursMinutes(totalMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.68f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author.ifBlank { stringResource(R.string.tracks_count, item.tracks.size) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FinishedBookListCard(
    item: AudiobookWithTracks,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val book = item.audiobook
    val totalMs = remember(item.tracks) { item.tracks.sumOf { it.durationMs.coerceAtLeast(0L) } }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverImage(
                url = book.artworkUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .alpha(0.82f)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = buildString {
                    if (book.author.isNotBlank()) {
                        append(book.author)
                        if (totalMs > 0L) append(" · ")
                    }
                    if (totalMs > 0L) {
                        append(Formatters.formatCompactHoursMinutes(totalMs))
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.finished),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FinishedBookGridCard(
    item: AudiobookWithTracks,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val book = item.audiobook
    val totalMs = remember(item.tracks) { item.tracks.sumOf { it.durationMs.coerceAtLeast(0L) } }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box {
                BookCoverImage(
                    url = book.artworkUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .alpha(0.82f)
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.finished),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (totalMs > 0L) Formatters.formatCompactHoursMinutes(totalMs)
                else book.author.ifBlank { stringResource(R.string.finished) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HiddenBookRow(
    item: AudiobookWithTracks,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onUnhide: () -> Unit
) {
    val book = item.audiobook
    val totalMs = remember(item.tracks) { item.tracks.sumOf { it.durationMs.coerceAtLeast(0L) } }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverImage(
                url = book.artworkUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .alpha(0.75f)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = buildString {
                    if (book.author.isNotBlank()) {
                        append(book.author)
                        if (totalMs > 0L) append(" · ")
                    }
                    if (totalMs > 0L) {
                        append(Formatters.formatCompactHoursMinutes(totalMs))
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onUnhide) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_unhide_short))
            }
        }
    }
}

@Composable
fun TrashBookRow(
    item: AudiobookWithTracks,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverImage(
                url = item.audiobook.artworkUrl,
                contentDescription = item.audiobook.title,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.audiobook.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.audiobook.author.isNotBlank()) {
                    Text(
                        text = item.audiobook.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.RestoreFromTrash, contentDescription = stringResource(R.string.restore))
            }
            IconButton(onClick = onDeletePermanently) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = stringResource(R.string.delete_permanently),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

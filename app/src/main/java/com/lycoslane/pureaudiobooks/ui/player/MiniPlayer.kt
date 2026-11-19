package com.lycoslane.pureaudiobooks.ui.player

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.audio.PlayerState
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage
import com.lycoslane.pureaudiobooks.ui.common.Formatters
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    state: PlayerState,
    onTogglePlayPause: () -> Unit,
    onRewind10: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val book = state.currentBook ?: return
    val track = state.currentTrack ?: return
    val bookProgress = if (state.bookTracks.isNotEmpty()) {
        Formatters.calculateBookProgress(
            tracks = state.bookTracks,
            activeTrackId = track.id,
            activePositionMs = state.positionMs,
            fallbackTrackId = book.lastTrackId
        )
    } else null

    val progressFraction = bookProgress?.fraction ?: if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val rawRemaining = bookProgress?.remainingMs ?: (state.durationMs - state.positionMs).coerceAtLeast(0L)
    val effectiveRemaining = Formatters.effectiveDurationMs(rawRemaining, state.playbackSpeed)
    val remainingText = if (Formatters.hasCustomSpeed(state.playbackSpeed)) {
        stringResource(
            R.string.remaining_time_at_speed,
            Formatters.formatCompactHoursMinutes(effectiveRemaining),
            Formatters.formatPlaybackSpeed(state.playbackSpeed)
        )
    } else {
        stringResource(
            R.string.remaining_time_compact,
            Formatters.formatCompactHoursMinutes(effectiveRemaining)
        )
    }
    val chapterLine = if (state.totalBookTracks > 1) {
        "${stringResource(R.string.chapter_short, state.currentTrackIndex + 1, state.totalBookTracks)} · ${track.title}"
    } else {
        track.title
    }
    val pctText = "${(progressFraction * 100f).roundToInt()}%"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.cd_expand_player),
                onClick = onExpand
            ),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverImage(
                    url = book.artworkUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = chapterLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$pctText · $remainingText",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRewind10) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = stringResource(R.string.cd_rewind_10)
                    )
                }
                Spacer(Modifier.width(4.dp))
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.cd_pause else R.string.cd_play
                        )
                    )
                }
            }
        }
    }
}

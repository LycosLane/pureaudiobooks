package com.lycoslane.pureaudiobooks.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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

@Composable
fun MiniPlayer(
    state: PlayerState,
    onTogglePlayPause: () -> Unit,
    onForward30: () -> Unit,
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
    val remainingText = stringResource(
        R.string.remaining_time_compact,
        Formatters.formatCompactHoursMinutes(effectiveRemaining)
    )
    val subtitleText = buildString {
        append(remainingText)
        if (Formatters.hasCustomSpeed(state.playbackSpeed)) {
            append(" (")
            append(Formatters.formatPlaybackSpeed(state.playbackSpeed))
            append(")")
        }
        append(" · ")
        if (state.totalBookTracks > 1) {
            append("${state.currentTrackIndex + 1}/${state.totalBookTracks}")
        } else {
            append(track.title)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.cd_expand_player),
                onClick = onExpand
            ),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverImage(
                    url = book.artworkUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.cd_pause else R.string.cd_play
                        )
                    )
                }
                IconButton(onClick = onForward30) {
                    Icon(
                        imageVector = Icons.Default.Forward30,
                        contentDescription = stringResource(R.string.cd_forward_30)
                    )
                }
            }
        }
    }
}

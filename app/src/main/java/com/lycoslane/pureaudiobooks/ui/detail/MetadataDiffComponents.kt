package com.lycoslane.pureaudiobooks.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.network.OnlineBookMatch
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage

@Composable
fun MetadataCandidateStrip(
    candidates: List<OnlineBookMatch>,
    selectedIndex: Int,
    onSelectCandidate: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.metadata_candidates_header, candidates.size),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(candidates) { idx, match ->
                val selected = idx == selectedIndex
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .width(114.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectCandidate(idx) }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            BookCoverImage(
                                url = match.coverUrl,
                                contentDescription = match.title,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Text(
                                text = "${idx + 1}/${candidates.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = match.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = match.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataCoverDiffCard(
    currentCoverUrl: String,
    candidateCoverUrl: String,
    candidateSource: String,
    candidateIndex: Int,
    totalCandidates: Int,
    useNewCover: Boolean,
    onSelectSide: (Boolean) -> Unit,
    onStepCandidate: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiffFieldHeader(
                title = stringResource(R.string.field_cover),
                candidateIndex = candidateIndex,
                totalCandidates = totalCandidates,
                onStepCandidate = onStepCandidate
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiffOptionColumn(
                    header = stringResource(R.string.metadata_before_label),
                    selected = !useNewCover,
                    onClick = { onSelectSide(false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BookCoverImage(
                            url = currentCoverUrl,
                            contentDescription = stringResource(R.string.metadata_before_label),
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Text(
                            text = stringResource(R.string.metadata_current_label),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                DiffOptionColumn(
                    header = stringResource(R.string.metadata_after_label),
                    selected = useNewCover,
                    onClick = { onSelectSide(true) },
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(totalCandidates) {
                            var dragTotal = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragTotal = 0f },
                                onDragEnd = {
                                    when {
                                        dragTotal < -36f -> onStepCandidate(1)
                                        dragTotal > 36f -> onStepCandidate(-1)
                                    }
                                    dragTotal = 0f
                                },
                                onHorizontalDrag = { _, amount -> dragTotal += amount }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BookCoverImage(
                            url = candidateCoverUrl,
                            contentDescription = candidateSource,
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Text(
                            text = if (candidateCoverUrl.isBlank()) stringResource(R.string.metadata_no_cover) else candidateSource,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (totalCandidates > 1) {
                            Text(
                                text = stringResource(R.string.metadata_swipe_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataTextDiffCard(
    title: String,
    beforeText: String,
    emptyBeforeText: String,
    candidateText: String,
    manualDraft: String?,
    isEditing: Boolean,
    candidateIndex: Int,
    totalCandidates: Int,
    singleLine: Boolean,
    useNewValue: Boolean,
    onSelectSide: (Boolean) -> Unit,
    onStepCandidate: (Int) -> Unit,
    onStartEdit: () -> Unit,
    onChangeDraft: (String) -> Unit,
    onFinishEdit: () -> Unit
) {
    val afterDisplay = (manualDraft ?: candidateText).ifBlank { emptyBeforeText }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiffFieldHeader(
                title = title,
                candidateIndex = candidateIndex,
                totalCandidates = totalCandidates,
                onStepCandidate = onStepCandidate
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiffOptionColumn(
                    header = stringResource(R.string.metadata_before_label),
                    selected = !useNewValue,
                    onClick = { onSelectSide(false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = beforeText.ifBlank { emptyBeforeText },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (singleLine) 3 else 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DiffOptionColumn(
                    header = stringResource(R.string.metadata_after_label),
                    selected = useNewValue,
                    onClick = { onSelectSide(true) },
                    modifier = Modifier.weight(1f)
                ) {
                    if (isEditing) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = manualDraft ?: candidateText,
                                onValueChange = onChangeDraft,
                                singleLine = singleLine,
                                minLines = if (singleLine) 1 else 2,
                                maxLines = if (singleLine) 2 else 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(
                                onClick = onFinishEdit,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(stringResource(R.string.action_done))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = afterDisplay,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = if (singleLine) 3 else 5,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (manualDraft != null) {
                                    Text(
                                        text = stringResource(R.string.metadata_edited_tag),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(
                                onClick = onStartEdit,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_metadata),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffFieldHeader(
    title: String,
    candidateIndex: Int,
    totalCandidates: Int,
    onStepCandidate: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (totalCandidates > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onStepCandidate(-1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                }
                Text(
                    text = "${candidateIndex + 1}/$totalCandidates",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { onStepCandidate(1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun DiffOptionColumn(
    header: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            content()
        }
    }
}

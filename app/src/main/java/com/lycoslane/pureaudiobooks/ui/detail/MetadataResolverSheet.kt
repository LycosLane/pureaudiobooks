package com.lycoslane.pureaudiobooks.ui.detail

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.local.ConflictSummary
import com.lycoslane.pureaudiobooks.ui.common.pinchToZoomTextScale
import com.lycoslane.pureaudiobooks.core.network.OnlineBookMatch
import com.lycoslane.pureaudiobooks.ui.common.pinchToZoomTextScale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MetadataResolverSheet(
    book: AudiobookEntity,
    coverFocusMode: Boolean,
    onlineLookupEnabled: Boolean,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    onlineResults: List<OnlineBookMatch>,
    isSearchingOnline: Boolean,
    onEnableOnlineLookup: (String) -> Unit,
    onSearchOnline: (String) -> Unit,
    onSplitByAlbum: () -> Unit,
    onSave: (title: String, author: String, description: String, onlineCoverUrl: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val summary = remember(book.conflictSummaryJson) {
        ConflictSummary.fromJson(book.conflictSummaryJson)
    }

    var manualTitle by remember(book.id) { mutableStateOf(book.title) }
    var manualAuthor by remember(book.id) { mutableStateOf(book.author) }
    var manualDescription by remember(book.id) { mutableStateOf(book.description) }
    var searchQuery by remember(book.id) { mutableStateOf(book.title.ifBlank { book.folderName }) }

    var selectedCandidateIdx by remember(book.id) { mutableIntStateOf(0) }
    var coverIdx by remember(book.id) { mutableIntStateOf(0) }
    var titleIdx by remember(book.id) { mutableIntStateOf(0) }
    var authorIdx by remember(book.id) { mutableIntStateOf(0) }
    var descIdx by remember(book.id) { mutableIntStateOf(0) }

    var useNewCover by remember(book.id, coverFocusMode) { mutableStateOf(coverFocusMode) }
    var useNewTitle by remember(book.id, coverFocusMode) { mutableStateOf(!coverFocusMode) }
    var useNewAuthor by remember(book.id, coverFocusMode) { mutableStateOf(!coverFocusMode) }
    var useNewDesc by remember(book.id, coverFocusMode) { mutableStateOf(!coverFocusMode) }

    var diffDraftTitle by remember(book.id) { mutableStateOf<String?>(null) }
    var diffDraftAuthor by remember(book.id) { mutableStateOf<String?>(null) }
    var diffDraftDesc by remember(book.id) { mutableStateOf<String?>(null) }
    var editingDiffField by remember(book.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(book.id, coverFocusMode, onlineLookupEnabled) {
        if (coverFocusMode && onlineLookupEnabled && onlineResults.isEmpty() && searchQuery.isNotBlank()) {
            onSearchOnline(searchQuery)
        }
    }

    LaunchedEffect(onlineResults) {
        if (onlineResults.isNotEmpty()) {
            selectedCandidateIdx = 0
            coverIdx = 0
            titleIdx = 0
            authorIdx = 0
            descIdx = 0
            diffDraftTitle = null
            diffDraftAuthor = null
            diffDraftDesc = null
            editingDiffField = null
            if (coverFocusMode) {
                useNewCover = onlineResults.first().coverUrl.isNotBlank()
                useNewTitle = false
                useNewAuthor = false
                useNewDesc = false
            } else {
                val first = onlineResults.first()
                useNewCover = first.coverUrl.isNotBlank()
                useNewTitle = first.title.isNotBlank()
                useNewAuthor = first.author.isNotBlank()
                useNewDesc = first.description.isNotBlank()
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pinchToZoomTextScale(
                    textScale = textScale,
                    onTextScaleChange = onTextScaleChange
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.metadata_resolver_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Quick title & author suggestions from folder/ID3 tags
            val titleOptions = buildList {
                if (book.folderName.isNotBlank()) {
                    add(book.folderName to stringResource(R.string.source_folder_name))
                }
                summary?.albumCandidates?.forEach { cand ->
                    if (cand.name.isNotBlank() && cand.name != book.folderName) {
                        add(cand.name to stringResource(R.string.source_album_tag_count, cand.trackCount))
                    }
                }
            }.distinctBy { it.first }

            if (!coverFocusMode && titleOptions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.choose_title_source),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    titleOptions.forEach { (candidate, sourceLabel) ->
                        FilterChip(
                            selected = manualTitle == candidate,
                            onClick = { manualTitle = candidate },
                            label = {
                                Text(
                                    text = "$candidate ($sourceLabel)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            val authorOptions = buildList {
                if (book.parentFolderName.isNotBlank()) add(book.parentFolderName)
                summary?.artistCandidates?.forEach { if (it.isNotBlank()) add(it) }
            }.distinct()

            if (!coverFocusMode && authorOptions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.choose_author_source),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    authorOptions.forEach { cand ->
                        FilterChip(
                            selected = manualAuthor == cand,
                            onClick = { manualAuthor = cand },
                            label = { Text(cand, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }

            if (summary?.hasMultipleAlbums == true) {
                OutlinedButton(
                    onClick = onSplitByAlbum,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.split_by_album_action, summary.albumCandidates.size))
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.online_lookup_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (!onlineLookupEnabled) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_online_lookup_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { onEnableOnlineLookup(searchQuery) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.enable_online_lookup_action))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.search_query_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onSearchOnline(searchQuery) },
                        enabled = !isSearchingOnline && searchQuery.isNotBlank()
                    ) {
                        if (isSearchingOnline) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Quick search chips (Title / Author / Folder)
                val queryChips = buildList {
                    if (book.title.isNotBlank()) add(stringResource(R.string.field_title) to book.title)
                    if (book.author.isNotBlank()) add(stringResource(R.string.field_author) to book.author)
                    if (book.folderName.isNotBlank() && book.folderName != book.title) {
                        add(stringResource(R.string.source_folder_name) to book.folderName)
                    }
                }
                if (queryChips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        queryChips.forEach { (chipLabel, qVal) ->
                            FilterChip(
                                selected = searchQuery.trim().equals(qVal.trim(), ignoreCase = true),
                                onClick = {
                                    searchQuery = qVal
                                    onSearchOnline(qVal)
                                },
                                label = { Text("$chipLabel: $qVal", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }

            if (onlineResults.isNotEmpty()) {
                val safeSize = onlineResults.size
                val safeCovIdx = coverIdx.coerceIn(0, safeSize - 1)
                val safeTitIdx = titleIdx.coerceIn(0, safeSize - 1)
                val safeAuthIdx = authorIdx.coerceIn(0, safeSize - 1)
                val safeDescIdx = descIdx.coerceIn(0, safeSize - 1)

                val covMatch = onlineResults[safeCovIdx]
                val titMatch = onlineResults[safeTitIdx]
                val authMatch = onlineResults[safeAuthIdx]
                val descMatch = onlineResults[safeDescIdx]

                MetadataCandidateStrip(
                    candidates = onlineResults,
                    selectedIndex = selectedCandidateIdx.coerceIn(0, safeSize - 1),
                    onSelectCandidate = { idx ->
                        selectedCandidateIdx = idx
                        coverIdx = idx
                        titleIdx = idx
                        authorIdx = idx
                        descIdx = idx
                        diffDraftTitle = null
                        diffDraftAuthor = null
                        diffDraftDesc = null
                        editingDiffField = null
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            useNewCover = true
                            useNewTitle = true
                            useNewAuthor = true
                            useNewDesc = true
                            editingDiffField = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.metadata_select_all_new)) }
                    TextButton(
                        onClick = {
                            useNewCover = true
                            useNewTitle = false
                            useNewAuthor = false
                            useNewDesc = false
                            editingDiffField = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.metadata_select_cover_only)) }
                    TextButton(
                        onClick = {
                            useNewCover = false
                            useNewTitle = false
                            useNewAuthor = false
                            useNewDesc = false
                            editingDiffField = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.metadata_keep_all)) }
                }

                MetadataCoverDiffCard(
                    currentCoverUrl = book.artworkUrl,
                    candidateCoverUrl = covMatch.coverUrl,
                    candidateSource = covMatch.source,
                    candidateIndex = safeCovIdx,
                    totalCandidates = safeSize,
                    useNewCover = useNewCover,
                    onSelectSide = { useNewCover = it },
                    onStepCandidate = { delta ->
                        coverIdx = (safeCovIdx + delta + safeSize) % safeSize
                        useNewCover = onlineResults[coverIdx].coverUrl.isNotBlank()
                    }
                )

                MetadataTextDiffCard(
                    title = stringResource(R.string.field_title),
                    beforeText = book.title,
                    emptyBeforeText = book.folderName,
                    candidateText = titMatch.title,
                    manualDraft = diffDraftTitle,
                    isEditing = editingDiffField == "title",
                    candidateIndex = safeTitIdx,
                    totalCandidates = safeSize,
                    singleLine = true,
                    useNewValue = useNewTitle,
                    onSelectSide = { useNewTitle = it },
                    onStepCandidate = { delta ->
                        titleIdx = (safeTitIdx + delta + safeSize) % safeSize
                        diffDraftTitle = null
                        useNewTitle = true
                    },
                    onStartEdit = {
                        if (diffDraftTitle == null) diffDraftTitle = titMatch.title
                        useNewTitle = true
                        editingDiffField = "title"
                    },
                    onChangeDraft = { diffDraftTitle = it },
                    onFinishEdit = { editingDiffField = null }
                )

                MetadataTextDiffCard(
                    title = stringResource(R.string.field_author),
                    beforeText = book.author,
                    emptyBeforeText = stringResource(R.string.metadata_empty_value),
                    candidateText = authMatch.author,
                    manualDraft = diffDraftAuthor,
                    isEditing = editingDiffField == "author",
                    candidateIndex = safeAuthIdx,
                    totalCandidates = safeSize,
                    singleLine = true,
                    useNewValue = useNewAuthor,
                    onSelectSide = { useNewAuthor = it },
                    onStepCandidate = { delta ->
                        authorIdx = (safeAuthIdx + delta + safeSize) % safeSize
                        diffDraftAuthor = null
                        useNewAuthor = true
                    },
                    onStartEdit = {
                        if (diffDraftAuthor == null) diffDraftAuthor = authMatch.author
                        useNewAuthor = true
                        editingDiffField = "author"
                    },
                    onChangeDraft = { diffDraftAuthor = it },
                    onFinishEdit = { editingDiffField = null }
                )

                MetadataTextDiffCard(
                    title = stringResource(R.string.field_description),
                    beforeText = book.description,
                    emptyBeforeText = stringResource(R.string.metadata_empty_value),
                    candidateText = descMatch.description,
                    manualDraft = diffDraftDesc,
                    isEditing = editingDiffField == "desc",
                    candidateIndex = safeDescIdx,
                    totalCandidates = safeSize,
                    singleLine = false,
                    useNewValue = useNewDesc,
                    onSelectSide = { useNewDesc = it },
                    onStepCandidate = { delta ->
                        descIdx = (safeDescIdx + delta + safeSize) % safeSize
                        diffDraftDesc = null
                        useNewDesc = true
                    },
                    onStartEdit = {
                        if (diffDraftDesc == null) diffDraftDesc = descMatch.description
                        useNewDesc = true
                        editingDiffField = "desc"
                    },
                    onChangeDraft = { diffDraftDesc = it },
                    onFinishEdit = { editingDiffField = null }
                )

                Spacer(Modifier.height(4.dp))
                val newFieldCount = listOf(
                    useNewCover && covMatch.coverUrl.isNotBlank(),
                    useNewTitle,
                    useNewAuthor,
                    useNewDesc
                ).count { it }
                Button(
                    onClick = {
                        val finalTitle = if (useNewTitle) {
                            (diffDraftTitle ?: titMatch.title).ifBlank { book.title }
                        } else {
                            book.title
                        }
                        val finalAuthor = if (useNewAuthor) {
                            diffDraftAuthor ?: authMatch.author
                        } else {
                            book.author
                        }
                        val finalDesc = if (useNewDesc) {
                            diffDraftDesc ?: descMatch.description
                        } else {
                            book.description
                        }
                        val finalCover = if (useNewCover) covMatch.coverUrl.takeIf { it.isNotBlank() } else null
                        onSave(finalTitle, finalAuthor, finalDesc, finalCover)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (newFieldCount > 0) {
                            stringResource(R.string.metadata_apply_diff_count, newFieldCount)
                        } else {
                            stringResource(R.string.save_and_write_sidecar)
                        }
                    )
                }
            } else {
                // Offline / no search results yet: manual metadata fields
                OutlinedTextField(
                    value = manualTitle,
                    onValueChange = { manualTitle = it },
                    label = { Text(stringResource(R.string.field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualAuthor,
                    onValueChange = { manualAuthor = it },
                    label = { Text(stringResource(R.string.field_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualDescription,
                    onValueChange = { manualDescription = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { onSave(manualTitle, manualAuthor, manualDescription, null) },
                    enabled = manualTitle.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save_and_write_sidecar))
                }
            }
        }
    }
}

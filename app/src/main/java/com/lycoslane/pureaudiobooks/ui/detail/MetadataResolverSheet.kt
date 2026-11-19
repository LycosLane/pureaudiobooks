package com.lycoslane.pureaudiobooks.ui.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.local.ConflictSummary
import com.lycoslane.pureaudiobooks.core.network.OnlineBookMatch
import com.lycoslane.pureaudiobooks.ui.common.BookCoverImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MetadataResolverSheet(
    book: AudiobookEntity,
    onlineLookupEnabled: Boolean,
    onlineResults: List<OnlineBookMatch>,
    isSearchingOnline: Boolean,
    onSearchOnline: (String) -> Unit,
    onSplitByAlbum: () -> Unit,
    onSave: (title: String, author: String, description: String, onlineCoverUrl: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val summary = remember(book.conflictSummaryJson) {
        ConflictSummary.fromJson(book.conflictSummaryJson)
    }

    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var description by remember(book.id) { mutableStateOf(book.description) }
    var selectedOnlineCover by remember(book.id) { mutableStateOf<String?>(null) }
    var searchQuery by remember(book.id) { mutableStateOf(book.title.ifBlank { book.folderName }) }

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
                text = stringResource(R.string.metadata_resolver_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Quick title suggestions (Folder name vs detected Album tags)
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

            if (titleOptions.isNotEmpty()) {
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
                            selected = title == candidate,
                            onClick = { title = candidate },
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

            // Quick author suggestions (Parent folder vs detected Artist tags)
            val authorOptions = buildList {
                if (book.parentFolderName.isNotBlank()) add(book.parentFolderName)
                summary?.artistCandidates?.forEach { if (it.isNotBlank()) add(it) }
            }.distinct()

            if (authorOptions.isNotEmpty()) {
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
                            selected = author == cand,
                            onClick = { author = cand },
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

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.field_author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.field_description)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            if (onlineLookupEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = stringResource(R.string.online_lookup_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
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

                onlineResults.forEach { match ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                title = match.title
                                if (match.author.isNotBlank()) author = match.author
                                if (match.description.isNotBlank()) description = match.description
                                if (match.coverUrl.isNotBlank()) selectedOnlineCover = match.coverUrl
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BookCoverImage(
                                url = match.coverUrl,
                                contentDescription = match.title,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = match.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${match.author} · ${match.source}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onSave(title, author, description, selectedOnlineCover) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save_and_write_sidecar))
            }
        }
    }
}

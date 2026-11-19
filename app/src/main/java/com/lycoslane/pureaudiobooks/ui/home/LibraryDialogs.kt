package com.lycoslane.pureaudiobooks.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks

@Composable
fun BookActionsDialog(
    target: AudiobookWithTracks,
    onDismiss: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onMarkFinished: (Long, Boolean) -> Unit,
    onResetProgress: (Long) -> Unit,
    onSetHidden: (Long, Boolean) -> Unit,
    onMoveToTrash: (Long) -> Unit
) {
    val book = target.audiobook
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenBook(book.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.open_book)) }

                TextButton(
                    onClick = {
                        onDismiss()
                        onMarkFinished(book.id, !book.isFinished)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (book.isFinished) R.string.mark_unfinished else R.string.mark_finished
                        )
                    )
                }

                TextButton(
                    onClick = {
                        onDismiss()
                        onResetProgress(book.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.reset_progress)) }

                TextButton(
                    onClick = {
                        onDismiss()
                        onSetHidden(book.id, !book.isHidden)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (book.isHidden) R.string.unhide_book else R.string.hide_book
                        )
                    )
                }

                TextButton(
                    onClick = {
                        onDismiss()
                        onMoveToTrash(book.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.move_to_trash),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun ConfirmPermanentDeleteDialog(
    target: AudiobookWithTracks,
    onDismiss: () -> Unit,
    onConfirmDelete: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_permanently)) },
        text = { Text(stringResource(R.string.delete_permanently_confirm, target.audiobook.title)) },
        confirmButton = {
            TextButton(onClick = {
                val id = target.audiobook.id
                onDismiss()
                onConfirmDelete(id)
            }) {
                Text(stringResource(R.string.delete_permanently), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

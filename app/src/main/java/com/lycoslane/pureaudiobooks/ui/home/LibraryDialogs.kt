package com.lycoslane.pureaudiobooks.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lycoslane.pureaudiobooks.R
import com.lycoslane.pureaudiobooks.core.database.AudiobookWithTracks

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

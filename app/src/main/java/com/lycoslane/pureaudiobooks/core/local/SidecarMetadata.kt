package com.lycoslane.pureaudiobooks.core.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

data class SidecarInfo(
    val title: String = "",
    val author: String = "",
    val description: String = ""
) {
    val isEmpty: Boolean
        get() = title.isBlank() && author.isBlank() && description.isBlank()
}

object SidecarMetadata {

    const val INFO_FILENAME = "info.txt"
    const val COVER_FILENAME = "cover.jpg"
    private const val MAX_INFO_BYTES = 256 * 1024 // 256 KB

    fun parseInfoText(raw: String): SidecarInfo {
        if (raw.isBlank()) return SidecarInfo()
        var title = ""
        var author = ""
        val descLines = mutableListOf<String>()
        var inDescription = false

        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            val lower = line.lowercase()
            when {
                lower.startsWith("title:") || lower.startsWith("titel:") -> {
                    inDescription = false
                    title = line.substringAfter(':').trim()
                }
                lower.startsWith("author:") || lower.startsWith("autor:") -> {
                    inDescription = false
                    author = line.substringAfter(':').trim()
                }
                lower.startsWith("description:") || lower.startsWith("beschreibung:") -> {
                    inDescription = true
                    val first = line.substringAfter(':').trim()
                    if (first.isNotEmpty()) descLines += first
                }
                inDescription -> {
                    descLines += line
                }
            }
        }
        return SidecarInfo(
            title = title,
            author = author,
            description = descLines.joinToString("\n").trim()
        )
    }

    fun formatInfoText(info: SidecarInfo): String = buildString {
        append("Title: ").append(info.title.replace("\n", " ").trim()).append('\n')
        append("Author: ").append(info.author.replace("\n", " ").trim()).append('\n')
        if (info.description.isNotBlank()) {
            append("Description: ").append(info.description.trim()).append('\n')
        }
    }

    fun readInfoFromUri(context: Context, docUri: Uri): SidecarInfo? {
        return runCatching {
            context.contentResolver.openInputStream(docUri)?.use { stream ->
                val buffer = ByteArray(MAX_INFO_BYTES + 1)
                var total = 0
                while (total < buffer.size) {
                    val read = stream.read(buffer, total, buffer.size - total)
                    if (read <= 0) break
                    total += read
                }
                if (total > MAX_INFO_BYTES) throw IOException("info.txt too large")
                parseInfoText(buffer.copyOf(total).toString(Charsets.UTF_8))
            }
        }.getOrNull()
    }

    fun writeSidecarToFolder(
        context: Context,
        treeUriString: String,
        folderDocId: String,
        info: SidecarInfo,
        coverBytes: ByteArray?
    ): String? {
        if (treeUriString.isBlank() || folderDocId.isBlank()) return null
        val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return null
        val hasWrite = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }
        if (!hasWrite) return null

        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, folderDocId)
        val existingChildren = querySimpleChildren(context, treeUri, folderDocId)

        // Write info.txt
        runCatching {
            val existingInfoUri = existingChildren[INFO_FILENAME.lowercase()]
            val targetUri = existingInfoUri ?: DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "text/plain",
                INFO_FILENAME
            )
            if (targetUri != null) {
                context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                    out.write(formatInfoText(info).toByteArray(Charsets.UTF_8))
                }
            }
        }

        // Write cover.jpg if provided
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            val coverUri = runCatching {
                val existingCoverUri = existingChildren[COVER_FILENAME.lowercase()]
                val targetUri = existingCoverUri ?: DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    "image/jpeg",
                    COVER_FILENAME
                )
                if (targetUri != null) {
                    context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                        out.write(coverBytes)
                    }
                }
                targetUri?.toString()
            }.getOrNull()
            if (!coverUri.isNullOrBlank()) return coverUri
        }

        return null
    }

    private fun querySimpleChildren(
        context: Context,
        treeUri: Uri,
        parentDocId: String
    ): Map<String, Uri> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        val map = mutableMapOf<String, Uri>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) else null
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    if (!id.isNullOrBlank() && !name.isNullOrBlank()) {
                        map[name.lowercase()] = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    }
                }
            }
        }
        return map
    }
}

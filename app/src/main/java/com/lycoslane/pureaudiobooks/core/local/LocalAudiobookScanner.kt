package com.lycoslane.pureaudiobooks.core.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.lycoslane.pureaudiobooks.core.database.AudiobookEntity
import com.lycoslane.pureaudiobooks.core.database.ChapterTrackEntity
import com.lycoslane.pureaudiobooks.core.database.PureAudiobooksDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object LocalAudiobookScanner {

    private const val TAG = "LocalAudiobookScanner"
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4b", "m4a", "aac", "ogg", "opus", "flac", "wav")
    private val COVER_FILENAMES = listOf(
        "cover.jpg", "cover.jpeg", "cover.png",
        "folder.jpg", "folder.jpeg", "folder.png",
        "artwork.jpg", "artwork.jpeg", "artwork.png"
    )
    private val DISC_SUBFOLDER_REGEX = Regex(
        "^(cd|disc|disk|part|teil|volume|vol)[\\s._-]*\\d+$",
        RegexOption.IGNORE_CASE
    )

    private data class DocEntry(
        val docId: String,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
        val lastModifiedMs: Long,
        val uri: Uri,
        val relativeSortPath: String = name
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private data class ScannedAudioMeta(
        val title: String,
        val album: String,
        val artist: String,
        val trackNumber: Int,
        val durationMs: Long
    )

    private data class BookFolderCandidate(
        val folderDocId: String,
        val folderName: String,
        val parentFolderName: String,
        val directChildren: List<DocEntry>,
        val audioFiles: List<DocEntry>
    )

    fun hasWritePermission(context: Context, treeUriString: String): Boolean {
        if (treeUriString.isBlank()) return false
        val target = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == target && it.isWritePermission
        }
    }

    fun deleteLocalDocument(context: Context, docUriString: String): Boolean {
        if (!docUriString.startsWith("content://")) return false
        return runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(docUriString))
        }.onFailure {
            Log.w(TAG, "Failed to delete local document $docUriString", it)
        }.getOrDefault(false)
    }

    suspend fun syncLocalFolder(
        context: Context,
        dao: PureAudiobooksDao,
        treeUriString: String,
        defaultSpeed: Float = 1.0f,
        defaultSkipSilence: Boolean = false,
        defaultSilenceIntensity: Int = 1
    ): Int = withContext(Dispatchers.IO) {
        if (treeUriString.isBlank()) {
            dao.getAllAudiobooksOnce().forEach { dao.deleteAudiobook(it.id) }
            return@withContext 0
        }

        val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return@withContext 0
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext 0

        val rootName = queryDocumentName(context, treeUri, rootDocId).orEmpty()
        val candidates = mutableListOf<BookFolderCandidate>()
        val scanComplete = collectBookFolders(
            context = context,
            treeUri = treeUri,
            folderDocId = rootDocId,
            folderName = rootName.ifBlank { "Audiobooks" },
            parentFolderName = "",
            depth = 0,
            out = candidates
        )
        if (!scanComplete) {
            Log.w(TAG, "Aborting library sync cleanup due to SAF query error for $treeUriString")
            return@withContext 0
        }

        val discoveredKeys = mutableSetOf<String>()
        var syncedCount = 0

        for (candidate in candidates) {
            if (candidate.audioFiles.isEmpty()) continue
            val existingForFolder = dao.getAudiobooksByFolderDocId(candidate.folderDocId)
            val isAlreadySplit = existingForFolder.any { it.isSplitByAlbum }

            val sortedAudio = candidate.audioFiles.sortedWith { a, b ->
                NaturalOrderComparator.compare(a.relativeSortPath, b.relativeSortPath)
            }

            // Read sidecar info.txt if present
            val infoDoc = candidate.directChildren.firstOrNull {
                !it.isDirectory && it.name.equals(SidecarMetadata.INFO_FILENAME, ignoreCase = true)
            }
            val sidecarInfo = infoDoc?.let { SidecarMetadata.readInfoFromUri(context, it.uri) }

            val coverUri = findCoverUri(context, candidate.folderDocId, candidate.directChildren, sortedAudio)

            // Extract metadata for all audio files in this folder
            val metaByDocId = HashMap<String, ScannedAudioMeta>(sortedAudio.size)
            for (doc in sortedAudio) {
                metaByDocId[doc.docId] = extractAudioMeta(context, doc)
            }

            if (isAlreadySplit) {
                val grouped = sortedAudio.groupBy { doc ->
                    metaByDocId[doc.docId]?.album?.takeIf { it.isNotBlank() } ?: candidate.folderName
                }
                for ((albumName, groupDocs) in grouped) {
                    val folderKey = "local://${candidate.folderDocId}::$albumName"
                    discoveredKeys += folderKey
                    upsertAudiobook(
                        context = context,
                        dao = dao,
                        folderKey = folderKey,
                        folderDocId = candidate.folderDocId,
                        folderUri = treeUriString,
                        folderName = candidate.folderName,
                        parentFolderName = candidate.parentFolderName,
                        audioFiles = groupDocs,
                        metaByDocId = metaByDocId,
                        sidecarInfo = null,
                        coverUri = coverUri,
                        forcedTitle = albumName,
                        isSplitByAlbum = true,
                        defaultSpeed = defaultSpeed,
                        defaultSkipSilence = defaultSkipSilence,
                        defaultSilenceIntensity = defaultSilenceIntensity
                    )
                    syncedCount++
                }
            } else {
                val folderKey = "local://${candidate.folderDocId}"
                discoveredKeys += folderKey
                upsertAudiobook(
                    context = context,
                    dao = dao,
                    folderKey = folderKey,
                    folderDocId = candidate.folderDocId,
                    folderUri = treeUriString,
                    folderName = candidate.folderName,
                    parentFolderName = candidate.parentFolderName,
                    audioFiles = sortedAudio,
                    metaByDocId = metaByDocId,
                    sidecarInfo = sidecarInfo,
                    coverUri = coverUri,
                    forcedTitle = null,
                    isSplitByAlbum = false,
                    defaultSpeed = defaultSpeed,
                    defaultSkipSilence = defaultSkipSilence,
                    defaultSilenceIntensity = defaultSilenceIntensity
                )
                syncedCount++
            }
        }

        // Remove stale entries no longer present in the scanned folder (unless in Trash? Keep cleanup consistent)
        dao.getAllAudiobooksOnce()
            .filter { it.folderKey !in discoveredKeys }
            .forEach { dao.deleteAudiobook(it.id) }

        syncedCount
    }

    private fun collectBookFolders(
        context: Context,
        treeUri: Uri,
        folderDocId: String,
        folderName: String,
        parentFolderName: String,
        depth: Int,
        out: MutableList<BookFolderCandidate>
    ): Boolean {
        if (depth > 3) return true
        val children = queryChildren(context, treeUri, folderDocId) ?: return false
        val directAudio = children.filter { !it.isDirectory && isAudioFile(it) }
        val subdirs = children.filter { it.isDirectory }

        if (depth > 0) {
            // Check if subdirs are CD1/CD2 disc folders
            val discSubdirs = subdirs.filter { DISC_SUBFOLDER_REGEX.matches(it.name.trim()) }
            val otherSubdirs = subdirs.filter { !DISC_SUBFOLDER_REGEX.matches(it.name.trim()) }

            val collectedAudio = directAudio.toMutableList()
            for (discDir in discSubdirs) {
                val discChildren = queryChildren(context, treeUri, discDir.docId) ?: return false
                discChildren
                    .filter { !it.isDirectory && isAudioFile(it) }
                    .forEach { entry ->
                        collectedAudio += entry.copy(relativeSortPath = "${discDir.name}/${entry.name}")
                    }
            }

            if (collectedAudio.isNotEmpty()) {
                out += BookFolderCandidate(
                    folderDocId = folderDocId,
                    folderName = folderName,
                    parentFolderName = parentFolderName,
                    directChildren = children,
                    audioFiles = collectedAudio
                )
            }

            for (sub in otherSubdirs) {
                if (!collectBookFolders(
                        context = context,
                        treeUri = treeUri,
                        folderDocId = sub.docId,
                        folderName = sub.name,
                        parentFolderName = folderName,
                        depth = depth + 1,
                        out = out
                    )
                ) {
                    return false
                }
            }
        } else {
            // Depth 0 (root): any audio files directly in root become one book, plus recurse into subdirs
            if (directAudio.isNotEmpty()) {
                out += BookFolderCandidate(
                    folderDocId = folderDocId,
                    folderName = folderName,
                    parentFolderName = "",
                    directChildren = children,
                    audioFiles = directAudio
                )
            }
            for (sub in subdirs) {
                if (!collectBookFolders(
                        context = context,
                        treeUri = treeUri,
                        folderDocId = sub.docId,
                        folderName = sub.name,
                        parentFolderName = "",
                        depth = 1,
                        out = out
                    )
                ) {
                    return false
                }
            }
        }
        return true
    }

    fun computeContentFingerprint(
        trackCount: Int,
        sizesAndDurations: List<Pair<Long, Long>>,
        firstFileHeaderHash: String = ""
    ): String {
        if (trackCount <= 0 || sizesAndDurations.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("v1|").append(trackCount).append('|')
        sizesAndDurations.forEach { (size, dur) ->
            sb.append(size).append(':').append(dur).append(';')
        }
        if (firstFileHeaderHash.isNotBlank()) {
            sb.append('|').append(firstFileHeaderHash)
        }
        return sha256(sb.toString())
    }

    private fun readFirstFileHeaderHash(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (total < buf.size) {
                    val n = input.read(buf, total, buf.size - total)
                    if (n <= 0) break
                    total += n
                }
                if (total <= 0) return@use ""
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(buf, 0, total)
                digest.digest().joinToString("") { "%02x".format(it) }
            }.orEmpty()
        }.onFailure {
            Log.w(TAG, "Failed to read file header hash for $uri", it)
        }.getOrDefault("")
    }

    private suspend fun upsertAudiobook(
        context: Context,
        dao: PureAudiobooksDao,
        folderKey: String,
        folderDocId: String,
        folderUri: String,
        folderName: String,
        parentFolderName: String,
        audioFiles: List<DocEntry>,
        metaByDocId: Map<String, ScannedAudioMeta>,
        sidecarInfo: SidecarInfo?,
        coverUri: String,
        forcedTitle: String?,
        isSplitByAlbum: Boolean,
        defaultSpeed: Float = 1.0f,
        defaultSkipSilence: Boolean = false,
        defaultSilenceIntensity: Int = 1
    ) {
        val now = System.currentTimeMillis()
        val albumTags = audioFiles.map { metaByDocId[it.docId]?.album.orEmpty() }
        val artistTags = audioFiles.map { metaByDocId[it.docId]?.artist.orEmpty() }

        val analysis = TagConflictAnalyzer.analyze(
            folderName = folderName,
            parentFolderName = parentFolderName,
            albumTags = albumTags,
            artistTags = artistTags,
            sidecar = sidecarInfo
        )

        val sizesAndDurations = audioFiles.map { doc ->
            doc.sizeBytes to (metaByDocId[doc.docId]?.durationMs ?: 0L)
        }
        val firstHeaderHash = audioFiles.firstOrNull()?.let { readFirstFileHeaderHash(context, it.uri) }.orEmpty()
        val fingerprint = computeContentFingerprint(audioFiles.size, sizesAndDurations, firstHeaderHash)

        val existingByKey = dao.getAudiobookByFolderKey(folderKey)
        val matchedByFingerprint = if (existingByKey == null && !isSplitByAlbum && fingerprint.isNotBlank()) {
            dao.getAudiobookByFingerprint(fingerprint)
        } else null
        val existing = existingByKey ?: matchedByFingerprint

        val audiobookId = if (existing == null) {
            dao.insertAudiobook(
                AudiobookEntity(
                    folderKey = folderKey,
                    folderDocId = folderDocId,
                    folderUri = folderUri,
                    folderName = folderName,
                    parentFolderName = parentFolderName,
                    title = forcedTitle ?: analysis.suggestedTitle,
                    author = sidecarInfo?.author?.ifBlank { analysis.suggestedAuthor } ?: analysis.suggestedAuthor,
                    description = sidecarInfo?.description.orEmpty(),
                    artworkUrl = coverUri,
                    playbackSpeed = defaultSpeed.coerceIn(0.50f, 3.00f),
                    skipSilence = defaultSkipSilence,
                    silenceIntensity = defaultSilenceIntensity.coerceIn(0, 2),
                    hasTagConflict = if (forcedTitle != null) false else analysis.hasConflict,
                    conflictSummaryJson = analysis.summary.toJson(),
                    metadataLockedByUser = sidecarInfo != null && sidecarInfo.title.isNotBlank(),
                    isSplitByAlbum = isSplitByAlbum,
                    lastScannedMs = now,
                    contentFingerprint = fingerprint
                )
            )
        } else {
            if (matchedByFingerprint != null || existing.contentFingerprint != fingerprint) {
                dao.updateAudiobookFolderLocation(
                    audiobookId = existing.id,
                    folderKey = folderKey,
                    folderDocId = folderDocId,
                    folderUri = folderUri,
                    folderName = folderName,
                    parentFolderName = parentFolderName,
                    contentFingerprint = fingerprint
                )
            }
            val keepUserMeta = existing.metadataLockedByUser && (sidecarInfo == null || sidecarInfo.isEmpty)
            val nextTitle = when {
                sidecarInfo != null && sidecarInfo.title.isNotBlank() -> sidecarInfo.title
                keepUserMeta -> existing.title
                forcedTitle != null -> forcedTitle
                else -> analysis.suggestedTitle
            }
            val nextAuthor = when {
                sidecarInfo != null && sidecarInfo.author.isNotBlank() -> sidecarInfo.author
                keepUserMeta -> existing.author
                else -> analysis.suggestedAuthor.ifBlank { existing.author }
            }
            val nextDesc = when {
                sidecarInfo != null && sidecarInfo.description.isNotBlank() -> sidecarInfo.description
                else -> existing.description
            }
            val hasExistingValidArt = existing.artworkUrl.isNotBlank() && (
                existing.artworkUrl.startsWith("content://") ||
                    File(existing.artworkUrl.removePrefix("file://")).exists()
                )
            val nextArt = if (existing.metadataLockedByUser && hasExistingValidArt) {
                existing.artworkUrl
            } else {
                coverUri.ifBlank { existing.artworkUrl }
            }
            val nextConflict = when {
                forcedTitle != null -> false
                sidecarInfo != null && sidecarInfo.title.isNotBlank() -> false
                keepUserMeta -> false
                else -> analysis.hasConflict
            }

            dao.updateAudiobookMetadata(
                audiobookId = existing.id,
                title = nextTitle,
                author = nextAuthor,
                description = nextDesc,
                artworkUrl = nextArt,
                hasTagConflict = nextConflict,
                conflictSummaryJson = analysis.summary.toJson(),
                metadataLockedByUser = existing.metadataLockedByUser || (sidecarInfo != null && sidecarInfo.title.isNotBlank()),
                lastScannedMs = now
            )
            existing.id
        }

        if (audiobookId <= 0L) return

        val existingTracks = dao.getTracksForAudiobook(audiobookId)
        val existingByGuid = existingTracks.associateBy { it.guid }
        val existingByOrder = existingTracks.associateBy { it.sortOrder }
        val claimedTrackIds = HashSet<Long>()
        val toInsert = mutableListOf<ChapterTrackEntity>()

        audioFiles.forEachIndexed { index, doc ->
            val guid = doc.docId
            val meta = metaByDocId[doc.docId]
            val cleanFileTitle = doc.name.substringBeforeLast('.')
            val trackTitle = meta?.title?.takeIf { it.isNotBlank() } ?: cleanFileTitle
            val existingTrack = existingByGuid[guid]
                ?: existingTracks.firstOrNull {
                    it.id !in claimedTrackIds &&
                        it.fileSizeBytes == doc.sizeBytes &&
                        (meta?.durationMs == 0L || it.durationMs == meta?.durationMs)
                }
                ?: existingByOrder[index]?.takeIf { it.id !in claimedTrackIds && matchedByFingerprint != null }

            if (existingTrack != null) {
                claimedTrackIds += existingTrack.id
                val uriStr = doc.uri.toString()
                if (existingTrack.guid != guid) {
                    dao.updateTrackIdentity(
                        trackId = existingTrack.id,
                        guid = guid,
                        fileUri = uriStr,
                        sortOrder = index,
                        trackNumber = meta?.trackNumber ?: existingTrack.trackNumber,
                        title = trackTitle,
                        durationMs = meta?.durationMs?.takeIf { it > 0L } ?: existingTrack.durationMs,
                        albumTag = meta?.album.orEmpty(),
                        artistTag = meta?.artist.orEmpty()
                    )
                } else if (existingTrack.fileUri != uriStr || existingTrack.sortOrder != index) {
                    dao.updateTrackDetails(
                        trackId = existingTrack.id,
                        fileUri = uriStr,
                        sortOrder = index,
                        trackNumber = meta?.trackNumber ?: existingTrack.trackNumber,
                        title = trackTitle,
                        durationMs = meta?.durationMs?.takeIf { it > 0L } ?: existingTrack.durationMs,
                        albumTag = meta?.album.orEmpty(),
                        artistTag = meta?.artist.orEmpty()
                    )
                }
            } else {
                toInsert += ChapterTrackEntity(
                    audiobookId = audiobookId,
                    guid = guid,
                    title = trackTitle,
                    trackNumber = meta?.trackNumber ?: (index + 1),
                    sortOrder = index,
                    fileUri = doc.uri.toString(),
                    albumTag = meta?.album.orEmpty(),
                    artistTag = meta?.artist.orEmpty(),
                    durationMs = meta?.durationMs ?: 0L,
                    positionMs = 0L,
                    isFinished = false,
                    fileSizeBytes = doc.sizeBytes,
                    lastModifiedMs = doc.lastModifiedMs
                )
            }
        }

        if (toInsert.isNotEmpty()) {
            dao.insertTracks(toInsert)
        }
        existingTracks
            .filter { it.id !in claimedTrackIds }
            .forEach { dao.deleteTrack(it.id) }
    }

    private fun extractAudioMeta(context: Context, doc: DocEntry): ScannedAudioMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, doc.uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
            val artist = (
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                )?.trim().orEmpty()
            val trackRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.trim().orEmpty()
            val trackNum = trackRaw.substringBefore('/').toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ScannedAudioMeta(
                title = title,
                album = album,
                artist = artist,
                trackNumber = trackNum,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract audio metadata for ${doc.uri}", e)
            ScannedAudioMeta("", "", "", 0, 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun findCoverUri(
        context: Context,
        folderDocId: String,
        children: List<DocEntry>,
        audioFiles: List<DocEntry>
    ): String {
        val imageDoc = COVER_FILENAMES.firstNotNullOfOrNull { targetName ->
            children.firstOrNull { !it.isDirectory && it.name.equals(targetName, ignoreCase = true) }
        } ?: children.firstOrNull {
            !it.isDirectory && it.mimeType.startsWith("image/")
        }
        if (imageDoc != null) return imageDoc.uri.toString()

        val firstAudio = audioFiles.firstOrNull() ?: return ""
        return extractEmbeddedCover(context, folderDocId, firstAudio.uri).orEmpty()
    }

    private fun extractEmbeddedCover(context: Context, folderDocId: String, audioUri: Uri): String? {
        val coversDir = File(context.filesDir, "local_covers").apply { mkdirs() }
        val target = File(coversDir, "${sha256(folderDocId)}.jpg")
        if (target.exists() && target.length() > 0L) return target.absolutePath

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, audioUri)
            val bytes = retriever.embeddedPicture ?: return null
            if (bytes.size > 8 * 1024 * 1024) return null
            target.writeBytes(bytes)
            target.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract embedded cover from $audioUri", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryChildren(context: Context, treeUri: Uri, parentDocId: String): List<DocEntry>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val result = mutableListOf<DocEntry>()
        return try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else null
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val mod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                    if (docId.isNullOrBlank() || name.isNullOrBlank()) continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    result += DocEntry(
                        docId = docId,
                        name = name,
                        mimeType = mime ?: "",
                        sizeBytes = size,
                        lastModifiedMs = mod,
                        uri = docUri
                    )
                }
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "SAF queryChildren failed for $parentDocId", e)
            null
        }
    }

    private fun queryDocumentName(context: Context, treeUri: Uri, docId: String): String? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.onFailure {
            Log.w(TAG, "SAF queryDocumentName failed for $docId", it)
        }.getOrNull()
    }

    private fun isAudioFile(entry: DocEntry): Boolean {
        val ext = entry.name.substringAfterLast('.', "").lowercase()
        if (ext in AUDIO_EXTENSIONS) return true
        return entry.mimeType.startsWith("audio/")
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

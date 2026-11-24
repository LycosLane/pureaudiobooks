package com.lycoslane.pureaudiobooks.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class AudiobookWithTracks(
    @Embedded val audiobook: AudiobookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "audiobookId"
    )
    val tracks: List<ChapterTrackEntity>
)

data class TrackWithAudiobook(
    @Embedded val track: ChapterTrackEntity,
    @Relation(
        parentColumn = "audiobookId",
        entityColumn = "id"
    )
    val audiobook: AudiobookEntity
)

@Dao
interface PureAudiobooksDao {

    @Transaction
    @Query("SELECT * FROM audiobooks WHERE deletedAtMs IS NULL ORDER BY lastPlayedAtMs DESC, title COLLATE NOCASE ASC")
    fun observeActiveAudiobooks(): Flow<List<AudiobookWithTracks>>

    @Transaction
    @Query("SELECT * FROM audiobooks WHERE deletedAtMs IS NOT NULL ORDER BY deletedAtMs DESC")
    fun observeTrashedAudiobooks(): Flow<List<AudiobookWithTracks>>

    @Transaction
    @Query("SELECT * FROM audiobooks WHERE id = :audiobookId")
    fun observeAudiobookWithTracks(audiobookId: Long): Flow<AudiobookWithTracks?>

    @Query("SELECT * FROM bookmarks WHERE audiobookId = :audiobookId ORDER BY createdAtMs DESC")
    fun observeBookmarks(audiobookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM audiobooks ORDER BY id ASC")
    suspend fun getAllAudiobooksOnce(): List<AudiobookEntity>

    @Transaction
    @Query("SELECT * FROM audiobooks WHERE deletedAtMs IS NULL AND isHidden = 0 AND isFinished = 0 AND lastPlayedAtMs > 0 ORDER BY lastPlayedAtMs DESC LIMIT 1")
    suspend fun getMostRecentInProgressAudiobook(): AudiobookWithTracks?

    @Query("SELECT * FROM audiobooks WHERE id = :audiobookId")
    suspend fun getAudiobook(audiobookId: Long): AudiobookEntity?

    @Query("SELECT * FROM audiobooks WHERE folderKey = :folderKey LIMIT 1")
    suspend fun getAudiobookByFolderKey(folderKey: String): AudiobookEntity?

    @Query("SELECT * FROM audiobooks WHERE folderDocId = :folderDocId")
    suspend fun getAudiobooksByFolderDocId(folderDocId: String): List<AudiobookEntity>

    @Query("SELECT * FROM audiobooks WHERE contentFingerprint = :fingerprint AND contentFingerprint != '' LIMIT 1")
    suspend fun getAudiobookByFingerprint(fingerprint: String): AudiobookEntity?

    @Query("SELECT * FROM chapter_tracks WHERE audiobookId = :audiobookId ORDER BY sortOrder ASC, trackNumber ASC, title COLLATE NOCASE ASC")
    suspend fun getTracksForAudiobook(audiobookId: Long): List<ChapterTrackEntity>

    @Query("SELECT * FROM chapter_tracks WHERE id = :trackId")
    suspend fun getTrack(trackId: Long): ChapterTrackEntity?

    @Transaction
    @Query("SELECT * FROM chapter_tracks WHERE id = :trackId")
    suspend fun getTrackWithAudiobook(trackId: Long): TrackWithAudiobook?

    @Query("SELECT * FROM bookmarks WHERE audiobookId = :audiobookId ORDER BY createdAtMs DESC")
    suspend fun getBookmarksForAudiobookOnce(audiobookId: Long): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks ORDER BY createdAtMs DESC")
    suspend fun getAllBookmarksOnce(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudiobook(audiobook: AudiobookEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<ChapterTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracksReturningIds(tracks: List<ChapterTrackEntity>): List<Long>

    @Transaction
    suspend fun splitAudiobookTransaction(
        sourceAudiobookId: Long,
        newBooksWithTracks: List<Pair<AudiobookEntity, List<ChapterTrackEntity>>>
    ): Int {
        val oldBookmarks = getBookmarksForAudiobookOnce(sourceAudiobookId)
        val trackMapping = HashMap<Long, Pair<Long, Long>>()
        var created = 0

        for ((bookEntity, clonedTracks) in newBooksWithTracks) {
            val existing = getAudiobookByFolderKey(bookEntity.folderKey)
            val targetBookId = existing?.id ?: insertAudiobook(bookEntity)
            if (targetBookId > 0L) {
                val preparedTracks = clonedTracks.map { it.copy(id = 0L, audiobookId = targetBookId) }
                val insertedIds = insertTracksReturningIds(preparedTracks)
                clonedTracks.forEachIndexed { idx, oldTrack ->
                    val newId = insertedIds.getOrNull(idx) ?: -1L
                    if (newId > 0L) {
                        trackMapping[oldTrack.id] = targetBookId to newId
                    }
                }
                created++
            }
        }

        for (bm in oldBookmarks) {
            val mapped = trackMapping[bm.trackId] ?: continue
            insertBookmark(
                bm.copy(
                    id = 0L,
                    audiobookId = mapped.first,
                    trackId = mapped.second
                )
            )
        }
        deleteAudiobook(sourceAudiobookId)
        return created
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)

    @Query("DELETE FROM bookmarks WHERE trackId = :trackId")
    suspend fun deleteBookmarksForTrack(trackId: Long)

    @Query("DELETE FROM audiobooks WHERE id = :audiobookId")
    suspend fun deleteAudiobook(audiobookId: Long)

    @Query("DELETE FROM chapter_tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: Long)

    @Query(
        """
        UPDATE audiobooks SET
            title = :title,
            author = :author,
            description = :description,
            artworkUrl = :artworkUrl,
            hasTagConflict = :hasTagConflict,
            conflictSummaryJson = :conflictSummaryJson,
            metadataLockedByUser = :metadataLockedByUser,
            lastScannedMs = :lastScannedMs
        WHERE id = :audiobookId
        """
    )
    suspend fun updateAudiobookMetadata(
        audiobookId: Long,
        title: String,
        author: String,
        description: String,
        artworkUrl: String,
        hasTagConflict: Boolean,
        conflictSummaryJson: String,
        metadataLockedByUser: Boolean,
        lastScannedMs: Long
    )

    @Query("UPDATE audiobooks SET speedOverride = :speedOverride, playbackSpeed = :playbackSpeed WHERE id = :audiobookId")
    suspend fun setBookSpeedOverride(audiobookId: Long, speedOverride: Float, playbackSpeed: Float)

    @Query("UPDATE audiobooks SET silenceModeOverride = :silenceModeOverride, skipSilence = :skipSilence, silenceIntensity = :silenceIntensity WHERE id = :audiobookId")
    suspend fun setBookSilenceModeOverride(audiobookId: Long, silenceModeOverride: Int, skipSilence: Boolean, silenceIntensity: Int)

    @Query("UPDATE audiobooks SET smartRewindOverride = :smartRewindOverride WHERE id = :audiobookId")
    suspend fun setBookSmartRewindOverride(audiobookId: Long, smartRewindOverride: Int)

    @Query("UPDATE audiobooks SET monoModeOverride = :mode WHERE id = :audiobookId")
    suspend fun setBookMonoModeOverride(audiobookId: Long, mode: Int)

    @Query("UPDATE audiobooks SET audioNormalizationOverride = :override WHERE id = :audiobookId")
    suspend fun setBookAudioNormalizationOverride(audiobookId: Long, override: Int)

    @Query(
        """
        UPDATE audiobooks SET
            playbackSpeed = :speed,
            speedOverride = -1.0,
            skipSilence = :skipSilence,
            silenceIntensity = :silenceIntensity,
            silenceModeOverride = -1,
            monoModeOverride = :monoOverride,
            audioNormalizationOverride = :normOverride,
            smartRewindOverride = -1
        """
    )
    suspend fun applyAudioSettingsToAllBooks(
        speed: Float,
        skipSilence: Boolean,
        silenceIntensity: Int,
        monoOverride: Int,
        normOverride: Int
    )

    @Query("UPDATE audiobooks SET artworkUrl = :artworkUrl, metadataLockedByUser = 1, lastScannedMs = :lastScannedMs WHERE id = :audiobookId")
    suspend fun updateBookArtwork(audiobookId: Long, artworkUrl: String, lastScannedMs: Long)

    @Query("UPDATE chapter_tracks SET monoModeOverride = :mode WHERE id = :trackId")
    suspend fun setTrackMonoModeOverride(trackId: Long, mode: Int)

    @Query("UPDATE audiobooks SET lastPlayedAtMs = :timestampMs WHERE id = :audiobookId")
    suspend fun updateLastPlayed(audiobookId: Long, timestampMs: Long)

    @Query("UPDATE audiobooks SET lastTrackId = :trackId, lastPlayedAtMs = :timestampMs WHERE id = :audiobookId")
    suspend fun updateLastTrack(audiobookId: Long, trackId: Long, timestampMs: Long)

    @Query("UPDATE audiobooks SET isHidden = :hidden WHERE id = :audiobookId")
    suspend fun setHidden(audiobookId: Long, hidden: Boolean)

    @Query("UPDATE audiobooks SET isFinished = :finished, finishedAtMs = :finishedAtMs WHERE id = :audiobookId")
    suspend fun setAudiobookFinished(audiobookId: Long, finished: Boolean, finishedAtMs: Long)

    @Query("UPDATE audiobooks SET deletedAtMs = :deletedAtMs WHERE id = :audiobookId")
    suspend fun setDeletedAt(audiobookId: Long, deletedAtMs: Long?)

    @Query("SELECT * FROM audiobooks WHERE deletedAtMs IS NOT NULL AND deletedAtMs <= :cutoffMs")
    suspend fun getExpiredTrashedAudiobooks(cutoffMs: Long): List<AudiobookEntity>

    @Query("SELECT * FROM audiobooks WHERE deletedAtMs IS NULL AND isFinished = 1 AND finishedAtMs > 0 AND finishedAtMs <= :cutoffMs")
    suspend fun getFinishedAudiobooksOlderThan(cutoffMs: Long): List<AudiobookEntity>

    @Query("UPDATE chapter_tracks SET positionMs = :positionMs WHERE id = :trackId")
    suspend fun updateTrackPosition(trackId: Long, positionMs: Long)

    @Query("UPDATE chapter_tracks SET isFinished = :finished WHERE id = :trackId")
    suspend fun setTrackFinished(trackId: Long, finished: Boolean)

    @Query("UPDATE chapter_tracks SET isFinished = :finished, positionMs = :positionMs WHERE audiobookId = :audiobookId")
    suspend fun resetAllTracksForBook(audiobookId: Long, finished: Boolean, positionMs: Long)

    @Query(
        """
        UPDATE audiobooks SET
            folderKey = :folderKey,
            folderDocId = :folderDocId,
            folderUri = :folderUri,
            folderName = :folderName,
            parentFolderName = :parentFolderName,
            contentFingerprint = :contentFingerprint
        WHERE id = :audiobookId
        """
    )
    suspend fun updateAudiobookFolderLocation(
        audiobookId: Long,
        folderKey: String,
        folderDocId: String,
        folderUri: String,
        folderName: String,
        parentFolderName: String,
        contentFingerprint: String
    )

    @Query("UPDATE chapter_tracks SET fileUri = :fileUri, sortOrder = :sortOrder, trackNumber = :trackNumber, title = :title, durationMs = :durationMs, albumTag = :albumTag, artistTag = :artistTag WHERE id = :trackId")
    suspend fun updateTrackDetails(
        trackId: Long,
        fileUri: String,
        sortOrder: Int,
        trackNumber: Int,
        title: String,
        durationMs: Long,
        albumTag: String,
        artistTag: String
    )

    @Query("UPDATE chapter_tracks SET guid = :guid, fileUri = :fileUri, sortOrder = :sortOrder, trackNumber = :trackNumber, title = :title, durationMs = :durationMs, albumTag = :albumTag, artistTag = :artistTag WHERE id = :trackId")
    suspend fun updateTrackIdentity(
        trackId: Long,
        guid: String,
        fileUri: String,
        sortOrder: Int,
        trackNumber: Int,
        title: String,
        durationMs: Long,
        albumTag: String,
        artistTag: String
    )
}

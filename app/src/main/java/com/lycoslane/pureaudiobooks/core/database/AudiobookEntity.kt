package com.lycoslane.pureaudiobooks.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audiobooks",
    indices = [
        Index(value = ["folderKey"], unique = true),
        Index(value = ["deletedAtMs"]),
        Index(value = ["lastPlayedAtMs"])
    ]
)
data class AudiobookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val folderKey: String,
    val folderDocId: String = "",
    val folderUri: String = "",
    val folderName: String,
    val parentFolderName: String = "",
    val title: String,
    val author: String = "",
    val description: String = "",
    val artworkUrl: String = "",
    val playbackSpeed: Float = 1.0f,
    val skipSilence: Boolean = false,
    val silenceIntensity: Int = 1,
    val hasTagConflict: Boolean = false,
    val conflictSummaryJson: String = "",
    val metadataLockedByUser: Boolean = false,
    val isSplitByAlbum: Boolean = false,
    val isFinished: Boolean = false,
    val finishedAtMs: Long = 0L,
    val isHidden: Boolean = false,
    val lastTrackId: Long = 0L,
    val deletedAtMs: Long? = null,
    val lastPlayedAtMs: Long = 0L,
    val lastScannedMs: Long = 0L,
    val contentFingerprint: String = ""
)

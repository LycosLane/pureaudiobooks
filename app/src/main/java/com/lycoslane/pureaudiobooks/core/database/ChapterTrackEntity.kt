package com.lycoslane.pureaudiobooks.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapter_tracks",
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["audiobookId"]),
        Index(value = ["audiobookId", "guid"], unique = true)
    ]
)
data class ChapterTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val audiobookId: Long,
    val guid: String,
    val title: String,
    val trackNumber: Int = 0,
    val sortOrder: Int = 0,
    val fileUri: String,
    val albumTag: String = "",
    val artistTag: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isFinished: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val lastModifiedMs: Long = 0L
)

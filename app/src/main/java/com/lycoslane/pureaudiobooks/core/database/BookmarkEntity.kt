package com.lycoslane.pureaudiobooks.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["audiobookId"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val audiobookId: Long,
    val trackId: Long,
    val trackTitle: String,
    val positionMs: Long,
    val note: String = "",
    val createdAtMs: Long
)

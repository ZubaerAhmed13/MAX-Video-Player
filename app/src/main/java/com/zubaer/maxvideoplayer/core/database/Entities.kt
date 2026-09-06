package com.zubaer.maxvideoplayer.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_history")
data class MediaHistoryEntity(
    @PrimaryKey val stableMediaId: String,
    val uri: String,
    val title: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastPlayedAtMs: Long,
    val completed: Boolean,
)

@Entity(tableName = "playback_preferences")
data class PlaybackPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)

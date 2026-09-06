package com.zubaer.maxvideoplayer.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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

@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val stableMediaId: String,
    val addedAtMs: Long,
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["name"], unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "stableMediaId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "orderIndex"], unique = true),
    ],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val stableMediaId: String,
    val orderIndex: Int,
    val addedAtMs: Long,
)

@Entity(tableName = "library_sources")
data class LibrarySourceEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val displayName: String,
    val sourceType: String,
    val status: String,
    val permissionPersisted: Boolean,
    val addedAtMs: Long,
    val lastScanAtMs: Long?,
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val folderKey: String,
    val displayName: String,
    val excludedAtMs: Long,
)

@Entity(
    tableName = "media_index",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["folderKey"]),
        Index(value = ["title"]),
        Index(value = ["dateModifiedMs"]),
    ],
)
data class MediaIndexEntity(
    @PrimaryKey val stableMediaId: String,
    val sourceId: String,
    val uri: String,
    val title: String,
    val fileName: String?,
    val mimeType: String?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val dateAddedMs: Long?,
    val dateModifiedMs: Long?,
    val relativePath: String?,
    val folderKey: String?,
    val folderName: String?,
    val sourceType: String,
    val availability: String,
)

@Entity(tableName = "library_preferences")
data class LibraryPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)

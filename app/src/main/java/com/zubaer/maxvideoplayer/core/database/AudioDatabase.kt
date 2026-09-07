package com.zubaer.maxvideoplayer.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(
    tableName = "audio_associations",
    indices = [
        Index(value = ["stableMediaId"]),
        Index(value = ["stableMediaId", "isPreferred"]),
    ],
)
data class AudioAssociationEntity(
    @PrimaryKey val id: String,
    val stableMediaId: String,
    val audioUri: String,
    val displayName: String,
    val language: String?,
    val mimeType: String?,
    val addedAtMs: Long,
    val isPreferred: Boolean,
    val availability: String,
)

@Entity(tableName = "audio_media_state")
data class AudioMediaStateEntity(
    @PrimaryKey val stableMediaId: String,
    val selectedExternalId: String?,
    val selectionMode: String,
    val selectedLanguage: String?,
    val selectedLabel: String?,
    val selectedMimeType: String?,
    val selectedChannelCount: Int?,
    val delayMs: Long,
    val updatedAtMs: Long,
)

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio_associations ORDER BY addedAtMs ASC")
    suspend fun allAssociations(): List<AudioAssociationEntity>

    @Query("SELECT * FROM audio_associations WHERE stableMediaId = :stableMediaId ORDER BY isPreferred DESC, addedAtMs ASC")
    suspend fun associationsForMedia(stableMediaId: String): List<AudioAssociationEntity>

    @Query("SELECT * FROM audio_associations WHERE id = :id LIMIT 1")
    suspend fun association(id: String): AudioAssociationEntity?

    @Upsert
    suspend fun upsertAssociation(entity: AudioAssociationEntity)

    @Query("DELETE FROM audio_associations WHERE id = :id")
    suspend fun deleteAssociation(id: String)

    @Query("DELETE FROM audio_associations WHERE stableMediaId = :stableMediaId")
    suspend fun deleteAssociationsForMedia(stableMediaId: String)

    @Query("SELECT * FROM audio_media_state")
    suspend fun allMediaState(): List<AudioMediaStateEntity>

    @Query("SELECT * FROM audio_media_state WHERE stableMediaId = :stableMediaId LIMIT 1")
    suspend fun mediaState(stableMediaId: String): AudioMediaStateEntity?

    @Upsert
    suspend fun upsertMediaState(entity: AudioMediaStateEntity)

    @Query("SELECT * FROM audio_associations WHERE stableMediaId = :stableMediaId ORDER BY addedAtMs ASC")
    fun associationsForMediaBlockingForMigrationTest(stableMediaId: String): List<AudioAssociationEntity>

    @Query("SELECT * FROM audio_media_state WHERE stableMediaId = :stableMediaId LIMIT 1")
    fun mediaStateBlockingForMigrationTest(stableMediaId: String): AudioMediaStateEntity?
}

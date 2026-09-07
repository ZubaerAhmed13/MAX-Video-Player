package com.zubaer.maxvideoplayer.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(
    tableName = "subtitle_associations",
    indices = [
        Index(value = ["stableMediaId"]),
        Index(value = ["stableMediaId", "isPreferred"]),
    ],
)
data class SubtitleAssociationEntity(
    @PrimaryKey val id: String,
    val stableMediaId: String,
    val subtitleUri: String,
    val displayName: String,
    val language: String?,
    val mimeType: String,
    val format: String,
    val encoding: String,
    val addedAtMs: Long,
    val isPreferred: Boolean,
    val availability: String,
    val delayMs: Long,
)

@Entity(tableName = "subtitle_media_state")
data class SubtitleMediaStateEntity(
    @PrimaryKey val stableMediaId: String,
    val selectedExternalId: String?,
    val delayMs: Long,
    val updatedAtMs: Long,
)

@Dao
interface SubtitleDao {
    @Query("SELECT * FROM subtitle_associations ORDER BY addedAtMs ASC")
    suspend fun allAssociations(): List<SubtitleAssociationEntity>

    @Query("SELECT * FROM subtitle_associations WHERE stableMediaId = :stableMediaId ORDER BY isPreferred DESC, addedAtMs ASC")
    suspend fun associationsForMedia(stableMediaId: String): List<SubtitleAssociationEntity>

    @Query("SELECT * FROM subtitle_associations WHERE id = :id LIMIT 1")
    suspend fun association(id: String): SubtitleAssociationEntity?

    @Upsert
    suspend fun upsertAssociation(entity: SubtitleAssociationEntity)

    @Query("UPDATE subtitle_associations SET isPreferred = CASE WHEN id = :preferredId THEN 1 ELSE 0 END WHERE stableMediaId = :stableMediaId")
    suspend fun setPreferred(stableMediaId: String, preferredId: String)

    @Query("UPDATE subtitle_associations SET availability = :availability WHERE id = :id")
    suspend fun setAvailability(id: String, availability: String)

    @Query("UPDATE subtitle_associations SET delayMs = :delayMs WHERE id = :id")
    suspend fun setAssociationDelay(id: String, delayMs: Long)

    @Query("DELETE FROM subtitle_associations WHERE id = :id")
    suspend fun deleteAssociation(id: String)

    @Query("DELETE FROM subtitle_associations WHERE stableMediaId = :stableMediaId")
    suspend fun deleteAssociationsForMedia(stableMediaId: String)

    @Query("SELECT * FROM subtitle_media_state")
    suspend fun allMediaState(): List<SubtitleMediaStateEntity>

    @Query("SELECT * FROM subtitle_media_state WHERE stableMediaId = :stableMediaId LIMIT 1")
    suspend fun mediaState(stableMediaId: String): SubtitleMediaStateEntity?

    @Upsert
    suspend fun upsertMediaState(entity: SubtitleMediaStateEntity)

    @Query("SELECT * FROM subtitle_associations WHERE stableMediaId = :stableMediaId ORDER BY addedAtMs ASC")
    fun associationsForMediaBlockingForMigrationTest(stableMediaId: String): List<SubtitleAssociationEntity>

    @Query("SELECT * FROM subtitle_media_state WHERE stableMediaId = :stableMediaId LIMIT 1")
    fun mediaStateBlockingForMigrationTest(stableMediaId: String): SubtitleMediaStateEntity?
}

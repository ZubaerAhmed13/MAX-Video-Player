package com.zubaer.maxvideoplayer.feature.decoder.persistence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "decoder_media_state")
data class DecoderMediaStateEntity(
    @PrimaryKey val stableMediaId: String,
    val requestedMode: String,
    val updatedAtMs: Long,
)

@Dao
interface DecoderMediaStateDao {
    @Query("SELECT * FROM decoder_media_state WHERE stableMediaId = :stableMediaId LIMIT 1")
    suspend fun get(stableMediaId: String): DecoderMediaStateEntity?

    @Upsert
    suspend fun upsert(entity: DecoderMediaStateEntity)

    @Query("DELETE FROM decoder_media_state WHERE stableMediaId = :stableMediaId")
    suspend fun delete(stableMediaId: String)

    @Query("DELETE FROM decoder_media_state")
    suspend fun clear()
}

package com.zubaer.maxvideoplayer.feature.privatevault.persistence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "private_media")
data class PrivateMediaEntity(
    @PrimaryKey val vaultId: String,
    val containerLocation: String,
    val encryptedSizeBytes: Long,
    val originalSizeBytes: Long,
    val createdAtMs: Long,
    val importedAtMs: Long,
    val formatVersion: Int,
    val status: String,
)

@Dao
interface PrivateMediaDao {
    @Query("SELECT * FROM private_media ORDER BY importedAtMs DESC")
    fun observeAll(): Flow<List<PrivateMediaEntity>>

    @Query("SELECT * FROM private_media ORDER BY importedAtMs DESC")
    suspend fun all(): List<PrivateMediaEntity>

    @Query("SELECT * FROM private_media WHERE vaultId = :vaultId LIMIT 1")
    suspend fun get(vaultId: String): PrivateMediaEntity?

    @Query("SELECT * FROM private_media WHERE vaultId = :vaultId LIMIT 1")
    fun getBlockingForMigrationTest(vaultId: String): PrivateMediaEntity?

    @Upsert
    suspend fun upsert(entity: PrivateMediaEntity)

    @Query("DELETE FROM private_media WHERE vaultId = :vaultId")
    suspend fun delete(vaultId: String)

    @Query("DELETE FROM private_media")
    suspend fun clear()
}

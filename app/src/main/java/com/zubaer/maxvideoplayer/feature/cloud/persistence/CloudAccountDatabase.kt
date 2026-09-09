package com.zubaer.maxvideoplayer.feature.cloud.persistence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "cloud_accounts",
    indices = [Index(value = ["provider", "providerAccountId"], unique = true)],
)
data class CloudAccountEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val providerAccountId: String,
    val displayName: String,
    val emailHint: String?,
    /** Opaque key/token-cache reference only. Raw access/refresh tokens must never be stored here. */
    val authReference: String,
    val createdAtMs: Long,
    val lastUsedAtMs: Long,
)

@Dao
interface CloudAccountDao {
    @Query("SELECT * FROM cloud_accounts ORDER BY lastUsedAtMs DESC, displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<CloudAccountEntity>>

    @Query("SELECT * FROM cloud_accounts ORDER BY lastUsedAtMs DESC, displayName COLLATE NOCASE")
    suspend fun all(): List<CloudAccountEntity>

    @Query("SELECT * FROM cloud_accounts WHERE id = :id LIMIT 1")
    suspend fun get(id: String): CloudAccountEntity?

    @Query("SELECT * FROM cloud_accounts WHERE provider = :provider AND providerAccountId = :providerAccountId LIMIT 1")
    suspend fun find(provider: String, providerAccountId: String): CloudAccountEntity?

    @Upsert
    suspend fun upsert(entity: CloudAccountEntity)

    @Query("UPDATE cloud_accounts SET lastUsedAtMs = :timestamp WHERE id = :id")
    suspend fun markUsed(id: String, timestamp: Long)

    @Query("DELETE FROM cloud_accounts WHERE id = :id")
    suspend fun delete(id: String)
}

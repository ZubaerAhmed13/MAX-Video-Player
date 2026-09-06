package com.zubaer.maxvideoplayer.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaHistoryDao {
    @Upsert
    suspend fun upsert(entity: MediaHistoryEntity)

    @Query("SELECT * FROM media_history WHERE stableMediaId = :stableId LIMIT 1")
    suspend fun get(stableId: String): MediaHistoryEntity?

    @Query("SELECT * FROM media_history ORDER BY lastPlayedAtMs DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<MediaHistoryEntity>>

    @Query("DELETE FROM media_history WHERE stableMediaId = :stableId")
    suspend fun delete(stableId: String)
}

@Dao
interface PlaybackPreferenceDao {
    @Upsert
    suspend fun upsert(entity: PlaybackPreferenceEntity)

    @Query("SELECT * FROM playback_preferences WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): PlaybackPreferenceEntity?
}

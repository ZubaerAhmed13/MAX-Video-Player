package com.zubaer.maxvideoplayer.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaHistoryDao {
    @Upsert suspend fun upsert(entity: MediaHistoryEntity)
    @Query("SELECT * FROM media_history WHERE stableMediaId = :stableId LIMIT 1") suspend fun get(stableId: String): MediaHistoryEntity?
    @Query("SELECT * FROM media_history WHERE stableMediaId = :stableId LIMIT 1") fun getBlockingForMigrationTest(stableId: String): MediaHistoryEntity?
    @Query("SELECT * FROM media_history ORDER BY lastPlayedAtMs DESC LIMIT :limit") fun recent(limit: Int = 50): Flow<List<MediaHistoryEntity>>
    @Query("SELECT * FROM media_history ORDER BY lastPlayedAtMs DESC") fun all(): Flow<List<MediaHistoryEntity>>
    @Query("DELETE FROM media_history WHERE stableMediaId = :stableId") suspend fun delete(stableId: String)
    @Query("DELETE FROM media_history") suspend fun clear()
    @Query("UPDATE media_history SET uri = :uri, title = :title, mimeType = :mimeType, sizeBytes = :sizeBytes, width = :width, height = :height WHERE stableMediaId = :stableId")
    suspend fun updateSource(stableId: String, uri: String, title: String, mimeType: String?, sizeBytes: Long?, width: Int?, height: Int?)
}

@Dao
interface PlaybackPreferenceDao {
    @Upsert suspend fun upsert(entity: PlaybackPreferenceEntity)
    @Query("SELECT * FROM playback_preferences WHERE `key` = :key LIMIT 1") suspend fun get(key: String): PlaybackPreferenceEntity?
}

@Dao
interface FavouriteDao {
    @Query("SELECT * FROM favourites ORDER BY addedAtMs DESC") fun observeAll(): Flow<List<FavouriteEntity>>
    @Query("SELECT stableMediaId FROM favourites") suspend fun ids(): List<String>
    @Upsert suspend fun upsert(entity: FavouriteEntity)
    @Query("DELETE FROM favourites WHERE stableMediaId = :stableMediaId") suspend fun delete(stableMediaId: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAtMs DESC, name COLLATE NOCASE") fun observePlaylists(): Flow<List<PlaylistEntity>>
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1") suspend fun getPlaylist(playlistId: Long): PlaylistEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlaylist(entity: PlaylistEntity): Long
    @Update suspend fun updatePlaylist(entity: PlaylistEntity)
    @Query("DELETE FROM playlists WHERE id = :playlistId") suspend fun deletePlaylist(playlistId: Long)
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC") fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC") suspend fun items(playlistId: Long): List<PlaylistItemEntity>
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId") suspend fun itemCount(playlistId: Long): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertItem(entity: PlaylistItemEntity): Long
    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND stableMediaId = :stableMediaId") suspend fun deleteItem(playlistId: Long, stableMediaId: String)
    @Query("DELETE FROM playlist_items WHERE stableMediaId = :stableMediaId") suspend fun deleteMediaEverywhere(stableMediaId: String)
    @Query("UPDATE playlist_items SET orderIndex = :orderIndex WHERE playlistId = :playlistId AND stableMediaId = :stableMediaId") suspend fun updateOrder(playlistId: Long, stableMediaId: String, orderIndex: Int)
}

@Dao
interface LibrarySourceDao {
    @Query("SELECT * FROM library_sources ORDER BY addedAtMs ASC") fun observeAll(): Flow<List<LibrarySourceEntity>>
    @Query("SELECT * FROM library_sources ORDER BY addedAtMs ASC") suspend fun all(): List<LibrarySourceEntity>
    @Query("SELECT * FROM library_sources WHERE id = :id LIMIT 1") suspend fun get(id: String): LibrarySourceEntity?
    @Upsert suspend fun upsert(entity: LibrarySourceEntity)
    @Query("DELETE FROM library_sources WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface ExcludedFolderDao {
    @Query("SELECT * FROM excluded_folders ORDER BY displayName COLLATE NOCASE") fun observeAll(): Flow<List<ExcludedFolderEntity>>
    @Query("SELECT folderKey FROM excluded_folders") suspend fun keys(): List<String>
    @Upsert suspend fun upsert(entity: ExcludedFolderEntity)
    @Query("DELETE FROM excluded_folders WHERE folderKey = :folderKey") suspend fun delete(folderKey: String)
}

@Dao
interface MediaIndexDao {
    @Query("SELECT * FROM media_index ORDER BY title COLLATE NOCASE, stableMediaId") fun observeAll(): Flow<List<MediaIndexEntity>>
    @Query("SELECT COUNT(*) FROM media_index") fun observeCount(): Flow<Int>
    @Query("SELECT * FROM media_index ORDER BY title COLLATE NOCASE, stableMediaId LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<MediaIndexEntity>
    @Query("SELECT * FROM media_index WHERE stableMediaId = :stableMediaId LIMIT 1") suspend fun get(stableMediaId: String): MediaIndexEntity?
    @Query("SELECT * FROM media_index WHERE stableMediaId IN (:stableIds)") suspend fun byIds(stableIds: List<String>): List<MediaIndexEntity>
    @Query("SELECT * FROM media_index WHERE sourceId = :sourceId") suspend fun bySource(sourceId: String): List<MediaIndexEntity>
    @Upsert suspend fun upsertAll(items: List<MediaIndexEntity>)
    @Query("UPDATE media_index SET availability = 'UNAVAILABLE' WHERE sourceId = :sourceId") suspend fun markSourceUnavailable(sourceId: String)
    @Query("DELETE FROM media_index WHERE sourceId = :sourceId") suspend fun deleteSource(sourceId: String)
    @Query("DELETE FROM media_index WHERE stableMediaId = :stableMediaId") suspend fun delete(stableMediaId: String)
}

@Dao
interface LibraryPreferenceDao {
    @Upsert suspend fun upsert(entity: LibraryPreferenceEntity)
    @Query("SELECT * FROM library_preferences WHERE `key` = :key LIMIT 1") suspend fun get(key: String): LibraryPreferenceEntity?
}
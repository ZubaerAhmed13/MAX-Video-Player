package com.zubaer.maxvideoplayer.feature.library

import android.net.Uri
import androidx.room.withTransaction
import com.zubaer.maxvideoplayer.core.database.ExcludedFolderEntity
import com.zubaer.maxvideoplayer.core.database.FavouriteEntity
import com.zubaer.maxvideoplayer.core.database.LibraryPreferenceEntity
import com.zubaer.maxvideoplayer.core.database.LibrarySourceEntity
import com.zubaer.maxvideoplayer.core.database.MaxDatabase
import com.zubaer.maxvideoplayer.core.database.MediaHistoryEntity
import com.zubaer.maxvideoplayer.core.database.MediaIndexEntity
import com.zubaer.maxvideoplayer.core.database.PlaybackHistoryRepository
import com.zubaer.maxvideoplayer.core.database.PlaylistEntity
import com.zubaer.maxvideoplayer.core.database.PlaylistItemEntity
import com.zubaer.maxvideoplayer.core.media.MediaStoreRepository
import com.zubaer.maxvideoplayer.core.media.SafTreeScanner
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.core.model.SourceAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class LibraryRepository(
    private val database: MaxDatabase,
    private val mediaStoreRepository: MediaStoreRepository,
    private val safTreeScanner: SafTreeScanner,
    private val historyRepository: PlaybackHistoryRepository,
) {
    fun media(): Flow<List<AppMedia>> = database.mediaIndexDao().observeAll().map { rows -> rows.map { row -> row.toAppMedia() } }
    fun favourites(): Flow<Set<String>> = database.favouriteDao().observeAll().map { rows -> rows.mapTo(linkedSetOf()) { it.stableMediaId } }
    fun playlists(): Flow<List<PlaylistEntity>> = database.playlistDao().observePlaylists()
    fun sources(): Flow<List<LibrarySourceEntity>> = database.librarySourceDao().observeAll()
    fun excludedFolders(): Flow<List<ExcludedFolderEntity>> = database.excludedFolderDao().observeAll()
    fun history(): Flow<List<MediaHistoryEntity>> = historyRepository.all()
    fun mediaStoreChanges(): Flow<Unit> = mediaStoreRepository.changes()

    suspend fun refreshAll() {
        refreshMediaStore()
        database.librarySourceDao().all().filter { it.sourceType == SOURCE_SAF_TREE }.forEach { refreshSafSource(it) }
    }

    suspend fun refreshMediaStore() {
        val scanned = mediaStoreRepository.videos()
        database.withTransaction {
            database.mediaIndexDao().markSourceUnavailable(MediaStoreRepository.SOURCE_ID)
            if (scanned.isNotEmpty()) database.mediaIndexDao().upsertAll(scanned.map { media -> media.toIndexEntity() })
        }
    }

    suspend fun addSafTree(uri: Uri, permissionPersisted: Boolean) {
        val uriString = uri.toString()
        val sourceId = "saf-tree:${sha256(uriString)}"
        val now = System.currentTimeMillis()
        val displayName = runCatching { safTreeScanner.displayName(uri) }.getOrDefault("Folder")
        val existing = database.librarySourceDao().get(sourceId)
        val source = LibrarySourceEntity(
            id = sourceId,
            uri = uriString,
            displayName = displayName,
            sourceType = SOURCE_SAF_TREE,
            status = STATUS_AVAILABLE,
            permissionPersisted = permissionPersisted,
            addedAtMs = existing?.addedAtMs ?: now,
            lastScanAtMs = existing?.lastScanAtMs,
        )
        database.librarySourceDao().upsert(source)
        refreshSafSource(source)
    }

    suspend fun refreshSafSource(source: LibrarySourceEntity) {
        val uri = Uri.parse(source.uri)
        val scanned = try {
            safTreeScanner.scan(uri, source.id, source.displayName)
        } catch (security: SecurityException) {
            database.librarySourceDao().upsert(source.copy(status = STATUS_PERMISSION_LOST))
            database.mediaIndexDao().markSourceUnavailable(source.id)
            return
        } catch (failure: Throwable) {
            database.librarySourceDao().upsert(source.copy(status = STATUS_UNAVAILABLE))
            throw failure
        }

        database.withTransaction {
            database.mediaIndexDao().markSourceUnavailable(source.id)
            if (scanned.isNotEmpty()) database.mediaIndexDao().upsertAll(scanned.map { media -> media.toIndexEntity() })
            database.librarySourceDao().upsert(source.copy(status = STATUS_AVAILABLE, lastScanAtMs = System.currentTimeMillis()))
        }
    }

    suspend fun removeSafSource(sourceId: String) {
        database.withTransaction {
            database.librarySourceDao().delete(sourceId)
            database.mediaIndexDao().deleteSource(sourceId)
        }
    }

    suspend fun excludeFolder(folderKey: String, displayName: String) {
        database.excludedFolderDao().upsert(ExcludedFolderEntity(folderKey, displayName, System.currentTimeMillis()))
    }

    suspend fun restoreExcludedFolder(folderKey: String) = database.excludedFolderDao().delete(folderKey)

    suspend fun setFavourite(stableMediaId: String, favourite: Boolean) {
        if (favourite) database.favouriteDao().upsert(FavouriteEntity(stableMediaId, System.currentTimeMillis()))
        else database.favouriteDao().delete(stableMediaId)
    }

    suspend fun createPlaylist(name: String): Long {
        val clean = name.trim().take(80)
        require(clean.isNotBlank()) { "Playlist name cannot be empty" }
        val now = System.currentTimeMillis()
        return database.playlistDao().insertPlaylist(PlaylistEntity(name = clean, createdAtMs = now, updatedAtMs = now))
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        val playlist = database.playlistDao().getPlaylist(playlistId) ?: return
        val clean = name.trim().take(80)
        require(clean.isNotBlank()) { "Playlist name cannot be empty" }
        database.playlistDao().updatePlaylist(playlist.copy(name = clean, updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(playlistId: Long) = database.playlistDao().deletePlaylist(playlistId)

    suspend fun addToPlaylist(playlistId: Long, stableMediaIds: Collection<String>) {
        database.withTransaction {
            var order = database.playlistDao().itemCount(playlistId)
            val now = System.currentTimeMillis()
            stableMediaIds.distinct().forEach { stableId ->
                val inserted = database.playlistDao().insertItem(PlaylistItemEntity(playlistId, stableId, order, now))
                if (inserted != -1L) order++
            }
            database.playlistDao().getPlaylist(playlistId)?.let { database.playlistDao().updatePlaylist(it.copy(updatedAtMs = now)) }
        }
    }

    suspend fun removeFromPlaylist(playlistId: Long, stableMediaId: String) {
        database.withTransaction {
            database.playlistDao().deleteItem(playlistId, stableMediaId)
            normalizePlaylistOrder(playlistId)
        }
    }

    suspend fun movePlaylistItem(playlistId: Long, stableMediaId: String, delta: Int) {
        database.withTransaction {
            val current = database.playlistDao().items(playlistId).toMutableList()
            val from = current.indexOfFirst { it.stableMediaId == stableMediaId }
            if (from < 0) return@withTransaction
            val to = (from + delta).coerceIn(current.indices)
            if (from == to) return@withTransaction
            val moved = current.removeAt(from)
            current.add(to, moved)
            current.forEachIndexed { index, item -> database.playlistDao().updateOrder(playlistId, item.stableMediaId, -(index + 1)) }
            current.forEachIndexed { index, item -> database.playlistDao().updateOrder(playlistId, item.stableMediaId, index) }
            database.playlistDao().getPlaylist(playlistId)?.let { database.playlistDao().updatePlaylist(it.copy(updatedAtMs = System.currentTimeMillis())) }
        }
    }

    suspend fun playlistMedia(playlistId: Long): List<AppMedia> {
        val items = database.playlistDao().items(playlistId)
        if (items.isEmpty()) return emptyList()
        val rows = database.mediaIndexDao().byIds(items.map { it.stableMediaId }).associateBy { it.stableMediaId }
        return items.mapNotNull { item -> rows[item.stableMediaId]?.toAppMedia() ?: historyRepository.get(item.stableMediaId)?.toUnavailableMedia() }
    }

    /**
     * Preserve the app-level stable ID and all user relationships while reconnecting an unavailable
     * source (or reconciling a provider rename that returned a new content URI).
     */
    suspend fun relinkMedia(original: AppMedia, replacement: AppMedia): MediaRelinkValidator.Validation {
        val validation = MediaRelinkValidator.validate(original, replacement)
        if (!validation.accepted) return validation
        val relinked = replacement.copy(
            stableId = original.stableId,
            sourceId = replacement.sourceId ?: original.sourceId ?: "relinked",
            availability = SourceAvailability.AVAILABLE,
        )
        database.withTransaction {
            database.mediaIndexDao().upsertAll(listOf(relinked.toIndexEntity()))
            database.mediaHistoryDao().updateSource(
                stableId = original.stableId,
                uri = relinked.uri,
                title = relinked.title,
                mimeType = relinked.mimeType,
                sizeBytes = relinked.sizeBytes,
                width = relinked.width,
                height = relinked.height,
            )
        }
        return validation
    }

    /** Cleanup is called only after Android/provider deletion has actually succeeded. */
    suspend fun cleanupDeletedMedia(stableMediaId: String) {
        database.withTransaction {
            database.mediaIndexDao().delete(stableMediaId)
            database.favouriteDao().delete(stableMediaId)
            database.playlistDao().deleteMediaEverywhere(stableMediaId)
            database.mediaHistoryDao().delete(stableMediaId)
        }
    }

    suspend fun deleteHistory(stableMediaId: String) = historyRepository.delete(stableMediaId)
    suspend fun clearHistory() = historyRepository.clear()
    suspend fun getPreference(key: String): String? = database.libraryPreferenceDao().get(key)?.value
    suspend fun setPreference(key: String, value: String) = database.libraryPreferenceDao().upsert(LibraryPreferenceEntity(key, value))

    private suspend fun normalizePlaylistOrder(playlistId: Long) {
        val items = database.playlistDao().items(playlistId)
        items.forEachIndexed { index, item -> database.playlistDao().updateOrder(playlistId, item.stableMediaId, -(index + 1)) }
        items.forEachIndexed { index, item -> database.playlistDao().updateOrder(playlistId, item.stableMediaId, index) }
    }

    private fun AppMedia.toIndexEntity() = MediaIndexEntity(
        stableMediaId = stableId,
        sourceId = sourceId ?: when (sourceType) {
            MediaSourceType.MEDIA_STORE -> MediaStoreRepository.SOURCE_ID
            MediaSourceType.SAF -> "saf:unknown"
            MediaSourceType.NETWORK -> "network"
        },
        uri = uri,
        title = title,
        fileName = fileName,
        mimeType = mimeType,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        dateAddedMs = dateAddedMs,
        dateModifiedMs = dateModifiedMs,
        relativePath = relativePath,
        folderKey = folderKey,
        folderName = folderName,
        sourceType = sourceType.name,
        availability = SourceAvailability.AVAILABLE.name,
    )

    private fun MediaIndexEntity.toAppMedia() = AppMedia(
        stableId = stableMediaId,
        uri = uri,
        title = title,
        fileName = fileName,
        mimeType = mimeType,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        dateAddedMs = dateAddedMs,
        dateModifiedMs = dateModifiedMs,
        relativePath = relativePath,
        folderKey = folderKey,
        folderName = folderName,
        sourceId = sourceId,
        availability = runCatching { SourceAvailability.valueOf(availability) }.getOrDefault(SourceAvailability.UNKNOWN),
        sourceType = runCatching { MediaSourceType.valueOf(sourceType) }.getOrDefault(MediaSourceType.SAF),
    )

    private fun MediaHistoryEntity.toUnavailableMedia() = AppMedia(
        stableId = stableMediaId,
        uri = uri,
        title = title,
        mimeType = mimeType,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        availability = SourceAvailability.UNAVAILABLE,
        sourceType = when {
            uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("rtsp://") -> MediaSourceType.NETWORK
            uri.startsWith("content://media") -> MediaSourceType.MEDIA_STORE
            else -> MediaSourceType.SAF
        },
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(24)

    companion object {
        const val SOURCE_SAF_TREE = "SAF_TREE"
        const val STATUS_AVAILABLE = "AVAILABLE"
        const val STATUS_PERMISSION_LOST = "PERMISSION_LOST"
        const val STATUS_UNAVAILABLE = "UNAVAILABLE"
    }
}
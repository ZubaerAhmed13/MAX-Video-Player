package com.zubaer.maxvideoplayer.feature.library

import com.zubaer.maxvideoplayer.core.database.ExcludedFolderEntity
import com.zubaer.maxvideoplayer.core.database.LibrarySourceEntity
import com.zubaer.maxvideoplayer.core.database.MediaHistoryEntity
import com.zubaer.maxvideoplayer.core.database.PlaylistEntity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.ResumeAction
import com.zubaer.maxvideoplayer.core.model.ResumePolicy
import com.zubaer.maxvideoplayer.core.model.SourceAvailability
import java.util.Locale

enum class LibrarySection { VIDEOS, FOLDERS, CONTINUE_WATCHING, RECENT, FAVOURITES, PLAYLISTS, HISTORY }
enum class LibraryViewMode { LIST, GRID }
enum class VideoSort { NAME, DATE_ADDED, DATE_MODIFIED, DURATION, SIZE, RESOLUTION, LAST_PLAYED }
enum class SortDirection { ASCENDING, DESCENDING }
enum class LibraryFilter { ALL, WATCHED, UNWATCHED, IN_PROGRESS, FAVOURITES }

data class FolderItem(
    val key: String,
    val name: String,
    val videos: List<AppMedia>,
    val totalSizeBytes: Long,
    val newestModifiedMs: Long?,
)

data class LibraryPlaybackRequest(
    val queue: List<AppMedia>,
    val startIndex: Int,
)

data class LibraryUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val section: LibrarySection = LibrarySection.VIDEOS,
    val query: String = "",
    val sort: VideoSort = VideoSort.DATE_MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val viewMode: LibraryViewMode = LibraryViewMode.LIST,
    val media: List<AppMedia> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val selectedFolderKey: String? = null,
    val selectedPlaylistId: Long? = null,
    val selectedPlaylistMedia: List<AppMedia> = emptyList(),
    val history: List<MediaHistoryEntity> = emptyList(),
    val favouriteIds: Set<String> = emptySet(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val sources: List<LibrarySourceEntity> = emptyList(),
    val excludedFolders: List<ExcludedFolderEntity> = emptyList(),
)

object LibraryQueryEngine {
    fun apply(
        input: List<AppMedia>,
        query: String,
        sort: VideoSort,
        direction: SortDirection,
        filter: LibraryFilter,
        history: Map<String, MediaHistoryEntity>,
        favouriteIds: Set<String>,
        includeUnavailable: Boolean = false,
        preserveInputOrder: Boolean = false,
    ): List<AppMedia> {
        val normalizedQuery = normalize(query)
        val filtered = input.asSequence()
            .filter { includeUnavailable || it.availability == SourceAvailability.AVAILABLE }
            .filter { media -> matchesFilter(media, filter, history, favouriteIds) }
            .filter { media ->
                normalizedQuery.isEmpty() || sequenceOf(media.title, media.fileName, media.folderName)
                    .filterNotNull()
                    .map(::normalize)
                    .any { it.contains(normalizedQuery) }
            }
            .toList()

        if (preserveInputOrder) return filtered
        val sorted = filtered.sortedWith(comparator(sort, history))
        return if (direction == SortDirection.ASCENDING) sorted else sorted.asReversed()
    }

    fun isContinueWatching(history: MediaHistoryEntity): Boolean =
        !history.completed && history.durationMs > 0L &&
            ResumePolicy.decide(history.lastPositionMs, history.durationMs).action == ResumeAction.OFFER_RESUME

    private fun matchesFilter(
        media: AppMedia,
        filter: LibraryFilter,
        history: Map<String, MediaHistoryEntity>,
        favouriteIds: Set<String>,
    ): Boolean {
        val item = history[media.stableId]
        return when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.WATCHED -> item?.completed == true
            LibraryFilter.UNWATCHED -> item == null
            LibraryFilter.IN_PROGRESS -> item?.let(::isContinueWatching) == true
            LibraryFilter.FAVOURITES -> media.stableId in favouriteIds
        }
    }

    private fun comparator(sort: VideoSort, history: Map<String, MediaHistoryEntity>): Comparator<AppMedia> = Comparator { a, b ->
        when (sort) {
            VideoSort.NAME -> a.title.compareTo(b.title, ignoreCase = true)
            VideoSort.DATE_ADDED -> compareNullable(a.dateAddedMs, b.dateAddedMs)
            VideoSort.DATE_MODIFIED -> compareNullable(a.dateModifiedMs, b.dateModifiedMs)
            VideoSort.DURATION -> compareNullable(a.durationMs, b.durationMs)
            VideoSort.SIZE -> compareNullable(a.sizeBytes, b.sizeBytes)
            VideoSort.RESOLUTION -> compareNullable(resolutionPixels(a), resolutionPixels(b))
            VideoSort.LAST_PLAYED -> compareNullable(history[a.stableId]?.lastPlayedAtMs, history[b.stableId]?.lastPlayedAtMs)
        }.let { primary -> if (primary != 0) primary else a.title.compareTo(b.title, ignoreCase = true) }
    }

    private fun resolutionPixels(media: AppMedia): Long? {
        val width = media.width ?: return null
        val height = media.height ?: return null
        return width.toLong() * height.toLong()
    }

    private fun <T : Comparable<T>> compareNullable(a: T?, b: T?): Int = when {
        a == null && b == null -> 0
        a == null -> -1
        b == null -> 1
        else -> a.compareTo(b)
    }

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

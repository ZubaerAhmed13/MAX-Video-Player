package com.zubaer.maxvideoplayer.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zubaer.maxvideoplayer.core.database.ExcludedFolderEntity
import com.zubaer.maxvideoplayer.core.database.LibrarySourceEntity
import com.zubaer.maxvideoplayer.core.database.MediaHistoryEntity
import com.zubaer.maxvideoplayer.core.database.PlaylistEntity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.core.model.SourceAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var indexedMedia: List<AppMedia> = emptyList()
    private var historyRows: List<MediaHistoryEntity> = emptyList()
    private var favouriteIds: Set<String> = emptySet()
    private var playlists: List<PlaylistEntity> = emptyList()
    private var sources: List<LibrarySourceEntity> = emptyList()
    private var excludedFolders: List<ExcludedFolderEntity> = emptyList()
    private var selectedPlaylistMedia: List<AppMedia> = emptyList()

    init {
        viewModelScope.launch { repository.media().collect { indexedMedia = it; recompute() } }
        viewModelScope.launch { repository.history().collect { historyRows = it; recompute() } }
        viewModelScope.launch { repository.favourites().collect { favouriteIds = it; recompute() } }
        viewModelScope.launch { repository.playlists().collect { playlists = it; recompute() } }
        viewModelScope.launch { repository.sources().collect { sources = it; recompute() } }
        viewModelScope.launch { repository.excludedFolders().collect { excludedFolders = it; recompute() } }
        viewModelScope.launch { restorePreferences() }
    }

    fun refresh() = launchAction(loading = true) { repository.refreshAll() }

    fun addFolder(uri: Uri, permissionPersisted: Boolean) = launchAction(loading = true) {
        repository.addSafTree(uri, permissionPersisted)
    }

    fun setSection(section: LibrarySection) {
        _state.value = _state.value.copy(section = section, selectedFolderKey = null, selectedPlaylistId = null, selectedPlaylistMedia = emptyList())
        selectedPlaylistMedia = emptyList()
        recompute()
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        recompute()
    }

    fun setSort(sort: VideoSort) {
        _state.value = _state.value.copy(sort = sort)
        persist(PREF_SORT, sort.name)
        recompute()
    }

    fun toggleSortDirection() {
        val next = if (_state.value.sortDirection == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
        _state.value = _state.value.copy(sortDirection = next)
        persist(PREF_SORT_DIRECTION, next.name)
        recompute()
    }

    fun setFilter(filter: LibraryFilter) {
        _state.value = _state.value.copy(filter = filter)
        persist(PREF_FILTER, filter.name)
        recompute()
    }

    fun setViewMode(mode: LibraryViewMode) {
        _state.value = _state.value.copy(viewMode = mode)
        persist(PREF_VIEW_MODE, mode.name)
    }

    fun openFolder(folderKey: String) {
        _state.value = _state.value.copy(section = LibrarySection.FOLDERS, selectedFolderKey = folderKey)
        recompute()
    }

    fun closeFolder() {
        _state.value = _state.value.copy(selectedFolderKey = null)
        recompute()
    }

    fun openPlaylist(playlistId: Long) {
        _state.value = _state.value.copy(section = LibrarySection.PLAYLISTS, selectedPlaylistId = playlistId)
        reloadPlaylist(playlistId)
    }

    fun closePlaylist() {
        selectedPlaylistMedia = emptyList()
        _state.value = _state.value.copy(selectedPlaylistId = null, selectedPlaylistMedia = emptyList())
        recompute()
    }

    fun toggleFavourite(media: AppMedia) = launchAction {
        repository.setFavourite(media.stableId, media.stableId !in favouriteIds)
    }

    fun createPlaylist(name: String) = launchAction { repository.createPlaylist(name) }
    fun renamePlaylist(playlistId: Long, name: String) = launchAction { repository.renamePlaylist(playlistId, name) }
    fun deletePlaylist(playlistId: Long) = launchAction {
        repository.deletePlaylist(playlistId)
        if (_state.value.selectedPlaylistId == playlistId) closePlaylist()
    }

    fun addToPlaylist(playlistId: Long, media: AppMedia) = launchAction {
        repository.addToPlaylist(playlistId, listOf(media.stableId))
        if (_state.value.selectedPlaylistId == playlistId) reloadPlaylist(playlistId)
    }

    fun removeFromPlaylist(playlistId: Long, media: AppMedia) = launchAction {
        repository.removeFromPlaylist(playlistId, media.stableId)
        reloadPlaylist(playlistId)
    }

    fun movePlaylistItem(playlistId: Long, media: AppMedia, delta: Int) = launchAction {
        repository.movePlaylistItem(playlistId, media.stableId, delta)
        reloadPlaylist(playlistId)
    }

    fun excludeFolder(folder: FolderItem) = launchAction { repository.excludeFolder(folder.key, folder.name) }
    fun restoreFolder(folderKey: String) = launchAction { repository.restoreExcludedFolder(folderKey) }
    fun removeSource(sourceId: String) = launchAction { repository.removeSafSource(sourceId) }
    fun deleteHistory(stableMediaId: String) = launchAction { repository.deleteHistory(stableMediaId) }
    fun clearHistory() = launchAction { repository.clearHistory() }

    fun playbackRequest(media: AppMedia): LibraryPlaybackRequest {
        val visible = _state.value.media.filter { it.availability == SourceAvailability.AVAILABLE }
        val queue = if (media in visible) visible else listOf(media)
        return LibraryPlaybackRequest(queue = queue, startIndex = queue.indexOfFirst { it.stableId == media.stableId }.coerceAtLeast(0))
    }

    private fun reloadPlaylist(playlistId: Long) {
        viewModelScope.launch {
            selectedPlaylistMedia = repository.playlistMedia(playlistId)
            recompute()
        }
    }

    private fun recompute() {
        val state = _state.value
        val excludedKeys = excludedFolders.mapTo(hashSetOf()) { it.folderKey }
        val historyById = historyRows.associateBy { it.stableMediaId }
        val available = indexedMedia.filter { media -> media.folderKey !in excludedKeys }
        val folders = available
            .filter { it.availability == SourceAvailability.AVAILABLE }
            .groupBy { it.folderKey ?: "root:${it.sourceId ?: it.sourceType.name}" }
            .map { (key, media) ->
                FolderItem(
                    key = key,
                    name = media.firstNotNullOfOrNull { it.folderName } ?: "Root",
                    videos = media,
                    totalSizeBytes = media.sumOf { it.sizeBytes ?: 0L },
                    newestModifiedMs = media.mapNotNull { it.dateModifiedMs }.maxOrNull(),
                )
            }
            .sortedBy { it.name.lowercase() }

        val base = when (state.section) {
            LibrarySection.VIDEOS -> available
            LibrarySection.FOLDERS -> state.selectedFolderKey?.let { key -> folders.firstOrNull { it.key == key }?.videos }.orEmpty()
            LibrarySection.CONTINUE_WATCHING -> historyRows.filter(LibraryQueryEngine::isContinueWatching).mapNotNull { row ->
                available.firstOrNull { it.stableId == row.stableMediaId && it.availability == SourceAvailability.AVAILABLE }
            }
            LibrarySection.RECENT -> historyRows.take(30).mapNotNull { row -> available.firstOrNull { it.stableId == row.stableMediaId } }
            LibrarySection.FAVOURITES -> available.filter { it.stableId in favouriteIds }
            LibrarySection.PLAYLISTS -> if (state.selectedPlaylistId != null) selectedPlaylistMedia else emptyList()
            LibrarySection.HISTORY -> historyRows.map { row ->
                available.firstOrNull { it.stableId == row.stableMediaId } ?: AppMedia(
                    stableId = row.stableMediaId,
                    uri = row.uri,
                    title = row.title,
                    mimeType = row.mimeType,
                    durationMs = row.durationMs,
                    sizeBytes = row.sizeBytes,
                    width = row.width,
                    height = row.height,
                    availability = SourceAvailability.UNAVAILABLE,
                    sourceType = if (row.uri.startsWith("content://media")) MediaSourceType.MEDIA_STORE else MediaSourceType.SAF,
                )
            }
        }

        val filtered = LibraryQueryEngine.apply(
            input = base,
            query = state.query,
            sort = state.sort,
            direction = state.sortDirection,
            filter = state.filter,
            history = historyById,
            favouriteIds = favouriteIds,
        )

        _state.value = state.copy(
            media = filtered,
            folders = folders,
            selectedPlaylistMedia = selectedPlaylistMedia,
            history = historyRows,
            favouriteIds = favouriteIds,
            playlists = playlists,
            sources = sources,
            excludedFolders = excludedFolders,
        )
    }

    private suspend fun restorePreferences() {
        val sort = repository.getPreference(PREF_SORT)?.let { runCatching { VideoSort.valueOf(it) }.getOrNull() } ?: _state.value.sort
        val direction = repository.getPreference(PREF_SORT_DIRECTION)?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() } ?: _state.value.sortDirection
        val filter = repository.getPreference(PREF_FILTER)?.let { runCatching { LibraryFilter.valueOf(it) }.getOrNull() } ?: _state.value.filter
        val viewMode = repository.getPreference(PREF_VIEW_MODE)?.let { runCatching { LibraryViewMode.valueOf(it) }.getOrNull() } ?: _state.value.viewMode
        _state.value = _state.value.copy(sort = sort, sortDirection = direction, filter = filter, viewMode = viewMode)
        recompute()
    }

    private fun persist(key: String, value: String) {
        viewModelScope.launch { repository.setPreference(key, value) }
    }

    private fun launchAction(loading: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (loading) _state.value = _state.value.copy(loading = true, error = null)
            runCatching { block() }
                .onFailure { error -> _state.value = _state.value.copy(error = error.message ?: "Library operation failed") }
            if (loading) _state.value = _state.value.copy(loading = false)
        }
    }

    companion object {
        private const val PREF_SORT = "library.video_sort"
        private const val PREF_SORT_DIRECTION = "library.sort_direction"
        private const val PREF_FILTER = "library.filter"
        private const val PREF_VIEW_MODE = "library.view_mode"
    }
}

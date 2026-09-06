package com.zubaer.maxvideoplayer.feature.library

import android.content.IntentSender
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LibraryFileAction {
    data object Delete : LibraryFileAction
    data class Rename(val requestedName: String) : LibraryFileAction
}

data class FileActionConfirmation(
    val media: AppMedia,
    val action: LibraryFileAction,
    val intentSender: IntentSender,
    val retryAfterApproval: Boolean,
)

sealed interface LibraryEvent {
    data class FileConfirmationRequired(val request: FileActionConfirmation) : LibraryEvent
    data class Message(val text: String) : LibraryEvent
}

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val fileActions: MediaFileActionRepository? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val events: Flow<LibraryEvent> = _events.asSharedFlow()

    private var indexedMedia: List<AppMedia> = emptyList()
    private var historyRows: List<MediaHistoryEntity> = emptyList()
    private var favouriteIds: Set<String> = emptySet()
    private var playlists: List<PlaylistEntity> = emptyList()
    private var sources: List<LibrarySourceEntity> = emptyList()
    private var excludedFolders: List<ExcludedFolderEntity> = emptyList()
    private var selectedPlaylistMedia: List<AppMedia> = emptyList()
    private var appliedQuery = ""
    private var queryDebounceJob: Job? = null
    private var recomputeJob: Job? = null

    init {
        viewModelScope.launch { repository.media().collect { indexedMedia = it; recompute() } }
        viewModelScope.launch { repository.history().collect { historyRows = it; recompute() } }
        viewModelScope.launch { repository.favourites().collect { favouriteIds = it; recompute() } }
        viewModelScope.launch { repository.playlists().collect { playlists = it; recompute() } }
        viewModelScope.launch { repository.sources().collect { sources = it; recompute() } }
        viewModelScope.launch { repository.excludedFolders().collect { excludedFolders = it; recompute() } }
        viewModelScope.launch {
            repository.mediaStoreChanges().collect {
                runCatching { repository.refreshMediaStore() }
                    .onFailure { failure -> _state.value = _state.value.copy(error = failure.message ?: "Media library refresh failed") }
            }
        }
        viewModelScope.launch { restorePreferences() }
    }

    fun refresh() = launchAction(loading = true) { repository.refreshAll() }
    fun addFolder(uri: Uri, permissionPersisted: Boolean) = launchAction(loading = true) { repository.addSafTree(uri, permissionPersisted) }

    fun setSection(section: LibrarySection) {
        _state.value = _state.value.copy(section = section, selectedFolderKey = null, selectedPlaylistId = null, selectedPlaylistMedia = emptyList())
        selectedPlaylistMedia = emptyList()
        recompute()
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        queryDebounceJob?.cancel()
        queryDebounceJob = viewModelScope.launch {
            delay(180L)
            appliedQuery = query
            recompute()
        }
    }

    fun setSort(sort: VideoSort) { _state.value = _state.value.copy(sort = sort); persist(PREF_SORT, sort.name); recompute() }
    fun setFolderSort(sort: FolderSort) { _state.value = _state.value.copy(folderSort = sort); persist(PREF_FOLDER_SORT, sort.name); recompute() }

    fun toggleSortDirection() {
        val next = if (_state.value.sortDirection == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
        _state.value = _state.value.copy(sortDirection = next)
        persist(PREF_SORT_DIRECTION, next.name)
        recompute()
    }

    fun setFilter(filter: LibraryFilter) { _state.value = _state.value.copy(filter = filter); persist(PREF_FILTER, filter.name); recompute() }
    fun setViewMode(mode: LibraryViewMode) { _state.value = _state.value.copy(viewMode = mode); persist(PREF_VIEW_MODE, mode.name) }
    fun openFolder(folderKey: String) { _state.value = _state.value.copy(section = LibrarySection.FOLDERS, selectedFolderKey = folderKey); recompute() }
    fun closeFolder() { _state.value = _state.value.copy(selectedFolderKey = null); recompute() }

    fun openPlaylist(playlistId: Long) {
        _state.value = _state.value.copy(section = LibrarySection.PLAYLISTS, selectedPlaylistId = playlistId)
        reloadPlaylist(playlistId)
    }

    fun closePlaylist() {
        selectedPlaylistMedia = emptyList()
        _state.value = _state.value.copy(selectedPlaylistId = null, selectedPlaylistMedia = emptyList())
        recompute()
    }

    fun toggleFavourite(media: AppMedia) = launchAction { repository.setFavourite(media.stableId, media.stableId !in favouriteIds) }
    fun setFavourite(media: AppMedia, favourite: Boolean) = launchAction { repository.setFavourite(media.stableId, favourite) }
    fun createPlaylist(name: String) = launchAction { repository.createPlaylist(name) }
    fun renamePlaylist(playlistId: Long, name: String) = launchAction { repository.renamePlaylist(playlistId, name) }
    fun deletePlaylist(playlistId: Long) = launchAction { repository.deletePlaylist(playlistId); if (_state.value.selectedPlaylistId == playlistId) closePlaylist() }

    fun addToPlaylist(playlistId: Long, media: AppMedia) = launchAction {
        repository.addToPlaylist(playlistId, listOf(media.stableId))
        if (_state.value.selectedPlaylistId == playlistId) reloadPlaylist(playlistId)
    }

    fun addManyToPlaylist(playlistId: Long, media: Collection<AppMedia>) = launchAction {
        repository.addToPlaylist(playlistId, media.map { it.stableId })
        if (_state.value.selectedPlaylistId == playlistId) reloadPlaylist(playlistId)
    }

    fun removeFromPlaylist(playlistId: Long, media: AppMedia) = launchAction { repository.removeFromPlaylist(playlistId, media.stableId); reloadPlaylist(playlistId) }
    fun movePlaylistItem(playlistId: Long, media: AppMedia, delta: Int) = launchAction { repository.movePlaylistItem(playlistId, media.stableId, delta); reloadPlaylist(playlistId) }
    fun excludeFolder(folder: FolderItem) = launchAction { repository.excludeFolder(folder.key, folder.name) }
    fun restoreFolder(folderKey: String) = launchAction { repository.restoreExcludedFolder(folderKey) }
    fun removeSource(sourceId: String) = launchAction { repository.removeSafSource(sourceId) }
    fun deleteHistory(stableMediaId: String) = launchAction { repository.deleteHistory(stableMediaId) }
    fun clearHistory() = launchAction { repository.clearHistory() }

    fun requestDelete(media: AppMedia) {
        val actions = fileActions ?: return emitMessage("File actions are unavailable")
        viewModelScope.launch { handleDeleteResult(media, actions.delete(media)) }
    }

    fun requestRename(media: AppMedia, requestedName: String) {
        val clean = requestedName.trim()
        if (clean.isBlank()) return emitMessage("File name cannot be empty")
        val actions = fileActions ?: return emitMessage("File actions are unavailable")
        viewModelScope.launch { handleRenameResult(media, clean, actions.rename(media, clean)) }
    }

    fun completeConfirmedFileAction(confirmation: FileActionConfirmation, approved: Boolean) {
        if (!approved) return emitMessage("File action cancelled")
        when (val action = confirmation.action) {
            LibraryFileAction.Delete -> {
                if (confirmation.retryAfterApproval) requestDelete(confirmation.media)
                else launchAction { repository.cleanupDeletedMedia(confirmation.media.stableId); emitMessage("Video deleted") }
            }
            is LibraryFileAction.Rename -> {
                if (confirmation.retryAfterApproval) requestRename(confirmation.media, action.requestedName)
                else emitMessage("Rename permission granted")
            }
        }
    }

    fun relinkMedia(original: AppMedia, replacement: AppMedia) = launchAction {
        val validation = repository.relinkMedia(original, replacement)
        if (validation.accepted) emitMessage("Source reconnected")
        else emitMessage(validation.reason ?: "Selected file does not match")
    }

    fun playbackRequest(media: AppMedia): LibraryPlaybackRequest {
        val visible = _state.value.media.filter { it.availability == SourceAvailability.AVAILABLE }
        val queue = if (visible.any { it.stableId == media.stableId }) visible else listOf(media)
        return LibraryPlaybackRequest(queue = queue, startIndex = queue.indexOfFirst { it.stableId == media.stableId }.coerceAtLeast(0))
    }

    private suspend fun handleDeleteResult(media: AppMedia, result: MediaFileActionRepository.Result) {
        when (result) {
            is MediaFileActionRepository.Result.Completed -> {
                repository.cleanupDeletedMedia(media.stableId)
                emitMessage("Video deleted")
            }
            is MediaFileActionRepository.Result.ConfirmationRequired -> _events.emit(
                LibraryEvent.FileConfirmationRequired(
                    FileActionConfirmation(media, LibraryFileAction.Delete, result.intentSender, result.retryAfterApproval)
                )
            )
            is MediaFileActionRepository.Result.Unsupported -> emitMessage(result.reason)
            is MediaFileActionRepository.Result.Failed -> emitMessage(result.reason)
        }
    }

    private suspend fun handleRenameResult(media: AppMedia, clean: String, result: MediaFileActionRepository.Result) {
        when (result) {
            is MediaFileActionRepository.Result.Completed -> {
                val renamed = media.copy(
                    uri = result.resultingUri ?: media.uri,
                    fileName = clean,
                    title = clean.substringBeforeLast('.', clean),
                    availability = SourceAvailability.AVAILABLE,
                )
                repository.relinkMedia(media, renamed)
                emitMessage("Video renamed")
            }
            is MediaFileActionRepository.Result.ConfirmationRequired -> _events.emit(
                LibraryEvent.FileConfirmationRequired(
                    FileActionConfirmation(media, LibraryFileAction.Rename(clean), result.intentSender, result.retryAfterApproval)
                )
            )
            is MediaFileActionRepository.Result.Unsupported -> emitMessage(result.reason)
            is MediaFileActionRepository.Result.Failed -> emitMessage(result.reason)
        }
    }

    private fun reloadPlaylist(playlistId: Long) {
        viewModelScope.launch { selectedPlaylistMedia = repository.playlistMedia(playlistId); recompute() }
    }

    /** Large collection grouping/search/sort runs on Dispatchers.Default, never on the Compose/UI thread. */
    private fun recompute() {
        val stateSnapshot = _state.value
        val mediaSnapshot = indexedMedia
        val historySnapshot = historyRows
        val favouriteSnapshot = favouriteIds
        val playlistsSnapshot = playlists
        val sourcesSnapshot = sources
        val exclusionsSnapshot = excludedFolders
        val playlistMediaSnapshot = selectedPlaylistMedia
        val querySnapshot = appliedQuery

        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch {
            val derived = withContext(Dispatchers.Default) {
                deriveState(
                    stateSnapshot = stateSnapshot,
                    indexedMedia = mediaSnapshot,
                    historyRows = historySnapshot,
                    favouriteIds = favouriteSnapshot,
                    playlists = playlistsSnapshot,
                    sources = sourcesSnapshot,
                    excludedFolders = exclusionsSnapshot,
                    selectedPlaylistMedia = playlistMediaSnapshot,
                    query = querySnapshot,
                )
            }
            val latest = _state.value
            _state.value = latest.copy(
                media = derived.media,
                folders = derived.folders,
                selectedPlaylistMedia = derived.selectedPlaylistMedia,
                history = derived.history,
                favouriteIds = derived.favouriteIds,
                playlists = derived.playlists,
                sources = derived.sources,
                excludedFolders = derived.excludedFolders,
            )
        }
    }

    private fun deriveState(
        stateSnapshot: LibraryUiState,
        indexedMedia: List<AppMedia>,
        historyRows: List<MediaHistoryEntity>,
        favouriteIds: Set<String>,
        playlists: List<PlaylistEntity>,
        sources: List<LibrarySourceEntity>,
        excludedFolders: List<ExcludedFolderEntity>,
        selectedPlaylistMedia: List<AppMedia>,
        query: String,
    ): LibraryUiState {
        val excludedKeys = excludedFolders.mapTo(hashSetOf()) { it.folderKey }
        val historyById = historyRows.associateBy { it.stableMediaId }
        val availableOrKnown = indexedMedia.filter { it.folderKey !in excludedKeys }
        val unsortedFolders = availableOrKnown
            .asSequence()
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
        val folders = LibraryFolderEngine.sort(unsortedFolders, stateSnapshot.folderSort, stateSnapshot.sortDirection)

        val base = when (stateSnapshot.section) {
            LibrarySection.VIDEOS -> availableOrKnown
            LibrarySection.FOLDERS -> stateSnapshot.selectedFolderKey?.let { key -> folders.firstOrNull { it.key == key }?.videos }.orEmpty()
            LibrarySection.CONTINUE_WATCHING -> historyRows.filter(LibraryQueryEngine::isContinueWatching).mapNotNull { row ->
                availableOrKnown.firstOrNull { it.stableId == row.stableMediaId && it.availability == SourceAvailability.AVAILABLE }
            }
            LibrarySection.RECENT -> historyRows.take(30).mapNotNull { row -> availableOrKnown.firstOrNull { it.stableId == row.stableMediaId } }
            LibrarySection.FAVOURITES -> availableOrKnown.filter { it.stableId in favouriteIds }
            LibrarySection.PLAYLISTS -> if (stateSnapshot.selectedPlaylistId != null) selectedPlaylistMedia else emptyList()
            LibrarySection.HISTORY -> historyRows.map { row ->
                availableOrKnown.firstOrNull { it.stableId == row.stableMediaId } ?: AppMedia(
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

        val historyOrdered = stateSnapshot.section in setOf(LibrarySection.CONTINUE_WATCHING, LibrarySection.RECENT, LibrarySection.HISTORY)
        val playlistOrdered = stateSnapshot.section == LibrarySection.PLAYLISTS && stateSnapshot.selectedPlaylistId != null
        val filtered = LibraryQueryEngine.apply(
            input = base,
            query = query,
            sort = if (historyOrdered) VideoSort.LAST_PLAYED else stateSnapshot.sort,
            direction = if (historyOrdered) SortDirection.DESCENDING else stateSnapshot.sortDirection,
            filter = stateSnapshot.filter,
            history = historyById,
            favouriteIds = favouriteIds,
            includeUnavailable = stateSnapshot.section in setOf(LibrarySection.HISTORY, LibrarySection.PLAYLISTS),
            preserveInputOrder = playlistOrdered,
        )

        return stateSnapshot.copy(
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
        val folderSort = repository.getPreference(PREF_FOLDER_SORT)?.let { runCatching { FolderSort.valueOf(it) }.getOrNull() } ?: _state.value.folderSort
        val direction = repository.getPreference(PREF_SORT_DIRECTION)?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() } ?: _state.value.sortDirection
        val filter = repository.getPreference(PREF_FILTER)?.let { runCatching { LibraryFilter.valueOf(it) }.getOrNull() } ?: _state.value.filter
        val viewMode = repository.getPreference(PREF_VIEW_MODE)?.let { runCatching { LibraryViewMode.valueOf(it) }.getOrNull() } ?: _state.value.viewMode
        _state.value = _state.value.copy(sort = sort, folderSort = folderSort, sortDirection = direction, filter = filter, viewMode = viewMode)
        recompute()
    }

    private fun persist(key: String, value: String) { viewModelScope.launch { repository.setPreference(key, value) } }

    private fun launchAction(loading: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (loading) _state.value = _state.value.copy(loading = true, error = null)
            runCatching { block() }.onFailure { error -> _state.value = _state.value.copy(error = error.message ?: "Library operation failed") }
            if (loading) _state.value = _state.value.copy(loading = false)
        }
    }

    private fun emitMessage(message: String) {
        _events.tryEmit(LibraryEvent.Message(message))
    }

    companion object {
        private const val PREF_SORT = "library.video_sort"
        private const val PREF_FOLDER_SORT = "library.folder_sort"
        private const val PREF_SORT_DIRECTION = "library.sort_direction"
        private const val PREF_FILTER = "library.filter"
        private const val PREF_VIEW_MODE = "library.view_mode"
    }
}

package com.zubaer.maxvideoplayer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.library.LibraryScreen
import com.zubaer.maxvideoplayer.feature.library.LibraryViewModel
import com.zubaer.maxvideoplayer.feature.player.PlayerScreen
import com.zubaer.maxvideoplayer.feature.player.PlayerViewModel
import com.zubaer.maxvideoplayer.ui.MaxTheme
import kotlinx.coroutines.launch

@Composable
fun MaxApp(
    container: AppContainer,
    externalMedia: AppMedia?,
    onExternalConsumed: () -> Unit,
    persistUriPermission: (Uri) -> Boolean,
    onEnterPip: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navigationViewModel: AppNavigationViewModel = viewModel()
    val launch by navigationViewModel.playbackLaunch.collectAsStateWithLifecycle()
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = simpleFactory { LibraryViewModel(container.libraryRepository, container.mediaFileActionRepository) }
    )
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(externalMedia?.stableId) {
        externalMedia?.let {
            navigationViewModel.select(it)
            onExternalConsumed()
        }
    }

    MaxTheme {
        val playbackLaunch = launch
        if (playbackLaunch == null) {
            LibraryScreen(
                state = libraryState,
                events = libraryViewModel.events,
                thumbnailRepository = container.thumbnailRepository,
                onRefresh = libraryViewModel::refresh,
                onOpenDocument = { uri ->
                    persistUriPermission(uri)
                    scope.launch {
                        navigationViewModel.select(container.metadataExtractor.fromUri(uri, MediaSourceType.SAF))
                    }
                },
                onAddFolder = { uri -> libraryViewModel.addFolder(uri, persistUriPermission(uri)) },
                onPlay = { request -> navigationViewModel.selectQueue(request.queue, request.startIndex) },
                onOpenNetworkUrl = { url -> navigationViewModel.select(container.metadataExtractor.fromNetworkUrl(url)) },
                onSection = libraryViewModel::setSection,
                onQuery = libraryViewModel::setQuery,
                onSort = libraryViewModel::setSort,
                onFolderSort = libraryViewModel::setFolderSort,
                onToggleSortDirection = libraryViewModel::toggleSortDirection,
                onFilter = libraryViewModel::setFilter,
                onViewMode = libraryViewModel::setViewMode,
                onOpenFolder = libraryViewModel::openFolder,
                onCloseFolder = libraryViewModel::closeFolder,
                onOpenPlaylist = libraryViewModel::openPlaylist,
                onClosePlaylist = libraryViewModel::closePlaylist,
                onToggleFavourite = libraryViewModel::toggleFavourite,
                onSetFavourite = libraryViewModel::setFavourite,
                onCreatePlaylist = libraryViewModel::createPlaylist,
                onRenamePlaylist = libraryViewModel::renamePlaylist,
                onDeletePlaylist = libraryViewModel::deletePlaylist,
                onAddToPlaylist = libraryViewModel::addToPlaylist,
                onAddManyToPlaylist = libraryViewModel::addManyToPlaylist,
                onRemoveFromPlaylist = libraryViewModel::removeFromPlaylist,
                onMovePlaylistItem = libraryViewModel::movePlaylistItem,
                onExcludeFolder = libraryViewModel::excludeFolder,
                onRestoreFolder = libraryViewModel::restoreFolder,
                onRemoveSource = libraryViewModel::removeSource,
                onDeleteHistory = libraryViewModel::deleteHistory,
                onClearHistory = libraryViewModel::clearHistory,
                onRequestDelete = libraryViewModel::requestDelete,
                onRequestRename = libraryViewModel::requestRename,
                onFileActionApproval = libraryViewModel::completeConfirmedFileAction,
                onRelinkSelected = { original, uri ->
                    persistUriPermission(uri)
                    scope.launch {
                        val replacement = container.metadataExtractor.fromUri(uri, MediaSourceType.SAF)
                        libraryViewModel.relinkMedia(original, replacement)
                    }
                },
                playbackRequest = libraryViewModel::playbackRequest,
            )
        } else {
            val media = playbackLaunch.media
            val playerViewModel: PlayerViewModel = viewModel(
                key = "player:${media.stableId}:${playbackLaunch.queue.size}",
                factory = simpleFactory {
                    PlayerViewModel(
                        media = media,
                        historyRepository = container.historyRepository,
                        playbackConnection = container.playbackConnection,
                        queue = playbackLaunch.queue,
                        startIndex = playbackLaunch.startIndex,
                    )
                },
            )
            PlayerScreen(
                media = media,
                viewModel = playerViewModel,
                playbackConnection = container.playbackConnection,
                onBack = navigationViewModel::clearSelection,
                onEnterPip = onEnterPip,
                onFullscreenChanged = onFullscreenChanged,
            )
        }
    }
}

private fun <T : androidx.lifecycle.ViewModel> simpleFactory(factory: () -> T): androidx.lifecycle.ViewModelProvider.Factory =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : androidx.lifecycle.ViewModel> create(modelClass: Class<VM>): VM = factory() as VM
    }

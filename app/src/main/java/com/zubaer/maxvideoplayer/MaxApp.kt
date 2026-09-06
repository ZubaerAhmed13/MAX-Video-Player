package com.zubaer.maxvideoplayer

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.library.LibraryScreen
import com.zubaer.maxvideoplayer.feature.library.LibraryViewModel
import com.zubaer.maxvideoplayer.feature.player.PlayerScreen
import com.zubaer.maxvideoplayer.feature.player.PlayerViewModel
import com.zubaer.maxvideoplayer.ui.MaxTheme

@Composable
fun MaxApp(
    container: AppContainer,
    externalMedia: AppMedia?,
    onExternalConsumed: () -> Unit,
    persistUriPermission: (Uri) -> Unit,
    onEnterPip: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    var selectedMedia by remember { mutableStateOf<AppMedia?>(null) }
    val libraryViewModel: LibraryViewModel = viewModel(factory = simpleFactory { LibraryViewModel(container.mediaStoreRepository) })

    LaunchedEffect(externalMedia?.stableId) {
        externalMedia?.let {
            selectedMedia = it
            onExternalConsumed()
        }
    }

    MaxTheme {
        val media = selectedMedia
        if (media == null) {
            val libraryState by libraryViewModel.state.collectAsStateWithLifecycleCompat()
            LibraryScreen(
                state = libraryState,
                onRefresh = libraryViewModel::refresh,
                onOpenDocument = { uri ->
                    persistUriPermission(uri)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        val item = container.metadataExtractor.fromUri(uri, MediaSourceType.SAF)
                        selectedMedia = item
                    }
                },
                onPlay = { selectedMedia = it },
                onOpenNetworkUrl = { url -> selectedMedia = container.metadataExtractor.fromNetworkUrl(url) },
            )
        } else {
            val playerViewModel: PlayerViewModel = viewModel(
                key = "player:${media.stableId}",
                factory = simpleFactory { PlayerViewModel(media, container.historyRepository, container.playbackConnection) },
            )
            PlayerScreen(
                media = media,
                viewModel = playerViewModel,
                playbackConnection = container.playbackConnection,
                onBack = { selectedMedia = null },
                onEnterPip = onEnterPip,
                onFullscreenChanged = onFullscreenChanged,
            )
        }
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat(): androidx.compose.runtime.State<T> =
    androidx.lifecycle.compose.collectAsStateWithLifecycle()

private fun <T : androidx.lifecycle.ViewModel> simpleFactory(factory: () -> T): androidx.lifecycle.ViewModelProvider.Factory =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : androidx.lifecycle.ViewModel> create(modelClass: Class<VM>): VM = factory() as VM
    }

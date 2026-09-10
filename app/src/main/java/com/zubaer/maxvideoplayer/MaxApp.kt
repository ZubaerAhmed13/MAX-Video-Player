package com.zubaer.maxvideoplayer

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.core.model.PlaybackTarget
import com.zubaer.maxvideoplayer.feature.audio.BackgroundPlaybackMode
import com.zubaer.maxvideoplayer.feature.audio.ReleaseProfessionalAudioPlayerHost
import com.zubaer.maxvideoplayer.feature.cloud.presentation.CloudBrowserScreen
import com.zubaer.maxvideoplayer.feature.cloud.presentation.CloudBrowserViewModel
import com.zubaer.maxvideoplayer.feature.library.ReleaseLibraryScreen
import com.zubaer.maxvideoplayer.feature.library.LibraryViewModel
import com.zubaer.maxvideoplayer.feature.network.presentation.NetworkScreen
import com.zubaer.maxvideoplayer.feature.network.presentation.NetworkViewModel
import com.zubaer.maxvideoplayer.feature.output.ExternalDisplayController
import com.zubaer.maxvideoplayer.feature.output.OutputDeviceButton
import com.zubaer.maxvideoplayer.feature.player.OrientationMode
import com.zubaer.maxvideoplayer.feature.player.PlayerViewModel
import com.zubaer.maxvideoplayer.feature.privatevault.presentation.AppLockScreen
import com.zubaer.maxvideoplayer.feature.privatevault.presentation.PrivateVaultScreen
import com.zubaer.maxvideoplayer.feature.privatevault.presentation.PrivateVaultViewModel
import com.zubaer.maxvideoplayer.feature.settings.AccessibilityContrastMode
import com.zubaer.maxvideoplayer.feature.settings.SettingsScreen
import com.zubaer.maxvideoplayer.feature.sleeptimer.SleepTimerButton
import com.zubaer.maxvideoplayer.feature.sleeptimer.SleepTimerDialog
import com.zubaer.maxvideoplayer.feature.tv.TvDestination
import com.zubaer.maxvideoplayer.feature.tv.TvHomeScreen
import com.zubaer.maxvideoplayer.feature.tv.TvLibraryScreen
import com.zubaer.maxvideoplayer.ui.MaxTheme
import kotlinx.coroutines.launch

@Composable
fun MaxApp(
    container: AppContainer,
    externalDisplayController: ExternalDisplayController,
    externalMedia: AppMedia?,
    onExternalConsumed: () -> Unit,
    persistUriPermission: (Uri) -> Boolean,
    onEnterPip: (AppMedia) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    onOrientationModeChanged: (OrientationMode) -> Unit,
    onPlayerHostStateChanged: (AppMedia?, Boolean) -> Unit,
    onAudioBackgroundPolicyChanged: (BackgroundPlaybackMode, Boolean) -> Unit,
    onPrivateSurfaceChanged: (Boolean) -> Unit,
    onBiometricUnlock: () -> Unit,
    onBiometricEnroll: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by container.settingsRepository.state.collectAsStateWithLifecycle()
    val appLockState by container.appLockController.state.collectAsStateWithLifecycle()
    val biometricConfigured = container.privateVaultBiometricKeyManager.isEnabled()
    val isTv = remember(context) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
    var showNetwork by remember { mutableStateOf(false) }
    var showCloud by remember { mutableStateOf(false) }
    var showPrivate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showTvHome by remember { mutableStateOf(isTv) }
    var lastTvDestination by remember { mutableStateOf(TvDestination.LIBRARY) }
    val removableVolumes by container.removableStorageController.volumes.collectAsStateWithLifecycle()
    val navigationViewModel: AppNavigationViewModel = viewModel()
    val launch by navigationViewModel.playbackLaunch.collectAsStateWithLifecycle()
    val privateVaultViewModel: PrivateVaultViewModel = viewModel(
        factory = simpleFactory {
            PrivateVaultViewModel(
                container.privateVaultRepository,
                container.privateVaultAuthenticator,
                container.privateVaultSession,
            )
        },
    )
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = simpleFactory { LibraryViewModel(container.libraryRepository, container.mediaFileActionRepository) },
    )
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val networkViewModel: NetworkViewModel = viewModel(
        factory = simpleFactory { NetworkViewModel(container.networkRepository, container.historyRepository) },
    )
    val networkState by networkViewModel.state.collectAsStateWithLifecycle()
    val cloudViewModel: CloudBrowserViewModel = viewModel(
        factory = simpleFactory { CloudBrowserViewModel(container.cloudOAuthCoordinator) },
    )
    val cloudState by cloudViewModel.state.collectAsStateWithLifecycle()

    val removableTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val persisted = persistUriPermission(uri)
            libraryViewModel.addFolder(uri, persisted)
            if (isTv) {
                lastTvDestination = TvDestination.USB
                showTvHome = false
            }
        }
    }

    LaunchedEffect(externalMedia?.stableId) {
        externalMedia?.let {
            navigationViewModel.select(it)
            onExternalConsumed()
        }
    }

    val privatePlayback = launch?.media?.sourceType == MediaSourceType.PRIVATE
    LaunchedEffect(showPrivate, privatePlayback, settings.protectPrivateScreens) {
        onPrivateSurfaceChanged(settings.protectPrivateScreens && (showPrivate || privatePlayback))
    }
    LaunchedEffect(privatePlayback) {
        if (privatePlayback) externalDisplayController.returnToPhone()
    }

    MaxTheme(
        highContrast = settings.contrastMode == AccessibilityContrastMode.HIGH_CONTRAST,
        reduceMotion = settings.reduceMotion,
    ) {
        if (appLockState == com.zubaer.maxvideoplayer.feature.privatevault.auth.AppLockState.LOCKED) {
            AppLockScreen(container.appLockController)
            return@MaxTheme
        }

        val playbackLaunch = launch
        if (playbackLaunch == null) {
            when {
                showSettings -> SettingsScreen(
                    repository = container.settingsRepository,
                    vaultAuthenticator = container.privateVaultAuthenticator,
                    biometricConfigured = biometricConfigured,
                    onEnableBiometric = onBiometricEnroll,
                    onDisableBiometric = container.privateVaultBiometricKeyManager::disable,
                    onBack = { showSettings = false },
                )
                showPrivate -> PrivateVaultScreen(
                    viewModel = privateVaultViewModel,
                    repository = container.privateVaultRepository,
                    onBack = { showPrivate = false },
                    onPlay = { item ->
                        if (container.playbackConnection.state.value.playbackTarget == PlaybackTarget.CAST_DEVICE) {
                            Toast.makeText(context, "Disconnect Cast before playing Private Vault media.", Toast.LENGTH_LONG).show()
                        } else {
                            externalDisplayController.returnToPhone()
                            showPrivate = false
                            navigationViewModel.select(
                                container.privateVaultRepository.toAppMedia(item).copy(title = "Private media"),
                            )
                        }
                    },
                    biometricUnlockEnabled = settings.biometricUnlockEnabled && biometricConfigured,
                    onBiometricUnlock = onBiometricUnlock,
                )
                isTv && showTvHome -> TvHomeScreen(
                    lastFocused = lastTvDestination,
                    mountedUsbCount = removableVolumes.count { it.mounted },
                    onFocused = { lastTvDestination = it },
                    onDestination = { destination ->
                        lastTvDestination = destination
                        when (destination) {
                            TvDestination.LIBRARY -> {
                                showNetwork = false
                                showCloud = false
                                showTvHome = false
                            }
                            TvDestination.NETWORK -> {
                                showNetwork = true
                                showCloud = false
                                showTvHome = false
                            }
                            TvDestination.CLOUD -> {
                                showCloud = true
                                showNetwork = false
                                showTvHome = false
                            }
                            TvDestination.USB -> removableTreePicker.launch(null)
                        }
                    },
                )
                showCloud -> CloudBrowserScreen(
                    state = cloudState,
                    coordinator = container.cloudOAuthCoordinator,
                    viewModel = cloudViewModel,
                    onBack = {
                        showCloud = false
                        if (isTv) showTvHome = true
                    },
                    onPlay = { media ->
                        showCloud = false
                        lastTvDestination = TvDestination.CLOUD
                        navigationViewModel.select(media)
                    },
                )
                showNetwork -> NetworkScreen(
                    state = networkState,
                    viewModel = networkViewModel,
                    onBack = {
                        showNetwork = false
                        if (isTv) showTvHome = true
                    },
                    onPlay = { media ->
                        showNetwork = false
                        lastTvDestination = TvDestination.NETWORK
                        navigationViewModel.select(media)
                    },
                    onPlayQueue = { queue ->
                        showNetwork = false
                        lastTvDestination = TvDestination.NETWORK
                        navigationViewModel.selectQueue(queue, 0)
                    },
                )
                isTv -> TvLibraryScreen(
                    state = libraryState,
                    playbackRequest = libraryViewModel::playbackRequest,
                    onPlay = { request ->
                        lastTvDestination = TvDestination.LIBRARY
                        navigationViewModel.selectQueue(request.queue, request.startIndex)
                    },
                    onBack = { showTvHome = true },
                )
                else -> ReleaseLibraryScreen(
                    state = libraryState,
                    events = libraryViewModel.events,
                    thumbnailRepository = container.thumbnailRepository,
                    onRefresh = libraryViewModel::refresh,
                    onOpenDocument = { uri ->
                        persistUriPermission(uri)
                        scope.launch {
                            lastTvDestination = TvDestination.LIBRARY
                            navigationViewModel.select(container.metadataExtractor.fromUri(uri, MediaSourceType.SAF))
                        }
                    },
                    onAddFolder = { uri -> libraryViewModel.addFolder(uri, persistUriPermission(uri)) },
                    onPlay = { request ->
                        lastTvDestination = TvDestination.LIBRARY
                        navigationViewModel.selectQueue(request.queue, request.startIndex)
                    },
                    onOpenNetworkUrl = { url -> navigationViewModel.select(container.networkRepository.prepareDirect(url)) },
                    onOpenNetworkCenter = { showNetwork = true },
                    onOpenPrivate = { showPrivate = true },
                    onOpenCloud = { showCloud = true },
                    onOpenSettings = { showSettings = true },
                    onOpenUsb = { removableTreePicker.launch(null) },
                    mountedUsbCount = removableVolumes.count { it.mounted },
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
            }
        } else {
            val media = playbackLaunch.media
            val playerViewModel: PlayerViewModel = viewModel(
                key = "player:${media.stableId}:${playbackLaunch.queue.size}",
                factory = simpleFactory {
                    PlayerViewModel(
                        media = media,
                        historyRepository = container.historyRepository,
                        playbackConnection = container.playbackConnection,
                        preferences = container.playerPreferences,
                        decoderRepository = container.decoderRepository,
                        deviceCapabilityProvider = container.deviceCapabilityProvider,
                        queue = playbackLaunch.queue,
                        startIndex = playbackLaunch.startIndex,
                    )
                },
            )
            Box(Modifier.fillMaxSize()) {
                ReleaseProfessionalAudioPlayerHost(
                    media = media,
                    viewModel = playerViewModel,
                    playbackConnection = container.playbackConnection,
                    subtitleRepository = container.subtitleRepository,
                    audioRepository = container.audioRepository,
                    audioController = container.audioPlaybackController,
                    onBack = {
                        navigationViewModel.clearSelection()
                        if (isTv) showTvHome = true
                    },
                    onEnterPip = { selected ->
                        if (selected.sourceType != MediaSourceType.PRIVATE) onEnterPip(selected)
                    },
                    onFullscreenChanged = onFullscreenChanged,
                    onOrientationModeChanged = onOrientationModeChanged,
                    onPlayerHostStateChanged = onPlayerHostStateChanged,
                    onAudioBackgroundPolicyChanged = onAudioBackgroundPolicyChanged,
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 4.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SleepTimerButton(container.sleepTimerRepository, onOpen = { showSleepTimer = true })
                    if (media.sourceType != MediaSourceType.PRIVATE) {
                        OutputDeviceButton(controller = externalDisplayController)
                    }
                }
            }
            if (showSleepTimer) {
                SleepTimerDialog(container.sleepTimerRepository, onDismiss = { showSleepTimer = false })
            }
        }
    }
}

private fun <T : androidx.lifecycle.ViewModel> simpleFactory(factory: () -> T): androidx.lifecycle.ViewModelProvider.Factory =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : androidx.lifecycle.ViewModel> create(modelClass: Class<VM>): VM = factory() as VM
    }

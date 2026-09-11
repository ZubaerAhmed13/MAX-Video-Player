package com.zubaer.maxvideoplayer.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.PlaybackError
import com.zubaer.maxvideoplayer.core.model.PlaybackTarget
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleDialog
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleEdgeStyle
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleFormatPolicy
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleStyleState
import com.zubaer.maxvideoplayer.feature.tv.TvPlayerAction
import com.zubaer.maxvideoplayer.feature.tv.TvPlayerInputController
import com.zubaer.maxvideoplayer.feature.tv.TvPlayerShortcutBar
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun PlayerScreen(
    media: AppMedia,
    viewModel: PlayerViewModel,
    playbackConnection: PlaybackConnection,
    subtitleRepository: SubtitleRepository,
    onBack: () -> Unit,
    onEnterPip: (AppMedia) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    onOrientationModeChanged: (OrientationMode) -> Unit,
    onPlayerHostStateChanged: (AppMedia?, Boolean) -> Unit,
    onAudioControls: (() -> Unit)? = null,
) {
    val coordinator by viewModel.state.collectAsStateWithLifecycle()
    val playback by playbackConnection.state.collectAsStateWithLifecycle()
    val subtitleStyle by subtitleRepository.style.collectAsStateWithLifecycle()
    val currentMedia = viewModel.mediaForPlaybackId(playback.mediaId)
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val accessibilityManager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    val isTelevision =
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val localVideoProcessingAvailable = playback.playbackTarget != PlaybackTarget.CAST_DEVICE
    val tvFocusRequester = remember { FocusRequester() }
    val subtitlePanelFocusRequester = remember { FocusRequester() }
    val morePanelFocusRequester = remember { FocusRequester() }
    var subtitleDialogVisible by remember { mutableStateOf(false) }
    var queueDialogVisible by remember { mutableStateOf(false) }
    var subtitleLoadError by remember { mutableStateOf<String?>(null) }
    var touchExploration by remember(accessibilityManager) {
        mutableStateOf(accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled)
    }
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            subtitleRepository.persistReadPermission(uri)
            val descriptor = subtitleRepository.describe(uri)
            when {
                descriptor == null -> subtitleLoadError = "This file is not a supported subtitle format. Use SRT, WebVTT, SSA/ASS or TTML/DFXP."
                !subtitleRepository.canOpen(uri) -> subtitleLoadError = "The subtitle file could not be opened. Check the provider permission and try again."
                else -> {
                    playbackConnection.attachExternalSubtitle(descriptor)
                    subtitleLoadError = null
                }
            }
        }
    }

    DisposableEffect(accessibilityManager) {
        val accessibilityListener = AccessibilityManager.AccessibilityStateChangeListener { enabled ->
            touchExploration = enabled && accessibilityManager.isTouchExplorationEnabled
        }
        val touchListener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            touchExploration = accessibilityManager.isEnabled && enabled
        }
        accessibilityManager.addAccessibilityStateChangeListener(accessibilityListener)
        accessibilityManager.addTouchExplorationStateChangeListener(touchListener)
        onDispose {
            accessibilityManager.removeAccessibilityStateChangeListener(accessibilityListener)
            accessibilityManager.removeTouchExplorationStateChangeListener(touchListener)
        }
    }

    LaunchedEffect(playback.isPlaying) { viewModel.onPlaybackPlayingChanged(playback.isPlaying) }
    LaunchedEffect(playback.playbackEnded) { if (playback.playbackEnded) viewModel.showControls() }
    LaunchedEffect(touchExploration) { viewModel.setAccessibilityMode(touchExploration) }
    LaunchedEffect(coordinator.orientationMode) { onOrientationModeChanged(coordinator.orientationMode) }
    LaunchedEffect(coordinator.preferences.autoPip, currentMedia.stableId) {
        onPlayerHostStateChanged(currentMedia, coordinator.preferences.autoPip)
    }
    LaunchedEffect(isTelevision, coordinator.controlsVisible) {
        if (isTelevision && !coordinator.controlsVisible) tvFocusRequester.requestFocus()
    }
    LaunchedEffect(subtitleDialogVisible, isTelevision) {
        if (subtitleDialogVisible && !isTelevision) runCatching { subtitlePanelFocusRequester.requestFocus() }
    }
    LaunchedEffect(coordinator.activeMenu, isTelevision) {
        if (coordinator.activeMenu == PlayerMenu.SETTINGS && !isTelevision) {
            runCatching { morePanelFocusRequester.requestFocus() }
        }
    }
    LaunchedEffect(localVideoProcessingAvailable, coordinator.activeMenu) {
        if (!localVideoProcessingAvailable && coordinator.activeMenu in setOf(PlayerMenu.DECODER, PlayerMenu.DISPLAY)) {
            viewModel.closeMenu()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { setWindowBrightness(it, originalBrightness) }
            onFullscreenChanged(false)
            onOrientationModeChanged(OrientationMode.AUTO)
            onPlayerHostStateChanged(null, false)
        }
    }

    fun handlePlayerBack() {
        when {
            subtitleLoadError != null -> subtitleLoadError = null
            queueDialogVisible -> queueDialogVisible = false
            subtitleDialogVisible -> subtitleDialogVisible = false
            coordinator.resumePositionMs != null -> onBack()
            coordinator.tutorialVisible -> viewModel.dismissTutorial()
            coordinator.activeMenu != PlayerMenu.NONE -> viewModel.closeMenu()
            coordinator.controlsLocked -> viewModel.unlockControls()
            coordinator.fullscreen -> {
                viewModel.setFullscreen(false)
                onFullscreenChanged(false)
            }
            isTelevision && coordinator.controlsVisible -> viewModel.onSurfaceTap()
            else -> onBack()
        }
    }

    fun togglePlayback() {
        when {
            playback.playbackEnded -> {
                playbackConnection.seekTo(0L)
                playbackConnection.play()
            }
            playback.isPlaying -> playbackConnection.pause()
            else -> playbackConnection.play()
        }
        viewModel.showControls()
    }

    BackHandler { handlePlayerBack() }

    coordinator.resumePositionMs?.let { position ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Resume playback?") },
            text = { Text("Continue from ${formatPlayerTime(position)} or start from the beginning.") },
            confirmButton = { Button(onClick = viewModel::resume) { Text("Resume") } },
            dismissButton = { TextButton(onClick = viewModel::startOver) { Text("Start over") } },
        )
    }

    val rootModifier = if (isTelevision) {
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(tvFocusRequester)
            .onPreviewKeyEvent { composeEvent ->
                val event = composeEvent.nativeKeyEvent
                if (event.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val action = TvPlayerInputController.actionFor(event.keyCode) ?: return@onPreviewKeyEvent false
                val dpadNavigationKey = event.keyCode in setOf(
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                )
                // Once controls are visible, let Compose focus navigation operate normally. Media
                // transport keys and Back remain global. Hidden controls retain direct player keys.
                if (coordinator.controlsVisible && dpadNavigationKey) return@onPreviewKeyEvent false
                when (action) {
                    TvPlayerAction.PLAY_PAUSE -> togglePlayback()
                    TvPlayerAction.PLAY -> { playbackConnection.play(); viewModel.showControls() }
                    TvPlayerAction.PAUSE -> { playbackConnection.pause(); viewModel.showControls() }
                    TvPlayerAction.SEEK_BACKWARD -> {
                        playbackConnection.seekTo((playback.currentPositionMs - TvPlayerInputController.SEEK_STEP_MS).coerceAtLeast(0L))
                        viewModel.showControls()
                    }
                    TvPlayerAction.SEEK_FORWARD -> {
                        val target = playback.currentPositionMs + TvPlayerInputController.SEEK_STEP_MS
                        playbackConnection.seekTo(if (playback.durationMs > 0L) target.coerceAtMost(playback.durationMs) else target)
                        viewModel.showControls()
                    }
                    TvPlayerAction.PREVIOUS -> { playbackConnection.seekToPrevious(); viewModel.showControls() }
                    TvPlayerAction.NEXT -> { playbackConnection.seekToNext(); viewModel.showControls() }
                    TvPlayerAction.SHOW_CONTROLS -> viewModel.showControls()
                    TvPlayerAction.BACK -> handlePlayerBack()
                }
                true
            }
            .focusable()
    } else {
        Modifier.fillMaxSize().background(Color.Black)
    }

    Box(rootModifier.testTag("player_root")) {
        var viewportSize by remember { mutableStateOf(IntSize.Zero) }
        val latestCoordinator = rememberUpdatedState(coordinator)
        val latestPlayback = rememberUpdatedState(playback)
        val latestViewport = rememberUpdatedState(viewportSize)
        val viewConfiguration = LocalViewConfiguration.current

        PlayerVideoSurface(
            media = currentMedia,
            playbackConnection = playbackConnection,
            coordinator = coordinator,
            subtitleStyle = subtitleStyle,
            viewportSize = viewportSize,
            localVideoProcessingAvailable = localVideoProcessingAvailable,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .testTag("video_surface")
                .pointerInput(
                    coordinator.controlsLocked,
                    coordinator.activeMenu,
                    coordinator.tutorialVisible,
                    coordinator.resumePositionMs,
                    coordinator.preparing,
                    subtitleDialogVisible,
                    touchExploration,
                ) {
                    if (subtitleDialogVisible || surfaceInteractionBlocked(coordinator, touchExploration)) return@pointerInput
                    detectTapGestures(
                        onTap = { viewModel.onSurfaceTap() },
                        onDoubleTap = { offset ->
                            if (subtitleDialogVisible || surfaceInteractionBlocked(latestCoordinator.value, touchExploration)) return@detectTapGestures
                            val zone = PlayerInteractionPolicy.doubleTapZone(offset.x, size.width.toFloat())
                            val current = latestPlayback.value
                            viewModel.doubleTap(zone, current.currentPositionMs, current.durationMs)
                        },
                    )
                }
                .pointerInput(
                    coordinator.controlsLocked,
                    coordinator.activeMenu,
                    coordinator.tutorialVisible,
                    coordinator.resumePositionMs,
                    coordinator.preparing,
                    coordinator.preferences.horizontalSeekEnabled,
                    coordinator.preferences.brightnessGestureEnabled,
                    coordinator.preferences.volumeGestureEnabled,
                    coordinator.preferences.gestureSensitivity,
                    subtitleDialogVisible,
                    touchExploration,
                    localVideoProcessingAvailable,
                ) {
                    if (subtitleDialogVisible || surfaceInteractionBlocked(coordinator, touchExploration)) return@pointerInput
                    var startOffset = Offset.Zero
                    var accumulated = Offset.Zero
                    var gestureKind = PlayerGestureKind.NONE
                    var seekStartMs = 0L
                    var seekTargetMs: Long? = null
                    var brightnessStart = 0.5f
                    var volumeStart = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            if (!subtitleDialogVisible && !surfaceInteractionBlocked(latestCoordinator.value, touchExploration)) {
                                startOffset = offset
                                accumulated = Offset.Zero
                                gestureKind = PlayerGestureKind.NONE
                                seekStartMs = latestPlayback.value.currentPositionMs
                                seekTargetMs = null
                                val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                                brightnessStart = if (currentBrightness >= 0f) currentBrightness else readSystemBrightnessFraction(context)
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                volumeStart = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()
                                viewModel.beginInteraction()
                            }
                        },
                        onDragCancel = { viewModel.endInteraction() },
                        onDragEnd = {
                            val target = seekTargetMs
                            if (gestureKind == PlayerGestureKind.SEEK && target != null && !subtitleDialogVisible && !surfaceInteractionBlocked(latestCoordinator.value, touchExploration)) {
                                viewModel.commitSeek(target)
                            } else {
                                viewModel.endInteraction()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val currentCoordinator = latestCoordinator.value
                            if (subtitleDialogVisible || surfaceInteractionBlocked(currentCoordinator, touchExploration) || currentCoordinator.gestureKind == PlayerGestureKind.ZOOM) {
                                return@detectDragGestures
                            }
                            accumulated += dragAmount
                            if (gestureKind == PlayerGestureKind.NONE) {
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                gestureKind = PlayerInteractionPolicy.classifyDrag(
                                    dx = accumulated.x,
                                    dy = accumulated.y,
                                    startXFraction = startOffset.x / width,
                                    touchSlopPx = viewConfiguration.touchSlop,
                                    horizontalEnabled = currentCoordinator.preferences.horizontalSeekEnabled,
                                    brightnessEnabled = currentCoordinator.preferences.brightnessGestureEnabled,
                                    volumeEnabled = currentCoordinator.preferences.volumeGestureEnabled && localVideoProcessingAvailable,
                                )
                                if (gestureKind != PlayerGestureKind.NONE) viewModel.updateGestureKind(gestureKind)
                            }
                            when (gestureKind) {
                                PlayerGestureKind.SEEK -> {
                                    change.consume()
                                    val current = latestPlayback.value
                                    val target = PlayerInteractionPolicy.seekTargetMs(
                                        startPositionMs = seekStartMs,
                                        durationMs = current.durationMs,
                                        dragDxPx = accumulated.x,
                                        viewportWidthPx = size.width.toFloat(),
                                        sensitivity = currentCoordinator.preferences.gestureSensitivity,
                                    )
                                    seekTargetMs = target
                                    viewModel.updateSeekGesture(seekStartMs, target)
                                }
                                PlayerGestureKind.BRIGHTNESS -> {
                                    change.consume()
                                    val value = PlayerInteractionPolicy.brightnessFromDrag(brightnessStart, accumulated.y, size.height.toFloat())
                                    activity?.let { setWindowBrightness(it, value) }
                                    viewModel.updateBrightness(value)
                                }
                                PlayerGestureKind.VOLUME -> {
                                    if (!localVideoProcessingAvailable) return@detectDragGestures
                                    change.consume()
                                    val fraction = PlayerInteractionPolicy.volumeFromDrag(volumeStart, accumulated.y, size.height.toFloat())
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        PlayerInteractionPolicy.volumeIndex(fraction, max),
                                        0,
                                    )
                                    val actual = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max.toFloat()
                                    viewModel.updateVolume(actual)
                                }
                                else -> Unit
                            }
                        },
                    )
                }
                .pointerInput(
                    coordinator.controlsLocked,
                    coordinator.activeMenu,
                    coordinator.tutorialVisible,
                    coordinator.resumePositionMs,
                    coordinator.preparing,
                    coordinator.preferences.pinchZoomEnabled,
                    subtitleDialogVisible,
                    touchExploration,
                    localVideoProcessingAvailable,
                ) {
                    if (!localVideoProcessingAvailable || subtitleDialogVisible || surfaceInteractionBlocked(coordinator, touchExploration) || !coordinator.preferences.pinchZoomEnabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var transforming = false
                        do {
                            val event = awaitPointerEvent()
                            if (subtitleDialogVisible || surfaceInteractionBlocked(latestCoordinator.value, touchExploration)) break
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                transforming = true
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                viewModel.applyTransformForViewport(
                                    zoomMultiplier = zoomChange,
                                    panDx = panChange.x,
                                    panDy = panChange.y,
                                    viewportWidthPx = latestViewport.value.width.toFloat(),
                                    viewportHeightPx = latestViewport.value.height.toFloat(),
                                )
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (transforming) viewModel.endInteraction()
                    }
                },
        )

        playback.error?.let { error ->
            PlaybackErrorOverlay(
                error = error,
                onRetry = viewModel::retry,
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        PlayerControlsOverlay(
            coordinator = coordinator,
            playback = playback,
            fallbackTitle = currentMedia.title,
            localVideoProcessingAvailable = localVideoProcessingAvailable,
            subtitlePanelVisible = subtitleDialogVisible,
            onBack = onBack,
            onPlayPause = ::togglePlayback,
            onPrevious = { playbackConnection.seekToPrevious(); viewModel.showControls() },
            onNext = { playbackConnection.seekToNext(); viewModel.showControls() },
            onGoLive = playbackConnection::goLive,
            onSeekPreview = viewModel::updateSeekGesture,
            onSeekCommit = viewModel::commitSeek,
            onInteractionStart = { viewModel.beginInteraction(PlayerGestureKind.SEEK) },
            onInteractionEnd = viewModel::endInteraction,
            onOpenMenu = { menu ->
                if (localVideoProcessingAvailable || menu !in setOf(PlayerMenu.DECODER, PlayerMenu.DISPLAY)) {
                    viewModel.openMenu(menu)
                }
            },
            onSubtitles = {
                viewModel.showControls()
                subtitleDialogVisible = true
            },
            onRotate = { if (localVideoProcessingAvailable) viewModel.rotateDisplay() },
            onLock = viewModel::lockControls,
            onUnlock = viewModel::unlockControls,
            onPip = { onEnterPip(currentMedia) },
            onFullscreen = {
                val next = !coordinator.fullscreen
                viewModel.setFullscreen(next)
                onFullscreenChanged(next)
            },
        )

        if (isTelevision && coordinator.controlsVisible && !coordinator.controlsLocked && !coordinator.tutorialVisible && coordinator.resumePositionMs == null) {
            TvPlayerShortcutBar(
                localVideoProcessingAvailable = localVideoProcessingAvailable,
                onSubtitles = {
                    viewModel.showControls()
                    subtitleDialogVisible = true
                },
                onAudio = onAudioControls,
                onDecoder = { if (localVideoProcessingAvailable) viewModel.openMenu(PlayerMenu.DECODER) },
                onQueue = { queueDialogVisible = true },
                onSettings = { viewModel.openMenu(PlayerMenu.SETTINGS) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 54.dp),
            )
        }

        val dialogHostModifier = if (!isTelevision && coordinator.activeMenu == PlayerMenu.SETTINGS) {
            Modifier
                .fillMaxSize()
                .focusRequester(morePanelFocusRequester)
                .focusable()
                .testTag("more_panel_focus_host")
                .semantics { contentDescription = "More playback tools panel" }
        } else {
            Modifier.fillMaxSize()
        }
        Box(dialogHostModifier) {
            PlayerDialogs(
                coordinator = coordinator,
                playback = playback,
                media = currentMedia,
                onDismissMenu = viewModel::closeMenu,
                onSpeed = viewModel::setPlaybackSpeed,
                onRepeatMode = playbackConnection::setRepeatMode,
                onShuffle = playbackConnection::setShuffleEnabled,
                onVideoQualityAuto = playbackConnection::selectVideoQualityAuto,
                onVideoTrack = playbackConnection::selectVideoTrack,
                onDecoderMode = { mode -> if (localVideoProcessingAvailable) viewModel.setDecoderMode(mode) },
                onUseGlobalDecoder = { if (localVideoProcessingAvailable) viewModel.useGlobalDecoderForCurrentMedia() },
                onDefaultDecoderMode = { mode -> if (localVideoProcessingAvailable) viewModel.setDefaultDecoderMode(mode) },
                onRememberDecoderPerVideo = viewModel::setRememberDecoderPerVideo,
                onShowDecoderDiagnostics = viewModel::setShowDecoderDiagnostics,
                onResetDecoderPreferences = viewModel::resetDecoderPreferences,
                onResize = { mode -> if (localVideoProcessingAvailable) viewModel.setResizeMode(mode) },
                onCustomAspect = { width, height -> localVideoProcessingAvailable && viewModel.setCustomAspect(width, height) },
                onResetZoom = { if (localVideoProcessingAvailable) viewModel.resetZoom() },
                onRotate = { if (localVideoProcessingAvailable) viewModel.rotateDisplay() },
                onOrientation = viewModel::setOrientationMode,
                onDoubleTapSeconds = viewModel::setDoubleTapSeekSeconds,
                onSensitivity = viewModel::setGestureSensitivity,
                onHorizontalSeekEnabled = viewModel::setHorizontalSeekEnabled,
                onBrightnessEnabled = viewModel::setBrightnessGestureEnabled,
                onVolumeEnabled = viewModel::setVolumeGestureEnabled,
                onPinchEnabled = viewModel::setPinchZoomEnabled,
                onAutoHideMillis = viewModel::setAutoHideMillis,
                onRememberSpeed = viewModel::setRememberPlaybackSpeed,
                onAutoPip = viewModel::setAutoPip,
                onShowTutorial = viewModel::showTutorial,
                onDismissTutorial = viewModel::dismissTutorial,
            )
        }

        if (queueDialogVisible) {
            PlayerQueueDialog(
                playback = playback,
                onDismiss = { queueDialogVisible = false },
                onPrevious = playbackConnection::seekToPrevious,
                onNext = playbackConnection::seekToNext,
            )
        }

        if (subtitleDialogVisible) {
            val subtitleHostModifier = if (!isTelevision) {
                Modifier
                    .fillMaxSize()
                    .focusRequester(subtitlePanelFocusRequester)
                    .focusable()
                    .testTag("subtitle_panel_focus_host")
                    .semantics { contentDescription = "Subtitle tracks panel" }
            } else {
                Modifier.fillMaxSize()
            }
            Box(subtitleHostModifier) {
                SubtitleDialog(
                    playback = playback,
                    style = subtitleStyle,
                    onDismiss = { subtitleDialogVisible = false },
                    onEnabled = playbackConnection::setSubtitlesEnabled,
                    onAuto = playbackConnection::selectSubtitleAuto,
                    onTrack = playbackConnection::selectSubtitleTrack,
                    onLoadExternal = { subtitlePicker.launch(SubtitleFormatPolicy.supportedPickerMimeTypes()) },
                    onRemoveExternal = playbackConnection::clearExternalSubtitle,
                    onTextScale = subtitleRepository::setTextScale,
                    onBottomPadding = subtitleRepository::setBottomPaddingFraction,
                    onEdgeStyle = subtitleRepository::setEdgeStyle,
                    onForegroundColor = subtitleRepository::setForegroundColor,
                    onBackgroundColor = subtitleRepository::setBackgroundColor,
                    onApplyEmbeddedStyles = subtitleRepository::setApplyEmbeddedStyles,
                    onApplyEmbeddedFontSizes = subtitleRepository::setApplyEmbeddedFontSizes,
                    onResetStyle = subtitleRepository::resetStyle,
                )
            }
        }

        subtitleLoadError?.let { message ->
            AlertDialog(
                onDismissRequest = { subtitleLoadError = null },
                title = { Text("Subtitle file") },
                text = { Text(message) },
                confirmButton = { Button(onClick = { subtitleLoadError = null }) { Text("OK") } },
            )
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun PlayerVideoSurface(
    media: AppMedia,
    playbackConnection: PlaybackConnection,
    coordinator: PlayerCoordinatorState,
    subtitleStyle: SubtitleStyleState,
    viewportSize: IntSize,
    localVideoProcessingAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                player = playbackConnection.playerOrNull()
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                clipChildren = true
            }
        },
        update = { playerView ->
            playerView.player = playbackConnection.playerOrNull()
            playerView.resizeMode = if (!localVideoProcessingAvailable) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            } else {
                when (coordinator.resizeMode) {
                    ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    ResizeMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
            playerView.subtitleView?.applySubtitleStyle(subtitleStyle)
            val transform = if (!localVideoProcessingAvailable) {
                VideoTransform(1f, 1f, 0f, 0f, 0f)
            } else {
                PlayerInteractionPolicy.transform(
                    resizeMode = coordinator.resizeMode,
                    customAspectRatio = coordinator.customAspectRatio,
                    sourceWidth = media.width,
                    sourceHeight = media.height,
                    sourceRotationDegrees = media.rotationDegrees,
                    manualZoom = coordinator.zoom,
                    panX = coordinator.panX,
                    panY = coordinator.panY,
                    displayRotationDegrees = coordinator.displayRotationDegrees,
                    viewportWidthPx = viewportSize.width.toFloat(),
                    viewportHeightPx = viewportSize.height.toFloat(),
                )
            }
            playerView.videoSurfaceView?.let { surface ->
                surface.pivotX = surface.width / 2f
                surface.pivotY = surface.height / 2f
                surface.scaleX = transform.scaleX
                surface.scaleY = transform.scaleY
                surface.translationX = transform.translationX
                surface.translationY = transform.translationY
                surface.rotation = transform.rotationDegrees
            }
        },
        modifier = modifier,
    )
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private fun SubtitleView.applySubtitleStyle(style: SubtitleStyleState) {
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.textScale.coerceIn(0.5f, 2f))
    setBottomPaddingFraction(style.bottomPaddingFraction.coerceIn(0f, 0.35f))
    setApplyEmbeddedStyles(style.applyEmbeddedStyles)
    setApplyEmbeddedFontSizes(style.applyEmbeddedFontSizes)
    setStyle(
        CaptionStyleCompat(
            style.foregroundColor,
            style.backgroundColor,
            style.windowColor,
            when (style.edgeStyle) {
                SubtitleEdgeStyle.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
                SubtitleEdgeStyle.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                SubtitleEdgeStyle.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                SubtitleEdgeStyle.RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
                SubtitleEdgeStyle.DEPRESSED -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
            },
            style.edgeColor,
            null,
        ),
    )
}

@Composable
private fun PlaybackErrorOverlay(
    error: PlaybackError,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(24.dp).testTag("playback_error"),
        color = Color.Black.copy(alpha = 0.82f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(errorMessage(error), color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}

private fun surfaceInteractionBlocked(state: PlayerCoordinatorState, touchExploration: Boolean): Boolean =
    touchExploration || state.controlsLocked || state.activeMenu != PlayerMenu.NONE || state.tutorialVisible || state.resumePositionMs != null || state.preparing

private fun readSystemBrightnessFraction(context: Context): Float = runCatching {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        .coerceIn(1, 255) / 255f
}.getOrDefault(0.5f)

private fun setWindowBrightness(activity: Activity, value: Float) {
    val attrs = activity.window.attributes
    attrs.screenBrightness = if (value < 0f) -1f else value.coerceIn(0.01f, 1f)
    activity.window.attributes = attrs
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun errorMessage(error: PlaybackError): String = when (error) {
    PlaybackError.SourceUnavailable -> "The media source is no longer available."
    PlaybackError.PermissionLost -> "Permission to this media source was lost. Re-open it from the library."
    PlaybackError.UnsupportedFormat -> "This media container or manifest is not supported by the current decoder path."
    PlaybackError.UnsupportedDecoder -> "This device does not expose a compatible decoder for this stream."
    PlaybackError.MalformedMedia -> "The media appears malformed or corrupted."
    PlaybackError.Network -> "Network playback failed. Check the connection and URL."
    PlaybackError.NetworkTimeout -> "The network server did not respond in time."
    PlaybackError.SecureConnection -> "Secure connection could not be verified. Check the certificate and server name."
    PlaybackError.AuthenticationRequired -> "Authentication is required for this source."
    PlaybackError.NetworkAccessDenied -> "The server denied access to this media."
    PlaybackError.NetworkMediaNotFound -> "The network media was not found."
    PlaybackError.RangeRejected -> "The server rejected the requested seek position. Try playing from the start."
    PlaybackError.ServerThrottling -> "The server is limiting requests. Wait briefly and retry."
    is PlaybackError.NetworkServer -> "The media server returned an error (${error.statusCode})."
    PlaybackError.DecoderInitialization -> "The decoder could not be initialized on this device."
    is PlaybackError.Failure -> "Playback failed (diagnostic code ${error.diagnosticCode ?: -1})."
    PlaybackError.Unknown -> "Playback failed for an unknown reason."
}
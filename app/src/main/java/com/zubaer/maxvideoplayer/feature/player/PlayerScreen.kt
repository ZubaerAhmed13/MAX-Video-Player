package com.zubaer.maxvideoplayer.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.PlaybackError
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun PlayerScreen(
    media: AppMedia,
    viewModel: PlayerViewModel,
    playbackConnection: PlaybackConnection,
    onBack: () -> Unit,
    onEnterPip: (AppMedia) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    onOrientationModeChanged: (OrientationMode) -> Unit,
    onPlayerHostStateChanged: (AppMedia?, Boolean) -> Unit,
) {
    val coordinator by viewModel.state.collectAsStateWithLifecycle()
    val playback by playbackConnection.state.collectAsStateWithLifecycle()
    val currentMedia = viewModel.mediaForPlaybackId(playback.mediaId)
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val accessibilityManager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var touchExploration by remember(accessibilityManager) {
        mutableStateOf(accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled)
    }
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }

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

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { setWindowBrightness(it, originalBrightness) }
            onFullscreenChanged(false)
            onOrientationModeChanged(OrientationMode.AUTO)
            onPlayerHostStateChanged(null, false)
        }
    }

    BackHandler {
        when {
            coordinator.resumePositionMs != null -> onBack()
            coordinator.tutorialVisible -> viewModel.dismissTutorial()
            coordinator.activeMenu != PlayerMenu.NONE -> viewModel.closeMenu()
            coordinator.controlsLocked -> viewModel.unlockControls()
            coordinator.fullscreen -> {
                viewModel.setFullscreen(false)
                onFullscreenChanged(false)
            }
            else -> onBack()
        }
    }

    coordinator.resumePositionMs?.let { position ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Resume playback?") },
            text = { Text("Continue from ${formatPlayerTime(position)} or start from the beginning.") },
            confirmButton = { Button(onClick = viewModel::resume) { Text("Resume") } },
            dismissButton = { TextButton(onClick = viewModel::startOver) { Text("Start over") } },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        var viewportSize by remember { mutableStateOf(IntSize.Zero) }
        val latestCoordinator = rememberUpdatedState(coordinator)
        val latestPlayback = rememberUpdatedState(playback)
        val latestViewport = rememberUpdatedState(viewportSize)
        val viewConfiguration = LocalViewConfiguration.current

        PlayerVideoSurface(
            media = currentMedia,
            playbackConnection = playbackConnection,
            coordinator = coordinator,
            viewportSize = viewportSize,
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
                    touchExploration,
                ) {
                    if (surfaceInteractionBlocked(coordinator, touchExploration)) return@pointerInput
                    detectTapGestures(
                        onTap = { viewModel.onSurfaceTap() },
                        onDoubleTap = { offset ->
                            if (surfaceInteractionBlocked(latestCoordinator.value, touchExploration)) return@detectTapGestures
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
                    touchExploration,
                ) {
                    if (surfaceInteractionBlocked(coordinator, touchExploration)) return@pointerInput
                    var startOffset = Offset.Zero
                    var accumulated = Offset.Zero
                    var gestureKind = PlayerGestureKind.NONE
                    var seekStartMs = 0L
                    var seekTargetMs: Long? = null
                    var brightnessStart = 0.5f
                    var volumeStart = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            if (!surfaceInteractionBlocked(latestCoordinator.value, touchExploration)) {
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
                            if (gestureKind == PlayerGestureKind.SEEK && target != null && !surfaceInteractionBlocked(latestCoordinator.value, touchExploration)) {
                                viewModel.commitSeek(target)
                            } else {
                                viewModel.endInteraction()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val currentCoordinator = latestCoordinator.value
                            if (surfaceInteractionBlocked(currentCoordinator, touchExploration) || currentCoordinator.gestureKind == PlayerGestureKind.ZOOM) {
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
                                    volumeEnabled = currentCoordinator.preferences.volumeGestureEnabled,
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
                    touchExploration,
                ) {
                    if (surfaceInteractionBlocked(coordinator, touchExploration) || !coordinator.preferences.pinchZoomEnabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var transforming = false
                        do {
                            val event = awaitPointerEvent()
                            if (surfaceInteractionBlocked(latestCoordinator.value, touchExploration)) break
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
            onBack = onBack,
            onPlayPause = {
                when {
                    playback.playbackEnded -> {
                        playbackConnection.seekTo(0L)
                        playbackConnection.play()
                    }
                    playback.isPlaying -> playbackConnection.pause()
                    else -> playbackConnection.play()
                }
                viewModel.showControls()
            },
            onPrevious = { playbackConnection.seekToPrevious(); viewModel.showControls() },
            onNext = { playbackConnection.seekToNext(); viewModel.showControls() },
            onSeekPreview = viewModel::updateSeekGesture,
            onSeekCommit = viewModel::commitSeek,
            onInteractionStart = { viewModel.beginInteraction(PlayerGestureKind.SEEK) },
            onInteractionEnd = viewModel::endInteraction,
            onOpenMenu = viewModel::openMenu,
            onRotate = viewModel::rotateDisplay,
            onLock = viewModel::lockControls,
            onUnlock = viewModel::unlockControls,
            onPip = { onEnterPip(currentMedia) },
            onFullscreen = {
                val next = !coordinator.fullscreen
                viewModel.setFullscreen(next)
                onFullscreenChanged(next)
            },
        )

        PlayerDialogs(
            coordinator = coordinator,
            playback = playback,
            media = currentMedia,
            onDismissMenu = viewModel::closeMenu,
            onSpeed = viewModel::setPlaybackSpeed,
            onRepeatMode = playbackConnection::setRepeatMode,
            onShuffle = playbackConnection::setShuffleEnabled,
            onResize = viewModel::setResizeMode,
            onCustomAspect = viewModel::setCustomAspect,
            onResetZoom = viewModel::resetZoom,
            onRotate = viewModel::rotateDisplay,
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
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun PlayerVideoSurface(
    media: AppMedia,
    playbackConnection: PlaybackConnection,
    coordinator: PlayerCoordinatorState,
    viewportSize: IntSize,
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
            playerView.resizeMode = when (coordinator.resizeMode) {
                ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                ResizeMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            val transform = PlayerInteractionPolicy.transform(
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
    PlaybackError.DecoderInitialization -> "The decoder could not be initialized on this device."
    is PlaybackError.Failure -> "Playback failed (diagnostic code ${error.diagnosticCode ?: -1})."
    PlaybackError.Unknown -> "Playback failed for an unknown reason."
}

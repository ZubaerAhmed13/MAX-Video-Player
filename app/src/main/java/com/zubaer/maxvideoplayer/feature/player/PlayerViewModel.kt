package com.zubaer.maxvideoplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zubaer.maxvideoplayer.core.database.PlaybackHistoryRepository
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.ResumeAction
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(
    private val media: AppMedia,
    private val historyRepository: PlaybackHistoryRepository,
    private val playbackConnection: PlaybackConnection,
    private val preferences: PlayerPreferences,
    private val queue: List<AppMedia> = listOf(media),
    private val startIndex: Int = 0,
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerCoordinatorState())
    val state: StateFlow<PlayerCoordinatorState> = _state.asStateFlow()
    private var autoHideJob: Job? = null
    private var hudHideJob: Job? = null
    private var tutorialChecked = false

    init {
        playbackConnection.connect()
        viewModelScope.launch {
            preferences.state.collect { pref ->
                val previous = _state.value
                val firstPreferenceLoad = previous.preferences == PlayerPreferencesState()
                _state.value = previous.copy(
                    preferences = pref,
                    resizeMode = if (firstPreferenceLoad) pref.defaultResizeMode else previous.resizeMode,
                    orientationMode = if (firstPreferenceLoad) pref.orientationMode else previous.orientationMode,
                    tutorialVisible = previous.tutorialVisible || (!tutorialChecked && !pref.tutorialSeen),
                )
                tutorialChecked = true
                scheduleAutoHideIfNeeded()
            }
        }
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { historyRepository.get(media.stableId) }
            val decision = historyRepository.resumeDecision(history)
            if (decision.action == ResumeAction.OFFER_RESUME) {
                _state.value = _state.value.copy(preparing = false, resumePositionMs = decision.positionMs)
            } else {
                loadAt(0L)
            }
        }
    }

    fun resume() {
        val position = _state.value.resumePositionMs ?: 0L
        _state.value = _state.value.copy(preparing = true, resumePositionMs = null)
        viewModelScope.launch { loadAt(position) }
    }

    fun startOver() {
        _state.value = _state.value.copy(preparing = true, resumePositionMs = null)
        viewModelScope.launch { loadAt(0L) }
    }

    fun retry() {
        playbackConnection.retry()
        showControls()
    }

    fun setAccessibilityMode(enabled: Boolean) {
        _state.value = _state.value.copy(accessibilityMode = enabled, controlsVisible = if (enabled) true else _state.value.controlsVisible)
        scheduleAutoHideIfNeeded()
    }

    fun onPlaybackPlayingChanged(isPlaying: Boolean) {
        if (_state.value.isPlaying == isPlaying) return
        _state.value = _state.value.copy(isPlaying = isPlaying, controlsVisible = if (!isPlaying) true else _state.value.controlsVisible)
        scheduleAutoHideIfNeeded()
    }

    fun onSurfaceTap() {
        val current = _state.value
        if (current.controlsLocked) {
            _state.value = current.copy(unlockVisible = true)
            return
        }
        _state.value = current.copy(controlsVisible = !current.controlsVisible)
        scheduleAutoHideIfNeeded()
    }

    fun showControls() {
        if (_state.value.controlsLocked) return
        _state.value = _state.value.copy(controlsVisible = true)
        scheduleAutoHideIfNeeded()
    }

    fun beginInteraction(kind: PlayerGestureKind = PlayerGestureKind.NONE) {
        if (_state.value.controlsLocked) return
        autoHideJob?.cancel()
        _state.value = _state.value.copy(
            interactionInProgress = true,
            gestureKind = kind,
            controlsVisible = true,
        )
    }

    fun updateGestureKind(kind: PlayerGestureKind) {
        if (_state.value.controlsLocked) return
        _state.value = _state.value.copy(interactionInProgress = true, gestureKind = kind)
    }

    fun endInteraction() {
        _state.value = _state.value.copy(interactionInProgress = false, gestureKind = PlayerGestureKind.NONE, seekTargetMs = null)
        scheduleHudHide()
        scheduleAutoHideIfNeeded()
    }

    fun updateSeekGesture(fromMs: Long, targetMs: Long) {
        if (_state.value.controlsLocked) return
        _state.value = _state.value.copy(
            interactionInProgress = true,
            gestureKind = PlayerGestureKind.SEEK,
            seekTargetMs = targetMs,
            hud = PlayerHudState.Seek(fromMs, targetMs),
        )
    }

    fun commitSeek(targetMs: Long) {
        playbackConnection.seekTo(targetMs)
        _state.value = _state.value.copy(seekTargetMs = null)
        endInteraction()
    }

    fun doubleTap(zone: DoubleTapZone, currentPositionMs: Long, durationMs: Long) {
        if (_state.value.controlsLocked) return
        if (zone == DoubleTapZone.CENTER) {
            if (_state.value.isPlaying) playbackConnection.pause() else playbackConnection.play()
            showControls()
            return
        }
        val target = PlayerInteractionPolicy.doubleTapTargetMs(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            zone = zone,
            seekSeconds = _state.value.preferences.doubleTapSeekSeconds,
        )
        playbackConnection.seekTo(target)
        showHud(PlayerHudState.Seek(currentPositionMs, target))
    }

    fun updateBrightness(fraction: Float) {
        if (_state.value.controlsLocked) return
        val safe = fraction.coerceIn(0.01f, 1f)
        _state.value = _state.value.copy(
            brightnessFraction = safe,
            interactionInProgress = true,
            gestureKind = PlayerGestureKind.BRIGHTNESS,
            hud = PlayerHudState.Brightness(safe),
        )
    }

    fun updateVolume(fraction: Float) {
        if (_state.value.controlsLocked) return
        val safe = fraction.coerceIn(0f, 1f)
        _state.value = _state.value.copy(
            volumeFraction = safe,
            interactionInProgress = true,
            gestureKind = PlayerGestureKind.VOLUME,
            hud = PlayerHudState.Volume(safe),
        )
    }

    fun applyTransformForViewport(zoomMultiplier: Float, panDx: Float, panDy: Float, viewportWidthPx: Float, viewportHeightPx: Float) {
        if (_state.value.controlsLocked || !_state.value.preferences.pinchZoomEnabled) return
        autoHideJob?.cancel()
        val current = _state.value
        val nextZoom = PlayerInteractionPolicy.zoom(current.zoom, zoomMultiplier)
        val bounds = PlayerInteractionPolicy.panBounds(viewportWidthPx, viewportHeightPx, nextZoom)
        val (nextX, nextY) = PlayerInteractionPolicy.clampPan(current.panX + panDx, current.panY + panDy, bounds)
        _state.value = current.copy(
            interactionInProgress = true,
            gestureKind = if (zoomMultiplier != 1f) PlayerGestureKind.ZOOM else PlayerGestureKind.PAN,
            zoom = nextZoom,
            panX = if (nextZoom <= 1.001f) 0f else nextX,
            panY = if (nextZoom <= 1.001f) 0f else nextY,
            hud = PlayerHudState.Zoom(nextZoom),
        )
    }

    fun resetZoom() {
        _state.value = _state.value.copy(zoom = 1f, panX = 0f, panY = 0f, hud = PlayerHudState.Zoom(1f))
        scheduleHudHide()
    }

    fun setResizeMode(mode: ResizeMode) {
        preferences.setDefaultResizeMode(mode)
        _state.value = _state.value.copy(resizeMode = mode, zoom = 1f, panX = 0f, panY = 0f)
        showControls()
    }

    fun setCustomAspect(width: Float, height: Float): Boolean {
        val ratio = PlayerInteractionPolicy.validAspectRatio(width, height) ?: return false
        _state.value = _state.value.copy(customAspectRatio = ratio, resizeMode = ResizeMode.CUSTOM, zoom = 1f, panX = 0f, panY = 0f)
        preferences.setDefaultResizeMode(ResizeMode.CUSTOM)
        return true
    }

    fun rotateDisplay() {
        val next = (_state.value.displayRotationDegrees + 90) % 360
        _state.value = _state.value.copy(displayRotationDegrees = next, zoom = 1f, panX = 0f, panY = 0f)
        showControls()
    }

    fun setOrientationMode(mode: OrientationMode) {
        preferences.setOrientationMode(mode)
        _state.value = _state.value.copy(orientationMode = mode)
        showControls()
    }

    fun setFullscreen(enabled: Boolean) {
        _state.value = _state.value.copy(fullscreen = enabled)
        showControls()
    }

    fun lockControls() {
        autoHideJob?.cancel()
        _state.value = _state.value.copy(
            controlsLocked = true,
            controlsVisible = false,
            unlockVisible = true,
            activeMenu = PlayerMenu.NONE,
            interactionInProgress = false,
            gestureKind = PlayerGestureKind.NONE,
        )
    }

    fun unlockControls() {
        _state.value = _state.value.copy(controlsLocked = false, controlsVisible = true, unlockVisible = false)
        scheduleAutoHideIfNeeded()
    }

    fun openMenu(menu: PlayerMenu) {
        if (_state.value.controlsLocked) return
        autoHideJob?.cancel()
        _state.value = _state.value.copy(activeMenu = menu, controlsVisible = true)
    }

    fun closeMenu() {
        _state.value = _state.value.copy(activeMenu = PlayerMenu.NONE)
        scheduleAutoHideIfNeeded()
    }

    fun setPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(0.25f, 4f)
        playbackConnection.setPlaybackSpeed(safe)
        if (_state.value.preferences.rememberPlaybackSpeed) preferences.setRememberedPlaybackSpeed(safe)
        showControls()
    }

    fun setRememberPlaybackSpeed(enabled: Boolean) {
        preferences.setRememberPlaybackSpeed(enabled)
    }

    fun setDoubleTapSeekSeconds(seconds: Int) = preferences.setDoubleTapSeekSeconds(seconds)
    fun setGestureSensitivity(value: GestureSensitivity) = preferences.setGestureSensitivity(value)
    fun setHorizontalSeekEnabled(value: Boolean) = preferences.setHorizontalSeekEnabled(value)
    fun setBrightnessGestureEnabled(value: Boolean) = preferences.setBrightnessGestureEnabled(value)
    fun setVolumeGestureEnabled(value: Boolean) = preferences.setVolumeGestureEnabled(value)
    fun setPinchZoomEnabled(value: Boolean) = preferences.setPinchZoomEnabled(value)
    fun setAutoHideMillis(value: Long) = preferences.setAutoHideMillis(value)
    fun setAutoPip(value: Boolean) = preferences.setAutoPip(value)

    fun dismissTutorial() {
        preferences.setTutorialSeen(true)
        _state.value = _state.value.copy(tutorialVisible = false)
    }

    fun showTutorial() {
        _state.value = _state.value.copy(tutorialVisible = true)
        openMenu(PlayerMenu.NONE)
    }

    private fun showHud(hud: PlayerHudState) {
        hudHideJob?.cancel()
        _state.value = _state.value.copy(hud = hud)
        scheduleHudHide()
    }

    private fun scheduleHudHide() {
        hudHideJob?.cancel()
        hudHideJob = viewModelScope.launch {
            delay(850L)
            if (!_state.value.interactionInProgress) _state.value = _state.value.copy(hud = PlayerHudState.Hidden)
        }
    }

    private fun scheduleAutoHideIfNeeded() {
        autoHideJob?.cancel()
        val current = _state.value
        if (!PlayerInteractionPolicy.shouldAutoHideControls(
                isPlaying = current.isPlaying,
                controlsLocked = current.controlsLocked,
                controlsVisible = current.controlsVisible,
                interactionInProgress = current.interactionInProgress,
                activeMenu = current.activeMenu,
                tutorialVisible = current.tutorialVisible,
                accessibilityMode = current.accessibilityMode,
            )
        ) return
        autoHideJob = viewModelScope.launch {
            delay(current.preferences.autoHideMillis)
            val latest = _state.value
            if (PlayerInteractionPolicy.shouldAutoHideControls(
                    isPlaying = latest.isPlaying,
                    controlsLocked = latest.controlsLocked,
                    controlsVisible = latest.controlsVisible,
                    interactionInProgress = latest.interactionInProgress,
                    activeMenu = latest.activeMenu,
                    tutorialVisible = latest.tutorialVisible,
                    accessibilityMode = latest.accessibilityMode,
                )
            ) {
                _state.value = latest.copy(controlsVisible = false)
            }
        }
    }

    private suspend fun loadAt(positionMs: Long) {
        playbackConnection.connect()
        playbackConnection.state.filter { it.connected }.first()
        val safeQueue = queue.ifEmpty { listOf(media) }
        val effectiveIndex = safeQueue.indexOfFirst { it.stableId == media.stableId }
            .takeIf { it >= 0 }
            ?: startIndex.coerceIn(safeQueue.indices)
        if (safeQueue.size == 1) {
            playbackConnection.load(media, positionMs, playWhenReady = true)
        } else {
            playbackConnection.setQueue(safeQueue, effectiveIndex, positionMs, playWhenReady = true)
        }
        val pref = preferences.state.value
        playbackConnection.setPlaybackSpeed(if (pref.rememberPlaybackSpeed) pref.rememberedPlaybackSpeed else 1f)
        _state.value = _state.value.copy(preparing = false, resumePositionMs = null)
        scheduleAutoHideIfNeeded()
    }
}

package com.zubaer.maxvideoplayer.feature.output

import android.app.Activity
import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
)

sealed interface OutputDeviceState {
    data object Local : OutputDeviceState
    data class External(val display: ExternalDisplayInfo) : OutputDeviceState
    data class Cast(val name: String? = null) : OutputDeviceState
}

data class OutputDeviceUiState(
    val availableExternalDisplays: List<ExternalDisplayInfo> = emptyList(),
    val active: OutputDeviceState = OutputDeviceState.Local,
    val error: String? = null,
)

/**
 * Activity-owned external-display router. It never creates a second playback engine. The
 * Presentation's PlayerView is attached to the same service-owned MediaController exposed by
 * PlaybackConnection. While external output owns the renderer, PlayerViews in the Activity window
 * are actively kept detached so Compose recomposition cannot steal the video surface back.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class ExternalDisplayController(
    private val activity: Activity,
    private val playbackConnection: PlaybackConnection,
) : DisplayManager.DisplayListener {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private val _state = MutableStateFlow(OutputDeviceUiState())
    val state: StateFlow<OutputDeviceUiState> = _state.asStateFlow()

    private var presentation: VideoPresentation? = null
    private var boundPlayer: Player? = null
    private var started = false
    private var phoneSurfaceGuard: ViewTreeObserver.OnPreDrawListener? = null

    private val playerListener = object : Player.Listener {
        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                dismissPresentation(updateState = false, reattachPhone = false)
                _state.value = _state.value.copy(active = OutputDeviceState.Cast(), error = null)
            } else if (_state.value.active is OutputDeviceState.Cast) {
                _state.value = _state.value.copy(active = OutputDeviceState.Local, error = null)
                restorePhonePlayerViews()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        displayManager?.registerDisplayListener(this, null)
        refreshDisplays()
        refreshPlayerBinding()
    }

    fun stop() {
        if (!started) return
        started = false
        displayManager?.unregisterDisplayListener(this)
        dismissPresentation(updateState = false, reattachPhone = false)
        removePhoneSurfaceGuard(reattachPhone = false)
        boundPlayer?.removeListener(playerListener)
        boundPlayer = null
        _state.value = OutputDeviceUiState()
    }

    /** Call when the session connection changes so the listener always follows the same controller. */
    fun refreshPlayerBinding() {
        val next = playbackConnection.playerOrNull()
        if (boundPlayer === next) return
        boundPlayer?.removeListener(playerListener)
        boundPlayer = next
        next?.addListener(playerListener)
        if (next?.deviceInfo?.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            dismissPresentation(updateState = false, reattachPhone = false)
            _state.value = _state.value.copy(active = OutputDeviceState.Cast(), error = null)
        } else if (_state.value.active is OutputDeviceState.Cast) {
            _state.value = _state.value.copy(active = OutputDeviceState.Local, error = null)
            restorePhonePlayerViews()
        }
    }

    fun playOnExternalDisplay(displayId: Int): Boolean {
        refreshPlayerBinding()
        val player = boundPlayer
        if (player == null) {
            _state.value = _state.value.copy(error = "Playback is not connected yet.")
            return false
        }
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            _state.value = _state.value.copy(
                active = OutputDeviceState.Cast(),
                error = "Disconnect Cast before moving video to a wired or wireless external display.",
            )
            return false
        }
        val display = presentationDisplays().firstOrNull { it.displayId == displayId }
        if (display == null) {
            _state.value = _state.value.copy(error = "That external display is no longer available.")
            refreshDisplays()
            return false
        }

        dismissPresentation(updateState = false, reattachPhone = false)
        val info = ExternalDisplayInfo(display.displayId, display.name)
        return runCatching {
            installPhoneSurfaceGuard()
            VideoPresentation(activity, display, player).also { externalPresentation ->
                presentation = externalPresentation
                externalPresentation.setOnDismissListener {
                    if (presentation === externalPresentation) {
                        presentation = null
                        removePhoneSurfaceGuard(
                            reattachPhone = boundPlayer?.deviceInfo?.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE,
                        )
                        if (_state.value.active is OutputDeviceState.External) {
                            _state.value = _state.value.copy(active = OutputDeviceState.Local, error = null)
                        }
                    }
                }
                externalPresentation.show()
            }
            _state.value = _state.value.copy(active = OutputDeviceState.External(info), error = null)
            true
        }.getOrElse { error ->
            presentation = null
            removePhoneSurfaceGuard(reattachPhone = true)
            _state.value = _state.value.copy(
                active = OutputDeviceState.Local,
                error = error.message ?: "External display could not be opened.",
            )
            false
        }
    }

    fun returnToPhone() {
        dismissPresentation(updateState = false, reattachPhone = true)
        _state.value = _state.value.copy(active = OutputDeviceState.Local, error = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onDisplayAdded(displayId: Int) = refreshDisplays()

    override fun onDisplayChanged(displayId: Int) = refreshDisplays()

    override fun onDisplayRemoved(displayId: Int) {
        val active = _state.value.active
        if (active is OutputDeviceState.External && active.display.displayId == displayId) {
            dismissPresentation(updateState = false, reattachPhone = true)
            _state.value = _state.value.copy(
                active = OutputDeviceState.Local,
                error = "External display disconnected. Video returned to the phone.",
            )
        }
        refreshDisplays(preserveError = true)
    }

    private fun refreshDisplays(preserveError: Boolean = false) {
        val outputs = presentationDisplays().map { ExternalDisplayInfo(it.displayId, it.name) }
        val active = _state.value.active
        val stillPresent = active !is OutputDeviceState.External || outputs.any { it.displayId == active.display.displayId }
        _state.value = _state.value.copy(
            availableExternalDisplays = outputs,
            active = if (stillPresent) active else OutputDeviceState.Local,
            error = if (preserveError) _state.value.error else null,
        )
    }

    private fun presentationDisplays(): List<Display> =
        displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            ?.filter { display -> display.displayId != Display.DEFAULT_DISPLAY && display.isValid }
            .orEmpty()

    /**
     * Compose's AndroidView update block may run while the Presentation is active. Guard the
     * Activity window every frame so any phone PlayerView that gets reattached is immediately
     * detached before drawing. The Presentation lives in a different window and is unaffected.
     */
    private fun installPhoneSurfaceGuard() {
        val root = activity.window.decorView
        detachPlayerViews(root)
        if (phoneSurfaceGuard != null) return
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (_state.value.active is OutputDeviceState.External || presentation != null) {
                detachPlayerViews(activity.window.decorView)
            }
            true
        }
        phoneSurfaceGuard = listener
        root.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun removePhoneSurfaceGuard(reattachPhone: Boolean) {
        val root = activity.window.decorView
        phoneSurfaceGuard?.let { listener ->
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }
        phoneSurfaceGuard = null
        if (reattachPhone) restorePhonePlayerViews()
    }

    private fun restorePhonePlayerViews() {
        val player = boundPlayer ?: return
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return
        attachPlayerViews(activity.window.decorView, player)
    }

    private fun detachPlayerViews(view: View) {
        when (view) {
            is PlayerView -> if (view.player != null) view.player = null
            is ViewGroup -> for (index in 0 until view.childCount) detachPlayerViews(view.getChildAt(index))
        }
    }

    private fun attachPlayerViews(view: View, player: Player) {
        when (view) {
            is PlayerView -> if (view.player !== player) view.player = player
            is ViewGroup -> for (index in 0 until view.childCount) attachPlayerViews(view.getChildAt(index), player)
        }
    }

    private fun dismissPresentation(
        updateState: Boolean = true,
        reattachPhone: Boolean = true,
    ) {
        val current = presentation
        presentation = null
        if (current != null) runCatching { current.dismiss() }
        removePhoneSurfaceGuard(reattachPhone = reattachPhone)
        if (updateState && _state.value.active is OutputDeviceState.External) {
            _state.value = _state.value.copy(active = OutputDeviceState.Local)
        }
    }

    private class VideoPresentation(
        activity: Activity,
        display: Display,
        private val player: Player,
    ) : Presentation(activity, display) {
        private var playerView: PlayerView? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val view = PlayerView(context).apply {
                useController = false
                keepScreenOn = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.player = this@VideoPresentation.player
            }
            playerView = view
            setContentView(view)
        }

        override fun onStop() {
            playerView?.player = null
            playerView = null
            super.onStop()
        }
    }
}

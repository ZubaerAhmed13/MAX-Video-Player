package com.zubaer.maxvideoplayer.feature.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Host-owned actions that belong visually inside the single release player chrome.
 * The playback screen remains feature-agnostic while wrappers can expose production actions
 * without drawing competing overlays on top of the player.
 */
@Immutable
data class PlayerChromeHostState(
    val onAudio: (() -> Unit)? = null,
    val audioPanelVisible: Boolean = false,
)

val LocalPlayerChromeHostState = staticCompositionLocalOf { PlayerChromeHostState() }

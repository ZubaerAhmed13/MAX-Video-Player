package com.zubaer.maxvideoplayer.feature.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerControlsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun professionalControlsExposePrimaryActions() {
        var playClicked = false
        composeRule.setContent {
            MaterialTheme {
                PlayerControlsOverlay(
                    coordinator = PlayerCoordinatorState(controlsVisible = true),
                    playback = PlaybackUiState(
                        connected = true,
                        title = "Test Video",
                        durationMs = 60_000L,
                        currentPositionMs = 12_000L,
                        hasPrevious = true,
                        hasNext = true,
                        mediaItemCount = 3,
                        currentMediaItemIndex = 1,
                    ),
                    fallbackTitle = "Fallback",
                    onBack = {},
                    onPlayPause = { playClicked = true },
                    onPrevious = {},
                    onNext = {},
                    onSeekPreview = { _, _ -> },
                    onSeekCommit = {},
                    onInteractionStart = {},
                    onInteractionEnd = {},
                    onOpenMenu = {},
                    onRotate = {},
                    onLock = {},
                    onUnlock = {},
                    onPip = {},
                    onFullscreen = {},
                )
            }
        }

        composeRule.onNodeWithTag("seek_bar").assertExists()
        composeRule.onNodeWithTag("previous_button").assertExists()
        composeRule.onNodeWithTag("play_pause_button").assertExists().performClick()
        composeRule.onNodeWithTag("next_button").assertExists()
        composeRule.onNodeWithTag("speed_button").assertExists()
        composeRule.onNodeWithTag("display_button").assertExists()
        composeRule.onNodeWithTag("rotation_button").assertExists()
        composeRule.onNodeWithTag("orientation_button").assertExists()
        composeRule.onNodeWithTag("lock_button").assertExists()
        composeRule.onNodeWithTag("pip_button").assertExists()
        composeRule.onNodeWithTag("fullscreen_button").assertExists()
        composeRule.onNodeWithTag("subtitle_button").assertExists()
        composeRule.onNodeWithTag("decoder_button").assertExists()
        composeRule.onNodeWithTag("more_button").assertExists()
        composeRule.onNodeWithTag("extended_tool_rail").assertExists()
        assertTrue(playClicked)
    }

    @Test
    fun moreButtonRoutesToSingleSettingsPanel() {
        var requestedMenu = PlayerMenu.NONE
        composeRule.setContent {
            MaterialTheme {
                PlayerControlsOverlay(
                    coordinator = PlayerCoordinatorState(controlsVisible = true),
                    playback = PlaybackUiState(durationMs = 60_000L),
                    fallbackTitle = "More",
                    onBack = {}, onPlayPause = {}, onPrevious = {}, onNext = {},
                    onSeekPreview = { _, _ -> }, onSeekCommit = {},
                    onInteractionStart = {}, onInteractionEnd = {},
                    onOpenMenu = { requestedMenu = it },
                    onRotate = {}, onLock = {}, onUnlock = {}, onPip = {}, onFullscreen = {},
                )
            }
        }
        composeRule.onNodeWithTag("more_button").assertExists().performClick()
        composeRule.runOnIdle { assertEquals(PlayerMenu.SETTINGS, requestedMenu) }
    }

    @Test
    fun visibilityStateActuallyHidesAndRestoresControlOverlay() {
        var state by mutableStateOf(PlayerCoordinatorState(controlsVisible = true))
        composeRule.setContent {
            MaterialTheme {
                PlayerControlsOverlay(
                    coordinator = state,
                    playback = PlaybackUiState(durationMs = 60_000L),
                    fallbackTitle = "Visibility",
                    onBack = {}, onPlayPause = {}, onPrevious = {}, onNext = {},
                    onSeekPreview = { _, _ -> }, onSeekCommit = {},
                    onInteractionStart = {}, onInteractionEnd = {}, onOpenMenu = {},
                    onRotate = {}, onLock = {}, onUnlock = {}, onPip = {}, onFullscreen = {},
                )
            }
        }
        composeRule.onNodeWithTag("seek_bar").assertExists()
        composeRule.runOnIdle { state = state.copy(controlsVisible = false) }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("seek_bar").assertDoesNotExist()
        composeRule.runOnIdle { state = state.copy(controlsVisible = true) }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("seek_bar").assertExists()
    }

    @Test
    fun bufferingAndHudRemainVisibleIndependentlyOfControls() {
        composeRule.setContent {
            MaterialTheme {
                PlayerControlsOverlay(
                    coordinator = PlayerCoordinatorState(
                        controlsVisible = false,
                        hud = PlayerHudState.Brightness(0.65f),
                    ),
                    playback = PlaybackUiState(isBuffering = true),
                    fallbackTitle = "Buffering",
                    onBack = {}, onPlayPause = {}, onPrevious = {}, onNext = {},
                    onSeekPreview = { _, _ -> }, onSeekCommit = {},
                    onInteractionStart = {}, onInteractionEnd = {}, onOpenMenu = {},
                    onRotate = {}, onLock = {}, onUnlock = {}, onPip = {}, onFullscreen = {},
                )
            }
        }
        composeRule.onNodeWithTag("buffering_indicator").assertExists()
        composeRule.onNodeWithTag("player_hud").assertExists()
        composeRule.onNodeWithTag("seek_bar").assertDoesNotExist()
    }

    @Test
    fun lockedStateBlocksNormalOverlayAndKeepsExplicitUnlock() {
        var unlockClicked = false
        composeRule.setContent {
            MaterialTheme {
                PlayerControlsOverlay(
                    coordinator = PlayerCoordinatorState(
                        controlsVisible = false,
                        controlsLocked = true,
                        unlockVisible = true,
                    ),
                    playback = PlaybackUiState(),
                    fallbackTitle = "Locked",
                    onBack = {},
                    onPlayPause = {},
                    onPrevious = {},
                    onNext = {},
                    onSeekPreview = { _, _ -> },
                    onSeekCommit = {},
                    onInteractionStart = {},
                    onInteractionEnd = {},
                    onOpenMenu = {},
                    onRotate = {},
                    onLock = {},
                    onUnlock = { unlockClicked = true },
                    onPip = {},
                    onFullscreen = {},
                )
            }
        }

        composeRule.onNodeWithTag("unlock_button").assertExists().performClick()
        composeRule.onNodeWithTag("seek_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("play_pause_button").assertDoesNotExist()
        assertTrue(unlockClicked)
    }
}

package com.zubaer.maxvideoplayer.feature.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
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
        composeRule.onNodeWithTag("orientation_button").assertExists()
        composeRule.onNodeWithTag("lock_button").assertExists()
        composeRule.onNodeWithTag("pip_button").assertExists()
        assertTrue(playClicked)
    }

    @Test
    fun lockedStateBlocksNormalOverlayAndKeepsExplicitUnlock() {
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
                    onUnlock = {},
                    onPip = {},
                    onFullscreen = {},
                )
            }
        }

        composeRule.onNodeWithTag("unlock_button").assertExists()
        composeRule.onNodeWithTag("seek_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("play_pause_button").assertDoesNotExist()
    }
}

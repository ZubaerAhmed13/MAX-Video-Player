package com.zubaer.maxvideoplayer.feature.cast

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.core.model.PlaybackTarget
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.feature.audio.AudioEngineState
import com.zubaer.maxvideoplayer.feature.audio.ProfessionalAudioDialog
import com.zubaer.maxvideoplayer.feature.player.LocalPlayerChromeHostState
import com.zubaer.maxvideoplayer.feature.player.PlayerChromeHostState
import com.zubaer.maxvideoplayer.feature.player.PlayerControlsOverlay
import com.zubaer.maxvideoplayer.feature.player.PlayerCoordinatorState
import com.zubaer.maxvideoplayer.feature.tv.TvPlayerShortcutBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step8CastRemoteUiInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun remoteCast_disables_phone_video_processing_and_labels_it_truthfully() {
        compose.setContent {
            CompositionLocalProvider(
                LocalPlayerChromeHostState provides PlayerChromeHostState(onOutputDevice = {}),
            ) {
                MaterialTheme {
                    PlayerControlsOverlay(
                        coordinator = PlayerCoordinatorState(preparing = false, controlsVisible = true),
                        playback = PlaybackUiState(
                            connected = true,
                            playbackTarget = PlaybackTarget.CAST_DEVICE,
                            title = "Cast certification",
                            durationMs = 60_000L,
                        ),
                        fallbackTitle = "Cast certification",
                        localVideoProcessingAvailable = false,
                        onBack = {},
                        onPlayPause = {},
                        onPrevious = {},
                        onNext = {},
                        onGoLive = {},
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
                        onSubtitles = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("cast_video_processing_unavailable").assertTextContains("Cast", substring = true)
        compose.onNodeWithTag("output_device_button").assertTextContains("Cast", substring = true)
        compose.onNodeWithTag("decoder_button").assertIsNotEnabled()
        compose.onNodeWithTag("display_button").assertIsNotEnabled().assertTextContains("Fit", substring = true)
        compose.onNodeWithTag("rotation_button").assertIsNotEnabled()
    }

    @Test
    fun remoteCast_replaces_phone_audioDsp_controls_with_unavailable_notice() {
        compose.setContent {
            MaterialTheme {
                ProfessionalAudioDialog(
                    state = AudioEngineState(
                        equalizerEnabled = true,
                        preampDb = 6f,
                        boostDb = 6f,
                        audioDelayMs = 250L,
                    ),
                    localProcessingAvailable = false,
                    onDismiss = {},
                    onAuto = {},
                    onPreferredLanguage = {},
                    onTrack = {},
                    onExternal = {},
                    onLoadExternal = {},
                    onLoadExternalUrl = {},
                    onRelinkExternal = {},
                    onRemoveExternal = {},
                    onEqEnabled = {},
                    onPreset = {},
                    onBand = { _, _ -> },
                    onPreamp = {},
                    onBoost = {},
                    onDelayDelta = {},
                    onDelayReset = {},
                    onChannel = {},
                    onBalance = {},
                    onPitch = {},
                    onPitchReset = {},
                    onAudioOnly = {},
                    onBackgroundMode = {},
                    onDisableVideoBackground = {},
                    onRouteCompensationDelta = {},
                    onRouteCompensationReset = {},
                    onRefreshExternal = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("cast_audio_processing_unavailable")
            .assertTextContains("Available when playing on this device", substring = true)
    }

    @Test
    fun tvShortcut_decoder_is_disabled_duringCast_but_audio_and_queue_remain_reachable() {
        compose.setContent {
            MaterialTheme {
                TvPlayerShortcutBar(
                    localVideoProcessingAvailable = false,
                    onSubtitles = {},
                    onAudio = {},
                    onDecoder = {},
                    onQueue = {},
                    onSettings = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("tv_decoder_button").assertIsNotEnabled().assertTextContains("Cast", substring = true)
        compose.onNodeWithTag("tv_audio_button").assertTextContains("Audio", substring = true)
        compose.onNodeWithTag("tv_queue_button").assertTextContains("Queue", substring = true)
        compose.onNodeWithTag("tv_cast_processing_notice").assertTextContains("phone-only", substring = true)
    }
}

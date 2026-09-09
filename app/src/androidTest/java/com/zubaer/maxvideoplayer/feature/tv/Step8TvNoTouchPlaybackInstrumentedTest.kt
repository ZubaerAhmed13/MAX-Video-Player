package com.zubaer.maxvideoplayer.feature.tv

import android.content.res.Configuration
import android.net.Uri
import android.util.Base64
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.audio.ProfessionalAudioPlayerHost
import com.zubaer.maxvideoplayer.feature.audio.AudioPlaybackController
import com.zubaer.maxvideoplayer.feature.library.LibraryPlaybackRequest
import com.zubaer.maxvideoplayer.feature.library.LibraryUiState
import com.zubaer.maxvideoplayer.feature.player.OrientationMode
import com.zubaer.maxvideoplayer.feature.player.PlayerViewModel
import com.zubaer.maxvideoplayer.playback.PlaybackService
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.ui.MaxTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API/emulator certification for the production Android-TV path without touch input:
 * TV home -> TV library -> real service-owned playback -> media keys/D-pad -> player panels ->
 * Back hides controls -> Back exits to the TV library.
 *
 * This intentionally uses the real PlaybackConnection/PlaybackService and the production
 * ProfessionalAudioPlayerHost. It does not emulate a Cast receiver or physical TV hardware.
 */
@RunWith(AndroidJUnit4::class)
class Step8TvNoTouchPlaybackInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var connection: PlaybackConnection? = null
    private var fixture: File? = null

    @After
    fun tearDown() {
        connection?.pause()
        connection?.disconnect()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.stopService(android.content.Intent(context, PlaybackService::class.java))
        fixture?.delete()
    }

    @Test
    fun remoteOnly_home_library_play_seek_pause_panels_back_toLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MaxVideoPlayerApplication
        val container = app.container
        container.playerPreferences.setTutorialSeen(true)
        container.playerPreferences.setAutoHideMillis(8_000L)

        val file = writeTvFixture(instrumentation.targetContext.cacheDir)
        fixture = file
        val media = AppMedia(
            stableId = "step8-tv-no-touch-${System.nanoTime()}",
            uri = Uri.fromFile(file).toString(),
            title = "Step 8 TV remote certification",
            mimeType = "video/mp4",
            durationMs = 15_000L,
            sizeBytes = file.length(),
            width = 160,
            height = 90,
            fileName = file.name,
            sourceType = MediaSourceType.SAF,
        )

        val playbackConnection = PlaybackConnection(
            compose.activity,
            container.subtitleRepository,
            container.networkDiagnosticsMonitor,
        )
        connection = playbackConnection
        val stage = mutableStateOf(Stage.HOME)

        val tvConfiguration = Configuration(compose.activity.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or Configuration.UI_MODE_TYPE_TELEVISION
        }
        val tvContext = compose.activity.createConfigurationContext(tvConfiguration)

        compose.setContent {
            when (stage.value) {
                Stage.HOME -> androidx.tv.material3.MaterialTheme {
                    TvHomeScreen(
                        lastFocused = TvDestination.LIBRARY,
                        mountedUsbCount = 0,
                        onFocused = {},
                        onDestination = { destination ->
                            if (destination == TvDestination.LIBRARY) stage.value = Stage.LIBRARY
                        },
                    )
                }

                Stage.LIBRARY -> androidx.tv.material3.MaterialTheme {
                    TvLibraryScreen(
                        state = LibraryUiState(media = listOf(media)),
                        onBack = { stage.value = Stage.HOME },
                        onPlay = { stage.value = Stage.PLAYER },
                        playbackRequest = { LibraryPlaybackRequest(listOf(media), 0) },
                    )
                }

                Stage.PLAYER -> CompositionLocalProvider(LocalContext provides tvContext) {
                    MaxTheme {
                        val playerViewModel = remember(media.stableId) {
                            PlayerViewModel(
                                media = media,
                                historyRepository = container.historyRepository,
                                playbackConnection = playbackConnection,
                                preferences = container.playerPreferences,
                                decoderRepository = container.decoderRepository,
                                deviceCapabilityProvider = container.deviceCapabilityProvider,
                                queue = listOf(media),
                                startIndex = 0,
                            )
                        }
                        val audioController = remember {
                            AudioPlaybackController(container.audioRepository, playbackConnection)
                        }
                        ProfessionalAudioPlayerHost(
                            media = media,
                            viewModel = playerViewModel,
                            playbackConnection = playbackConnection,
                            subtitleRepository = container.subtitleRepository,
                            audioRepository = container.audioRepository,
                            audioController = audioController,
                            onBack = { stage.value = Stage.LIBRARY },
                            onEnterPip = {},
                            onFullscreenChanged = {},
                            onOrientationModeChanged = { _: OrientationMode -> },
                            onPlayerHostStateChanged = { _, _ -> },
                            onAudioBackgroundPolicyChanged = { _, _ -> },
                        )
                    }
                }
            }
        }

        // TV Home -> Library using only remote Enter.
        compose.waitForIdle()
        compose.onNodeWithTag("tv_destination_library").assertIsFocused().performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.waitUntil(5_000L) { stage.value == Stage.LIBRARY }

        // The first playable library item is the deterministic TV focus target.
        compose.onNodeWithTag("tv_media_${media.stableId}").assertIsFocused().performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.waitUntil(5_000L) { stage.value == Stage.PLAYER }

        // Real PlaybackService/MediaSession/MediaController path must become ready.
        compose.waitUntil(15_000L) {
            val state = playbackConnection.state.value
            state.connected && state.mediaId == media.stableId && state.durationMs >= 10_000L && state.error == null
        }

        // Pause quickly so the 15-second fixture stays deterministic while we certify seeking.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
        compose.waitUntil(5_000L) { !playbackConnection.state.value.isPlaying }
        val beforeForward = playbackConnection.state.value.currentPositionMs

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        compose.waitUntil(5_000L) {
            playbackConnection.state.value.currentPositionMs >= (beforeForward + 8_000L).coerceAtMost(14_000L)
        }
        val afterForward = playbackConnection.state.value.currentPositionMs
        assertTrue("TV fast-forward must move the authoritative MediaSession position", afterForward > beforeForward)

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_REWIND)
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs < afterForward }

        // Play must be real, not just a UI toggle: verify the service position advances, then pause.
        val beforePlay = playbackConnection.state.value.currentPositionMs
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY)
        compose.waitUntil(5_000L) { playbackConnection.state.value.isPlaying }
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs > beforePlay + 100L }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
        compose.waitUntil(5_000L) { !playbackConnection.state.value.isPlaying }

        // Production TV shortcut row: Subtitles -> Back -> Audio -> Back -> Queue -> Back.
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_subtitle_button").fetchSemanticsNode() }.isSuccess
        }
        compose.onNodeWithTag("tv_subtitle_button").assertIsFocused().performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("subtitle_dialog").fetchSemanticsNode() }.isSuccess
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("subtitle_dialog").fetchSemanticsNode() }.isFailure
        }

        compose.onNodeWithTag("tv_subtitle_button").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        compose.onNodeWithTag("tv_audio_button").assertIsFocused().performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("professional_audio_panel").fetchSemanticsNode() }.isSuccess
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("professional_audio_panel").fetchSemanticsNode() }.isFailure
        }

        // Audio -> Decoder -> Queue. Decoder is reachable locally; Queue remains the selected action.
        compose.onNodeWithTag("tv_audio_button").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        compose.onNodeWithTag("tv_queue_button").assertIsFocused().performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("player_queue_dialog").fetchSemanticsNode() }.isSuccess
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("player_queue_dialog").fetchSemanticsNode() }.isFailure
        }

        // Back hierarchy on TV: first hides controls, second exits player back to TV Library.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_player_shortcuts").fetchSemanticsNode() }.isFailure
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) { stage.value == Stage.LIBRARY }
        compose.onNodeWithTag("tv_media_${media.stableId}").assertIsFocused()
    }

    private enum class Stage { HOME, LIBRARY, PLAYER }

    private fun writeTvFixture(directory: File): File = File(directory, "step8-tv-no-touch.mp4").apply {
        writeBytes(Base64.decode(TV_MP4_BASE64, Base64.NO_WRAP))
    }

    companion object {
        // 15 s, 160x90, 2 fps, H.264 baseline, no audio. Small but long enough for ±10 s seeks.
        private const val TV_MP4_BASE64 = "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"
    }
}

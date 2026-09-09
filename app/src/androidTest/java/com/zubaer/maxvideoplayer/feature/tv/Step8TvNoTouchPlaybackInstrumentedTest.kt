package com.zubaer.maxvideoplayer.feature.tv

import android.content.res.Configuration
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.audio.AudioPlaybackController
import com.zubaer.maxvideoplayer.feature.audio.ProfessionalAudioPlayerHost
import com.zubaer.maxvideoplayer.feature.library.LibraryPlaybackRequest
import com.zubaer.maxvideoplayer.feature.library.LibraryUiState
import com.zubaer.maxvideoplayer.feature.player.OrientationMode
import com.zubaer.maxvideoplayer.feature.player.PlayerViewModel
import com.zubaer.maxvideoplayer.playback.AndroidTestMediaFixture
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
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
 * Back hides controls -> D-pad restores controls -> Back exits to the TV library.
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            connection?.pause()
            connection?.disconnect()
        }
        val context = instrumentation.targetContext
        context.stopService(android.content.Intent(context, PlaybackService::class.java))
        fixture?.delete()
    }

    @Test
    fun remoteOnly_home_library_play_seek_pause_panels_back_toLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val app = targetContext.applicationContext as MaxVideoPlayerApplication
        val container = app.container
        container.playerPreferences.setTutorialSeen(true)
        container.playerPreferences.setAutoHideMillis(8_000L)

        // Reuse the same deterministic H.264 fixture already exercised by the local playback
        // certification. The 10-second TV seek delta itself is unit-certified by
        // TvPlayerInputController; this end-to-end test verifies that the real media-key path
        // reaches the service-owned player and clamps correctly at media boundaries.
        val file = AndroidTestMediaFixture.writeShortH264Mp4(
            targetContext,
            "step8_tv_no_touch_${System.nanoTime()}.mp4",
        )
        fixture = file
        val media = AppMedia(
            stableId = "step8-tv-no-touch-${System.nanoTime()}",
            uri = Uri.fromFile(file).toString(),
            title = "Step 8 TV remote certification",
            mimeType = "video/mp4",
            durationMs = 2_000L,
            sizeBytes = file.length(),
            width = 160,
            height = 90,
            fileName = file.name,
            sourceType = MediaSourceType.SAF,
        )

        // MainActivity connects this same container-owned connection before rendering MaxApp.
        // The isolated TV host mirrors that production lifecycle instead of creating a second
        // MediaController that can race the service-owned session.
        val playbackConnection = container.playbackConnection
        connection = playbackConnection
        instrumentation.runOnMainSync { playbackConnection.connect() }
        val stage = mutableStateOf(Stage.HOME)

        val tvConfiguration = Configuration(targetContext.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or Configuration.UI_MODE_TYPE_TELEVISION
        }
        val tvContext = targetContext.createConfigurationContext(tvConfiguration)

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

                Stage.PLAYER -> CompositionLocalProvider(
                    LocalContext provides tvContext,
                    LocalConfiguration provides tvConfiguration,
                    LocalActivityResultRegistryOwner provides compose.activity,
                ) {
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

        compose.waitForIdle()
        compose.onNodeWithTag("tv_destination_library").assertIsFocused().performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.waitUntil(5_000L) { stage.value == Stage.LIBRARY }

        compose.onNodeWithTag("tv_media_${media.stableId}").assertIsFocused().performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        compose.waitUntil(5_000L) { stage.value == Stage.PLAYER }

        compose.waitUntil(15_000L) {
            val state = playbackConnection.state.value
            state.connected && state.mediaId == media.stableId && state.durationMs >= 1_500L && state.error == null
        }

        // Explicit MEDIA_PAUSE / MEDIA_PLAY proof while the requested item is safely near its
        // beginning. Position advancement is the authoritative proof that MEDIA_PLAY reached the
        // service-owned player; it is more robust than sampling a short-lived isPlaying=true on a
        // two-second fixture under a loaded emulator.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
        compose.waitUntil(5_000L) { !playbackConnection.state.value.isPlaying }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_REWIND)
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs <= 500L }
        val beforeMediaPlay = playbackConnection.state.value.currentPositionMs
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY)
        compose.waitUntil(5_000L) {
            playbackConnection.state.value.currentPositionMs > beforeMediaPlay + 100L
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
        compose.waitUntil(5_000L) { !playbackConnection.state.value.isPlaying }

        // Rewind and fast-forward are sent as real media keys. On this deliberately short fixture
        // the 10-second policy clamps to the media boundaries; the exact 10-second arithmetic is
        // independently unit-certified by TvPlayerInputController.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_REWIND)
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs <= 500L }
        val beforeForward = playbackConnection.state.value.currentPositionMs
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs >= 1_500L }
        val afterForward = playbackConnection.state.value.currentPositionMs
        assertTrue("TV fast-forward must move the authoritative MediaSession position", afterForward > beforeForward)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_REWIND)
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs < afterForward }

        // Exercise PLAY_PAUSE after the boundary seeks. PlayerScreen's toggle path is explicitly
        // ended-safe (seek-to-zero + play) and must advance the same authoritative position.
        val beforeToggle = playbackConnection.state.value.currentPositionMs
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        compose.waitUntil(5_000L) {
            playbackConnection.state.value.currentPositionMs > beforeToggle + 100L
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
        compose.waitUntil(5_000L) { !playbackConnection.state.value.isPlaying }

        // Certify the required TV policy rather than assuming stale focus: Back hides the visible
        // controls, the root regains focus, and D-pad Up restores the shortcut strip with the first
        // action (Subtitles) focused for deterministic remote navigation.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_player_shortcuts").fetchSemanticsNode() }.isFailure
        }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("player_root").assertIsFocused() }.isSuccess
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_subtitle_button").assertIsFocused() }.isSuccess
        }

        compose.onNodeWithTag("tv_subtitle_button").performKeyInput {
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
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_subtitle_button").assertIsFocused() }.isSuccess
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
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_audio_button").assertIsFocused() }.isSuccess
        }

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

        // Back hierarchy: with no panel open, first Back hides controls and second Back exits the
        // player. The TV library then restores deterministic focus to the current video.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_player_shortcuts").fetchSemanticsNode() }.isFailure
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) { stage.value == Stage.LIBRARY }
        compose.onNodeWithTag("tv_media_${media.stableId}").assertIsFocused()
    }

    private enum class Stage { HOME, LIBRARY, PLAYER }
}

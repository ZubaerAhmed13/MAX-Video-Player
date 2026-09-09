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
import com.zubaer.maxvideoplayer.core.model.RepeatMode
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
            connection?.setRepeatMode(RepeatMode.OFF)
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

        fun pressFocused(tag: String, key: Key) {
            compose.onNodeWithTag(tag).assertIsFocused().performKeyInput {
                keyDown(key)
                keyUp(key)
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

        // The certified fixture is deliberately only two seconds long. Keep it repeating while
        // transport keys are exercised so emulator scheduling cannot turn a valid PLAY assertion
        // into an ENDED race between polls. Normalise the service-owned player as test setup; all
        // behavior asserted below is still driven exclusively by remote/media key events.
        instrumentation.runOnMainSync {
            playbackConnection.setRepeatMode(RepeatMode.ONE)
            playbackConnection.pause()
            playbackConnection.seekTo(0L)
        }
        compose.waitUntil(5_000L) {
            val state = playbackConnection.state.value
            !state.isPlaying && state.currentPositionMs <= 500L && state.error == null
        }

        // Hide controls first so the player root owns focus exactly as it does during normal
        // full-screen TV viewing. Compose key injection then routes the hardware key through the
        // focused hierarchy and PlayerScreen.onPreviewKeyEvent instead of relying on Android's
        // system MediaSession dispatch to rediscover this isolated test Activity.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_player_shortcuts").fetchSemanticsNode() }.isFailure
        }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("player_root").assertIsFocused() }.isSuccess
        }

        // MEDIA_PLAY is injected at the focused root. The production handler shows controls, so
        // Subtitles becomes the focused child; subsequent global transport keys are injected there
        // and intercepted by the same root preview handler on their downward pass.
        pressFocused("player_root", Key.MediaPlay)
        compose.waitUntil(5_000L) { playbackConnection.state.value.isPlaying }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_subtitle_button").assertIsFocused() }.isSuccess
        }
        pressFocused("tv_subtitle_button", Key.MediaPause)
        compose.waitUntil(5_000L) { !playbackConnection.state.value.isPlaying }

        // Rewind/fast-forward remain real media-key events while paused. On this short fixture the
        // 10-second policy clamps to boundaries; exact 10-second arithmetic is separately covered
        // by TvPlayerInputController unit tests.
        pressFocused("tv_subtitle_button", Key.MediaFastForward)
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs >= 1_500L }
        val afterForward = playbackConnection.state.value.currentPositionMs
        pressFocused("tv_subtitle_button", Key.MediaRewind)
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs <= 500L }
        val afterRewind = playbackConnection.state.value.currentPositionMs
        assertTrue("TV rewind must move the authoritative MediaSession position", afterRewind < afterForward)

        pressFocused("tv_subtitle_button", Key.MediaPlayPause)
        compose.waitUntil(5_000L) { playbackConnection.state.value.isPlaying }
        pressFocused("tv_subtitle_button", Key.MediaPause)
        compose.waitUntil(5_000L) { !playbackConnection.state.value.isPlaying }

        // Certify the required TV policy explicitly: Back hides visible controls, the root regains
        // focus, and D-pad Up restores the shortcut strip with Subtitles focused.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_player_shortcuts").fetchSemanticsNode() }.isFailure
        }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("player_root").assertIsFocused() }.isSuccess
        }
        pressFocused("player_root", Key.DirectionUp)
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

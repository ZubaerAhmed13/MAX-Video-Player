package com.zubaer.maxvideoplayer.feature.tv

import android.content.res.Configuration
import android.net.Uri
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Dialog-window Back routing itself belongs to AndroidX's separate ComponentDialog window and is
 * hardware/window-manager behavior. This synthetic TV host intentionally has no Android window
 * focus, so overlay state dismissal is certified through each production dialog's Done action,
 * which invokes the same app onDismiss callback as onDismissRequest. Real player Back hierarchy is
 * still certified below with focused Key.Escape events. Physical TV dialog-Back routing remains a
 * final hardware-certification item rather than being falsely simulated here.
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

        fun dismissOpenOverlay() {
            // Material3 AlertDialog routes both platform onDismissRequest and the explicit Done
            // action to the same app-owned onDismiss callback in these three production overlays.
            // Invoke that production action directly because this synthetic ComponentActivity has
            // no window focus for AndroidX's separate ComponentDialog window. performClick is a
            // semantics action, not a touch injection; all navigation/transport/player Back paths
            // in this workflow remain real D-pad/media-key events.
            compose.onNodeWithText("Done").performClick()
            compose.waitForIdle()
        }

        fun dispatchInitialPlayerBack() {
            // The synthetic TV Configuration intentionally does not guarantee Android window focus
            // for this ComponentActivity. Use the Activity Back dispatcher only to establish the
            // initial hidden-controls baseline without depending on emulator window focus. The
            // production TV KEYCODE_BACK mapping is re-certified below with focused Key.Escape
            // events for controls hiding and final return to the TV library.
            instrumentation.runOnMainSync {
                compose.activity.onBackPressedDispatcher.onBackPressed()
            }
            instrumentation.waitForIdleSync()
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

        // Keep the deliberately short fixture repeating so emulator scheduling cannot turn a
        // transport-key assertion into an unrelated ENDED race.
        instrumentation.runOnMainSync {
            playbackConnection.setRepeatMode(RepeatMode.ONE)
            playbackConnection.pause()
            playbackConnection.seekTo(0L)
        }
        compose.waitUntil(5_000L) {
            val state = playbackConnection.state.value
            !state.isPlaying && state.currentPositionMs <= 500L && state.error == null
        }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_player_shortcuts").fetchSemanticsNode() }.isSuccess
        }

        // Establish the hidden-controls baseline without requiring window-dependent initial child
        // focus. The actual TV Back-key contract is re-certified below using Key.Escape events.
        dispatchInitialPlayerBack()
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

        // Prove MEDIA_PLAY / MEDIA_PAUSE against the authoritative service-owned position.
        val beforePlay = playbackConnection.state.value.currentPositionMs
        pressFocused("tv_subtitle_button", Key.MediaPlay)
        compose.waitUntil(5_000L) {
            playbackConnection.state.value.currentPositionMs > beforePlay + 100L
        }
        pressFocused("tv_subtitle_button", Key.MediaPause)
        compose.waitUntil(5_000L) { !playbackConnection.state.value.isPlaying }

        // Rewind/fast-forward remain real media-key events. On this short fixture the 10-second
        // policy clamps to boundaries; exact seek arithmetic is unit-certified separately.
        pressFocused("tv_subtitle_button", Key.MediaFastForward)
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs >= 1_500L }
        val afterForward = playbackConnection.state.value.currentPositionMs
        pressFocused("tv_subtitle_button", Key.MediaRewind)
        compose.waitUntil(5_000L) { playbackConnection.state.value.currentPositionMs <= 500L }
        val afterRewind = playbackConnection.state.value.currentPositionMs
        assertTrue("TV rewind must move the authoritative MediaSession position", afterRewind < afterForward)

        val beforeToggle = playbackConnection.state.value.currentPositionMs
        pressFocused("tv_subtitle_button", Key.MediaPlayPause)
        compose.waitUntil(5_000L) {
            playbackConnection.state.value.currentPositionMs > beforeToggle + 100L
        }
        pressFocused("tv_subtitle_button", Key.MediaPause)
        compose.waitUntil(5_000L) { !playbackConnection.state.value.isPlaying }

        // Re-certify the real TV Back -> hidden controls -> D-pad Up -> focused Subtitles policy
        // after transport interaction, independently of the initial baseline setup above.
        pressFocused("tv_subtitle_button", Key.Escape)
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
        dismissOpenOverlay()
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
        dismissOpenOverlay()
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
        dismissOpenOverlay()
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("player_queue_dialog").fetchSemanticsNode() }.isFailure
        }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_queue_button").assertIsFocused() }.isSuccess
        }

        // Back hierarchy: first Back hides controls; second Back exits to the TV library, which
        // restores deterministic focus to the selected video.
        pressFocused("tv_queue_button", Key.Escape)
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("tv_player_shortcuts").fetchSemanticsNode() }.isFailure
        }
        compose.waitUntil(5_000L) {
            runCatching { compose.onNodeWithTag("player_root").assertIsFocused() }.isSuccess
        }
        pressFocused("player_root", Key.Escape)
        compose.waitUntil(5_000L) { stage.value == Stage.LIBRARY }
        compose.onNodeWithTag("tv_media_${media.stableId}").assertIsFocused()
    }

    private enum class Stage { HOME, LIBRARY, PLAYER }
}

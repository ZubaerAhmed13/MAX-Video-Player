package com.zubaer.maxvideoplayer.feature.player

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.core.database.MaxDatabase
import com.zubaer.maxvideoplayer.core.database.PlaybackHistoryRepository
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
import com.zubaer.maxvideoplayer.playback.AndroidTestMediaFixture
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PlayerScreenGestureInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realPlayerScreenDispatchesSeekDoubleTapPinchAndRenderedAwarePan() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = AndroidTestMediaFixture.writeShortH264Mp4(context, "player_screen_gesture_fixture.mp4")
        val database = Room.inMemoryDatabaseBuilder(context, MaxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val historyRepository = PlaybackHistoryRepository(database.mediaHistoryDao())
        val preferences = PlayerPreferences(context).apply {
            setTutorialSeen(true)
            setHorizontalSeekEnabled(true)
            setPinchZoomEnabled(true)
            setGestureSensitivity(GestureSensitivity.MEDIUM)
            setDoubleTapSeekSeconds(5)
        }
        val subtitleRepository = SubtitleRepository(context)
        val playbackConnection = PlaybackConnection(context, subtitleRepository)
        val media = AppMedia(
            stableId = "real-player-screen-gesture-fixture",
            uri = Uri.fromFile(fixture).toString(),
            title = "Real PlayerScreen gesture fixture",
            mimeType = "video/mp4",
            durationMs = 2_000L,
            sizeBytes = fixture.length(),
            width = 160,
            height = 90,
            rotationDegrees = 0,
            videoCodec = "h264",
            sourceType = MediaSourceType.SAF,
        )
        val viewModel = PlayerViewModel(
            media = media,
            historyRepository = historyRepository,
            playbackConnection = playbackConnection,
            preferences = preferences,
        )

        try {
            composeRule.setContent {
                MaterialTheme {
                    PlayerScreen(
                        media = media,
                        viewModel = viewModel,
                        playbackConnection = playbackConnection,
                        subtitleRepository = subtitleRepository,
                        onBack = {},
                        onEnterPip = {},
                        onFullscreenChanged = {},
                        onOrientationModeChanged = {},
                        onPlayerHostStateChanged = { _, _ -> },
                    )
                }
            }

            composeRule.waitUntil(timeoutMillis = 20_000L) {
                !viewModel.state.value.preparing &&
                    playbackConnection.state.value.connected &&
                    playbackConnection.state.value.durationMs >= 1_500L
            }
            assertNull("Fixture playback failed before gesture verification", playbackConnection.state.value.error)

            composeRule.runOnIdle {
                playbackConnection.pause()
                playbackConnection.seekTo(500L)
                if (viewModel.state.value.controlsVisible) viewModel.onSurfaceTap()
            }
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                playbackConnection.state.value.currentPositionMs in 250L..900L &&
                    !viewModel.state.value.controlsVisible
            }

            val surface = composeRule.onNodeWithTag("video_surface").assertExists()

            surface.performTouchInput {
                swipe(
                    start = Offset(width * 0.25f, height * 0.35f),
                    end = Offset(width * 0.78f, height * 0.35f),
                    durationMillis = 450L,
                )
            }
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                playbackConnection.state.value.currentPositionMs >= 1_500L
            }
            assertNull("Horizontal seek gesture caused playback failure", playbackConnection.state.value.error)

            composeRule.runOnIdle { playbackConnection.seekTo(500L) }
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                playbackConnection.state.value.currentPositionMs in 250L..900L
            }
            surface.performTouchInput {
                doubleClick(Offset(width * 0.84f, height * 0.35f))
            }
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                playbackConnection.state.value.currentPositionMs >= 1_500L
            }

            composeRule.runOnIdle { viewModel.resetZoom() }
            surface.performTouchInput {
                val y = height * 0.55f
                down(0, Offset(width * 0.40f, y))
                down(1, Offset(width * 0.60f, y))
                for (step in 1..6) {
                    val t = step / 6f
                    updatePointerTo(0, Offset(width * (0.40f - 0.05f * t), y))
                    updatePointerTo(1, Offset(width * (0.60f + 0.15f * t), y))
                    move(24L)
                }
                up(0)
                up(1)
            }
            composeRule.waitUntil(timeoutMillis = 5_000L) { viewModel.state.value.zoom > 1.25f }
            val afterPinch = viewModel.state.value
            assertTrue("Pinch did not create horizontal pan through PlayerScreen", afterPinch.panX > 0f)

            surface.performTouchInput {
                val startY = height * 0.42f
                val endY = height * 0.78f
                down(0, Offset(width * 0.35f, startY))
                down(1, Offset(width * 0.75f, startY))
                for (step in 1..6) {
                    val t = step / 6f
                    val y = startY + (endY - startY) * t
                    updatePointerTo(0, Offset(width * 0.35f, y))
                    updatePointerTo(1, Offset(width * 0.75f, y))
                    move(24L)
                }
                up(0)
                up(1)
            }
            composeRule.waitForIdle()

            val nodeBounds = surface.fetchSemanticsNode().boundsInRoot
            val finalState = viewModel.state.value
            val baseFrame = RenderedVideoGeometry.baseRenderedFrame(
                viewportWidthPx = nodeBounds.width,
                viewportHeightPx = nodeBounds.height,
                sourceWidth = media.width,
                sourceHeight = media.height,
                sourceRotationDegrees = media.rotationDegrees,
                resizeMode = finalState.resizeMode,
            )
            val transform = PlayerInteractionPolicy.transform(
                resizeMode = finalState.resizeMode,
                customAspectRatio = finalState.customAspectRatio,
                sourceWidth = media.width,
                sourceHeight = media.height,
                sourceRotationDegrees = media.rotationDegrees,
                manualZoom = finalState.zoom,
                panX = 0f,
                panY = 0f,
                displayRotationDegrees = finalState.displayRotationDegrees,
                viewportWidthPx = nodeBounds.width,
                viewportHeightPx = nodeBounds.height,
            )
            val renderedBounds = RenderedVideoGeometry.panBounds(
                viewportWidthPx = nodeBounds.width,
                viewportHeightPx = nodeBounds.height,
                renderedWidthPx = baseFrame.widthPx,
                renderedHeightPx = baseFrame.heightPx,
                scaleX = transform.scaleX,
                scaleY = transform.scaleY,
                rotationDegrees = transform.rotationDegrees,
            )
            val legacyViewportBounds = PlayerInteractionPolicy.panBounds(
                nodeBounds.width,
                nodeBounds.height,
                transform.scaleX,
                transform.scaleY,
            )

            assertTrue("Test geometry did not distinguish rendered video from legacy viewport math", renderedBounds.maxY < legacyViewportBounds.maxY)
            assertTrue(
                "PlayerScreen allowed vertical pan into letterbox space: panY=${finalState.panY}, renderedMaxY=${renderedBounds.maxY}",
                abs(finalState.panY) <= renderedBounds.maxY + 1.5f,
            )
            assertTrue(abs(finalState.panX) <= renderedBounds.maxX + 1.5f)
            assertNull("Gesture sequence caused playback failure", playbackConnection.state.value.error)
        } finally {
            composeRule.runOnIdle {
                playbackConnection.pause()
                playbackConnection.disconnect()
            }
            context.stopService(Intent(context, PlaybackService::class.java))
            database.close()
            fixture.delete()
        }
    }
}

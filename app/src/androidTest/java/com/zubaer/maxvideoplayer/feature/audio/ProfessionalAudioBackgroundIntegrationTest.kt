package com.zubaer.maxvideoplayer.feature.audio

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Production lifecycle certification for Step-5 background-audio policy. */
@RunWith(AndroidJUnit4::class)
class ProfessionalAudioBackgroundIntegrationTest {

    @Test
    fun backgroundPoliciesPreserveSessionSuppressAndRestoreVideoAndPauseWhenRequested() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val container = (context.applicationContext as MaxVideoPlayerApplication).container
        val connection = container.playbackConnection
        val controller = container.audioPlaybackController
        val fixture = copyAsset(context, testContext, "step5_multi_audio.mp4")
        val mediaId = "step5-background-${System.currentTimeMillis()}"
        val media = AppMedia(
            stableId = mediaId,
            uri = Uri.fromFile(fixture).toString(),
            title = "Step 5 background fixture",
            mimeType = "video/mp4",
            durationMs = 2_000L,
            sizeBytes = fixture.length(),
            width = 160,
            height = 90,
            sourceType = MediaSourceType.SAF,
        )

        controller.setBackgroundMode(BackgroundPlaybackMode.CONTINUE_AUDIO)
        controller.setDisableVideoInBackground(false)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })
            instrumentation.runOnMainSync {
                connection.load(media, 0L, false)
                controller.bind()
            }
            assertTrue("Background fixture did not load", await(15_000L) {
                connection.state.value.durationMs > 0L && connection.state.value.error == null
            })
            assertTrue("Video track was not selected before lifecycle certification", await(5_000L) {
                onMain(instrumentation) { isVideoTrackSelected(connection.playerOrNull()) }
            })

            scenario.onActivity { activity ->
                activity.setPlayerHostState(media, autoPip = false)
                activity.setAudioBackgroundPolicy(BackgroundPlaybackMode.CONTINUE_AUDIO, disableVideo = true)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue("Continue-audio background did not suppress the selected video track", await(5_000L) {
                onMain(instrumentation) {
                    val player = connection.playerOrNull()
                    player?.currentMediaItem?.mediaId == mediaId && !isVideoTrackSelected(player)
                }
            })
            assertEquals(
                "Background transition replaced the active Media3 session item",
                mediaId,
                onMain(instrumentation) { connection.playerOrNull()?.currentMediaItem?.mediaId },
            )

            // ActivityScenario forces STARTED by briefly resuming MainActivity and then covering it
            // with a floating test Activity. That is not a real foreground state and can legitimately
            // detach/release the video renderer again. Foreground restoration is therefore certified
            // at RESUMED, where MainActivity is actually visible and interactive to the user.
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue("Returning foreground did not restore a selected video track", await(5_000L) {
                onMain(instrumentation) { isVideoTrackSelected(connection.playerOrNull()) }
            })

            scenario.onActivity { activity ->
                activity.setAudioBackgroundPolicy(BackgroundPlaybackMode.PIP_WHEN_POSSIBLE, disableVideo = true)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue("PiP fallback did not preserve audio session and suppress video", await(5_000L) {
                onMain(instrumentation) {
                    val player = connection.playerOrNull()
                    player?.currentMediaItem?.mediaId == mediaId && !isVideoTrackSelected(player)
                }
            })
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue("PiP fallback foreground return did not restore video", await(5_000L) {
                onMain(instrumentation) { isVideoTrackSelected(connection.playerOrNull()) }
            })

            scenario.onActivity { activity ->
                activity.setAudioBackgroundPolicy(BackgroundPlaybackMode.PAUSE, disableVideo = false)
            }
            instrumentation.runOnMainSync { connection.play() }
            assertTrue("Playback did not enter playWhenReady before pause-policy test", await(3_000L) {
                onMain(instrumentation) { connection.playerOrNull()?.playWhenReady == true }
            })
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue("Pause background policy did not pause the service-owned player", await(5_000L) {
                onMain(instrumentation) { connection.playerOrNull()?.playWhenReady == false }
            })
            assertEquals(
                "Pause policy destroyed or replaced the session item",
                mediaId,
                onMain(instrumentation) { connection.playerOrNull()?.currentMediaItem?.mediaId },
            )
        } finally {
            instrumentation.runOnMainSync {
                controller.setBackgroundVideoDisabled(false)
                controller.setBackgroundMode(BackgroundPlaybackMode.CONTINUE_AUDIO)
                controller.setDisableVideoInBackground(false)
                connection.pause()
                controller.unbind()
                connection.disconnect()
            }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            fixture.delete()
        }
    }

    /**
     * Certifies observable playback behavior rather than the controller's copied parameter bundle.
     * MediaSession may transiently cache TrackSelectionParameters while selected Tracks already
     * reflect the service-owned ExoPlayer state. A selected video track is the behavior users see.
     */
    private fun isVideoTrackSelected(player: Player?): Boolean =
        player?.currentTracks?.groups?.any { group ->
            group.type == C.TRACK_TYPE_VIDEO &&
                (0 until group.length).any { index -> group.isTrackSelected(index) }
        } == true

    private fun copyAsset(
        targetContext: android.content.Context,
        testContext: android.content.Context,
        name: String,
    ): File = File(targetContext.cacheDir, "background-cert-$name").apply {
        testContext.assets.open(name).use { input ->
            outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun <T> onMain(instrumentation: android.app.Instrumentation, block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50L)
        }
        return condition()
    }
}

package com.zubaer.maxvideoplayer.playback

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end Step-1 playback certification on the Android emulator.
 *
 * The fixture is a deterministic two-second 160x90 H.264 Constrained Baseline + AAC MP4.
 * It is embedded as Base64 text so CI does not depend on network media or a binary GitHub fixture.
 * The test goes through PlaybackConnection -> MediaController -> MediaSessionService -> ExoPlayer,
 * which verifies the service-owned player path rather than only testing a fake or isolated UI state.
 */
@RunWith(AndroidJUnit4::class)
class LocalPlaybackIntegrationTest {

    @Test
    fun serviceOwnedPlayerPlaysPausesAndSeeksLocalMp4() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "step1_local_playback_fixture.mp4")
        fixture.writeBytes(Base64.decode(SAMPLE_MP4_BASE64, Base64.DEFAULT))

        val connection = PlaybackConnection(context)
        val media = AppMedia(
            stableId = "step1-local-playback-fixture",
            uri = Uri.fromFile(fixture).toString(),
            title = "Step 1 local playback fixture",
            mimeType = "video/mp4",
            durationMs = 2_000L,
            sizeBytes = fixture.length(),
            width = 160,
            height = 90,
            videoCodec = "h264",
            audioCodec = "aac",
            sourceType = MediaSourceType.SAF,
        )

        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect to PlaybackService", await(10_000L) {
                connection.state.value.connected
            })

            instrumentation.runOnMainSync {
                connection.load(media, startPositionMs = 0L, playWhenReady = false)
            }
            assertTrue("Local MP4 did not reach ready/error state", await(15_000L) {
                connection.state.value.durationMs >= 1_500L || connection.state.value.error != null
            })
            assertNull("Local MP4 failed to load: ${connection.state.value.error}", connection.state.value.error)
            assertTrue("Local MP4 did not expose a real duration", connection.state.value.durationMs >= 1_500L)

            instrumentation.runOnMainSync { connection.play() }
            assertTrue("Local playback never entered playing state", await(5_000L) {
                connection.state.value.isPlaying && connection.state.value.error == null
            })
            assertTrue("Playback position did not advance", await(5_000L) {
                connection.state.value.currentPositionMs >= 150L
            })

            instrumentation.runOnMainSync { connection.pause() }
            assertTrue("Pause command did not stop playback", await(5_000L) {
                !connection.state.value.isPlaying
            })

            instrumentation.runOnMainSync { connection.seekTo(1_000L) }
            assertTrue("Seek command did not move to the requested region", await(5_000L) {
                connection.state.value.currentPositionMs in 700L..1_300L && connection.state.value.error == null
            })
        } finally {
            instrumentation.runOnMainSync {
                connection.pause()
                connection.disconnect()
            }
            context.stopService(Intent(context, PlaybackService::class.java))
            fixture.delete()
        }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50L)
        }
        return condition()
    }

    private companion object {
        const val SAMPLE_MP4_BASE64 = "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"
    }
}

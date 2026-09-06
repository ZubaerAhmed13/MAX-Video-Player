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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end Step-1 playback certification on the Android emulator.
 *
 * The fixture is a deterministic two-second 160x90 H.264 Baseline + AAC MP4 generated for tests.
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
            assertTrue("Local MP4 did not become ready with a real duration", await(15_000L) {
                connection.state.value.durationMs >= 1_500L && connection.state.value.error == null
            })

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
        val SAMPLE_MP4_BASE64 = """
            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
        """.trimIndent().replace("\n", "")
    }
}

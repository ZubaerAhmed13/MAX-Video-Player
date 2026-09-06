package com.zubaer.maxvideoplayer.playback

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * End-to-end Step-1 playback certification on the Android emulator.
 *
 * The fixture is a deterministic two-second 160x90 H.264 Constrained Baseline MP4. Audio is
 * intentionally omitted so the binary remains tiny and deterministic. It is embedded as Base64
 * so CI does not depend on network media or a binary GitHub fixture. A SHA-256 assertion verifies
 * the exact decoded bytes before Media3 sees them.
 *
 * Playback is initiated while MainActivity is foreground because Android 15+ only grants media
 * audio focus to an eligible foreground app/foreground service. The actual player remains owned
 * by PlaybackService; the Activity never owns an ExoPlayer instance.
 *
 * The tested path is PlaybackConnection -> MediaController -> MediaSessionService -> ExoPlayer.
 */
@RunWith(AndroidJUnit4::class)
class LocalPlaybackIntegrationTest {

    @Test
    fun serviceOwnedPlayerPlaysPausesAndSeeksLocalMp4() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtureBytes = Base64.decode(SAMPLE_MP4_BASE64, Base64.NO_WRAP)
        assertEquals("Playback fixture byte length changed", FIXTURE_SIZE_BYTES, fixtureBytes.size)
        assertEquals("Playback fixture SHA-256 changed", FIXTURE_SHA256, fixtureBytes.sha256())

        val fixture = File(context.cacheDir, "step1_local_playback_fixture.mp4")
        fixture.writeBytes(fixtureBytes)
        assertEquals(FIXTURE_SIZE_BYTES.toLong(), fixture.length())

        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()

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
            assertTrue("Local MP4 did not enter playing/error state", await(5_000L) {
                connection.state.value.isPlaying || connection.state.value.error != null
            })
            assertNull("Local MP4 failed during playback: ${connection.state.value.error}", connection.state.value.error)
            assertTrue("Local playback never entered playing state", connection.state.value.isPlaying)
            assertTrue("Playback position did not advance", await(5_000L) {
                connection.state.value.currentPositionMs >= 150L || connection.state.value.error != null
            })
            assertNull("Local MP4 failed while advancing: ${connection.state.value.error}", connection.state.value.error)
            assertTrue("Playback position did not advance", connection.state.value.currentPositionMs >= 150L)

            instrumentation.runOnMainSync { connection.pause() }
            assertTrue("Pause command did not stop playback", await(5_000L) {
                !connection.state.value.isPlaying
            })

            instrumentation.runOnMainSync { connection.seekTo(1_000L) }
            assertTrue("Seek command did not move to the requested region", await(5_000L) {
                connection.state.value.currentPositionMs in 700L..1_300L || connection.state.value.error != null
            })
            assertNull("Local MP4 failed during seek: ${connection.state.value.error}", connection.state.value.error)
            assertTrue(
                "Seek command did not move to the requested region: ${connection.state.value.currentPositionMs} ms",
                connection.state.value.currentPositionMs in 700L..1_300L,
            )
        } finally {
            instrumentation.runOnMainSync {
                connection.pause()
                connection.disconnect()
            }
            activityScenario.close()
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

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val FIXTURE_SIZE_BYTES = 1_609
        const val FIXTURE_SHA256 = "bfc84c1d68e0c43336ba3c3e2103a95c732d96a7aa1ff1a8f6247bf7a0f3d22a"
        const val SAMPLE_MP4_BASE64 = "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"
    }
}

package com.zubaer.maxvideoplayer.playback

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end Step-1 playback certification on the Android emulator.
 *
 * The fixture is a deterministic two-second 160x90 H.264 Constrained Baseline MP4. Audio is
 * intentionally omitted so the binary remains tiny and deterministic. The shared instrumentation
 * fixture keeps the bytes identical across playback tests; size and SHA-256 are still asserted
 * here before Media3 sees them.
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
        val fixture = AndroidTestMediaFixture.writeShortH264Mp4(
            context,
            "step1_local_playback_fixture_${System.nanoTime()}.mp4",
        )
        val fixtureBytes = fixture.readBytes()
        assertEquals("Playback fixture byte length changed", FIXTURE_SIZE_BYTES, fixtureBytes.size)
        assertEquals("Playback fixture SHA-256 changed", FIXTURE_SHA256, fixtureBytes.sha256())
        assertEquals(FIXTURE_SIZE_BYTES.toLong(), fixture.length())

        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()

        val connection = PlaybackConnection(context)
        val media = AppMedia(
            stableId = "step1-local-playback-fixture-${System.nanoTime()}",
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

            // A MediaController may briefly publish the previous session item's terminal error
            // while the service is replacing the timeline. Certification therefore waits for the
            // requested media ID itself to become ready. A genuine failure of this file still
            // fails because the requested item never reaches a successful duration/error-free
            // state within the deadline.
            assertTrue(
                "Local MP4 did not become ready for ${media.stableId}: ${connection.state.value.error}",
                await(15_000L) {
                    val state = connection.state.value
                    state.mediaId == media.stableId &&
                        state.durationMs >= 1_500L &&
                        state.error == null
                },
            )
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
        const val FIXTURE_SHA256 = "f636bcf8f6bedbd668888db0e71a199c6b24f56e0c27b455d3d1725ab3165a69"
    }
}

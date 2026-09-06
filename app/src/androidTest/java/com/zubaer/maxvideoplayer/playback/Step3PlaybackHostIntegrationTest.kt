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

@RunWith(AndroidJUnit4::class)
class Step3PlaybackHostIntegrationTest {
    @Test
    fun serviceQueueSurvivesNavigationFullscreenRecreationAndPip() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "step3_host_fixture.mp4")
        fixture.writeBytes(Base64.decode(SAMPLE_MP4_BASE64, Base64.NO_WRAP))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
        val connection = PlaybackConnection(context)
        val queue = listOf("A", "B", "C").mapIndexed { index, suffix ->
            AppMedia(
                stableId = "step3-queue-${suffix.lowercase()}",
                uri = Uri.fromFile(fixture).toString(),
                title = "Step 3 Queue $suffix",
                mimeType = "video/mp4",
                durationMs = 2_000L,
                sizeBytes = fixture.length(),
                width = if (index == 2) 90 else 160,
                height = if (index == 2) 160 else 90,
                rotationDegrees = if (index == 2) 90 else 0,
                videoCodec = "h264",
                sourceType = MediaSourceType.SAF,
            )
        }

        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })

            instrumentation.runOnMainSync {
                connection.setQueue(queue, startIndex = 1, startPositionMs = 0L, playWhenReady = false)
            }
            assertTrue("Queue did not load selected item", await(15_000L) {
                connection.state.value.mediaId == queue[1].stableId || connection.state.value.error != null
            })
            assertNull("Queue load failed: ${connection.state.value.error}", connection.state.value.error)
            assertEquals(queue[1].stableId, connection.state.value.mediaId)
            assertEquals(3, connection.state.value.mediaItemCount)
            assertEquals(1, connection.state.value.currentMediaItemIndex)
            assertTrue(connection.state.value.hasPrevious)
            assertTrue(connection.state.value.hasNext)

            instrumentation.runOnMainSync { connection.seekToNext() }
            assertTrue("Next did not move queue", await(5_000L) { connection.state.value.mediaId == queue[2].stableId })
            assertEquals("Step 3 Queue C", connection.state.value.title)

            instrumentation.runOnMainSync { connection.seekToPrevious() }
            assertTrue("Previous did not restore queue item", await(5_000L) { connection.state.value.mediaId == queue[1].stableId })
            assertEquals("Step 3 Queue B", connection.state.value.title)

            instrumentation.runOnMainSync { connection.seekTo(750L) }
            assertTrue("Pre-recreation seek failed", await(5_000L) { connection.state.value.currentPositionMs in 500L..1_100L })

            scenario.onActivity { it.setFullscreen(true) }
            instrumentation.waitForIdleSync()
            assertEquals("Fullscreen must not replace the service session", queue[1].stableId, connection.state.value.mediaId)
            scenario.onActivity { it.setFullscreen(false) }
            instrumentation.waitForIdleSync()
            assertEquals("Leaving fullscreen must keep the session", queue[1].stableId, connection.state.value.mediaId)

            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertTrue("Activity recreation lost MediaSession queue state", await(5_000L) {
                connection.state.value.mediaId == queue[1].stableId && connection.state.value.error == null
            })
            assertEquals(3, connection.state.value.mediaItemCount)
            assertTrue("Activity recreation reset service-owned position", connection.state.value.currentPositionMs >= 400L)

            scenario.onActivity { it.enterPip(queue[1]) }
            assertTrue("PiP request did not enter PiP mode on API-35 emulator", await(5_000L) {
                var inPip = false
                runCatching { scenario.onActivity { inPip = it.isInPictureInPictureMode } }
                inPip
            })
            assertEquals("PiP must keep the same MediaSession item", queue[1].stableId, connection.state.value.mediaId)
            assertEquals(3, connection.state.value.mediaItemCount)
            assertNull(connection.state.value.error)
        } finally {
            instrumentation.runOnMainSync {
                connection.pause()
                connection.disconnect()
            }
            runCatching { scenario.close() }
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

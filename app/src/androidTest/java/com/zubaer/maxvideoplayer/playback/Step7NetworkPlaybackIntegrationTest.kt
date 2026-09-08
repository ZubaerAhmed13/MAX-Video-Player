package com.zubaer.maxvideoplayer.playback

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real production-path HTTP playback through PlaybackConnection -> service -> Media3 -> OkHttp. */
@RunWith(AndroidJUnit4::class)
class Step7NetworkPlaybackIntegrationTest {
    @Test
    fun authenticatedProgressiveHttpPlaysThroughTheSingleServiceOwnedPlayer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as MaxVideoPlayerApplication
        val fixture = AndroidTestMediaFixture.writeShortH264Mp4(context, "step7_http_fixture.mp4")
        val bytes = fixture.readBytes()
        val server = MockWebServer()
        repeat(4) {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Length", bytes.size)
                    .setHeader("Accept-Ranges", "bytes")
                    .body(Buffer().write(bytes))
                    .build(),
            )
        }
        server.start()
        val media = app.container.networkRepository.prepareDirect(
            rawUrl = server.url("/private/movie.mp4").toString(),
            title = "Authenticated network fixture",
            credential = NetworkCredential(username = "network-user", password = "network-password"),
        )
        assertEquals(MediaSourceType.NETWORK, media.sourceType)

        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val connection = PlaybackConnection(context)
        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })
            instrumentation.runOnMainSync { connection.load(media, playWhenReady = false) }
            assertTrue("HTTP media did not reach ready/error state", await(15_000L) {
                connection.state.value.durationMs >= 1_500L || connection.state.value.error != null
            })
            assertNull("HTTP media failed: ${connection.state.value.error}", connection.state.value.error)
            assertEquals(media.stableId, connection.state.value.mediaId)
            assertEquals(NetworkProtocol.HTTP, connection.state.value.network.protocol)
            assertTrue(connection.state.value.network.sanitizedUri?.contains("network-password") != true)
            assertTrue(connection.state.value.network.seekable == true)

            val request = server.takeRequest()
            assertEquals("/private/movie.mp4", request.url.encodedPath)
            assertTrue(request.headers["Authorization"].orEmpty().startsWith("Basic "))
            assertTrue(request.headers["User-Agent"].orEmpty().startsWith("MAXVideoPlayer/"))
        } finally {
            instrumentation.runOnMainSync {
                connection.pause()
                connection.disconnect()
            }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            server.close()
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
}

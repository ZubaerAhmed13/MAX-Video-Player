package com.zubaer.maxvideoplayer.playback

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step7AdaptiveStreamingIntegrationTest {
    @Test
    fun deterministicHlsAndDashUseMedia3AndHlsExposesRealQualityOverrides() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as MaxVideoPlayerApplication
        val server = assetServer(instrumentation.context)
        val hls = app.container.networkRepository.prepareDirect(server.url("/step7_hls/master.m3u8").toString(), "Step 7 HLS")
        val dash = app.container.networkRepository.prepareDirect(server.url("/step7_dash/manifest.mpd").toString(), "Step 7 DASH")
        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val connection = PlaybackConnection(context)
        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })

            instrumentation.runOnMainSync { connection.load(hls, playWhenReady = false) }
            assertTrue("HLS did not reach ready/error state", await(15_000L) {
                connection.state.value.mediaId == hls.stableId &&
                    (connection.state.value.durationMs >= 1_500L || connection.state.value.error != null)
            })
            assertNull("HLS failed: ${connection.state.value.error}", connection.state.value.error)
            assertEquals(NetworkProtocol.HLS, connection.state.value.network.protocol)
            assertTrue("HLS variants were not exposed as actual Media3 tracks", connection.state.value.videoTracks.mapNotNull { it.height }.toSet().containsAll(setOf(90, 180)))
            val low = connection.state.value.videoTracks.minBy { it.height ?: Int.MAX_VALUE }
            instrumentation.runOnMainSync { connection.selectVideoTrack(low.key) }
            assertTrue("Manual HLS quality override did not become effective", await(5_000L) {
                !connection.state.value.videoQualityAuto && connection.state.value.videoTracks.any { it.key == low.key && it.selected }
            })
            instrumentation.runOnMainSync { connection.selectVideoQualityAuto() }
            assertTrue("Auto adaptive quality was not restored", await(5_000L) { connection.state.value.videoQualityAuto })

            instrumentation.runOnMainSync { connection.load(dash, playWhenReady = false) }
            assertTrue("DASH did not reach ready/error state", await(15_000L) {
                connection.state.value.mediaId == dash.stableId && (connection.state.value.durationMs >= 1_500L || connection.state.value.error != null)
            })
            assertNull("DASH failed: ${connection.state.value.error}", connection.state.value.error)
            assertEquals(NetworkProtocol.DASH, connection.state.value.network.protocol)
            assertFalse(connection.state.value.videoTracks.isEmpty())
        } finally {
            instrumentation.runOnMainSync {
                connection.pause()
                connection.disconnect()
            }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            server.close()
        }
    }

    private fun assetServer(context: Context): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath.removePrefix("/")
                val bytes = runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull()
                    ?: return MockResponse.Builder().code(404).build()
                val contentType = when {
                    path.endsWith(".m3u8") -> "application/x-mpegURL"
                    path.endsWith(".mpd") -> "application/dash+xml"
                    path.endsWith(".ts") -> "video/mp2t"
                    path.endsWith(".m4s") -> "video/mp4"
                    else -> "application/octet-stream"
                }
                return MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Type", contentType)
                    .setHeader("Content-Length", bytes.size)
                    .body(Buffer().write(bytes))
                    .build()
            }
        }
        start()
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

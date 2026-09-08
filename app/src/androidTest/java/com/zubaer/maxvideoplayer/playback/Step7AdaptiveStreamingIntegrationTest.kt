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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class Step7AdaptiveStreamingIntegrationTest {
    @Test
    fun deterministicHlsAndDashUseMedia3AndHlsExposesRealQualityOverrides() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as MaxVideoPlayerApplication
        val server = assetServer(instrumentation.context)
        val hls = app.container.networkRepository.prepareDirect(server.server.url("/step7_hls/master.m3u8").toString(), "Step 7 HLS")
        val hlsLive = app.container.networkRepository.prepareDirect(server.server.url("/step7_hls/live.m3u8").toString(), "Step 7 live HLS")
        val dash = app.container.networkRepository.prepareDirect(server.server.url("/step7_dash/multi.mpd").toString(), "Step 7 DASH")
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
            assertTrue("HLS variants were not exposed as actual Media3 tracks", await(5_000L) {
                connection.state.value.videoTracks.mapNotNull { it.height }.toSet().containsAll(setOf(90, 180))
            })
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
            assertTrue("DASH representations were not exposed as actual Media3 tracks", await(5_000L) {
                connection.state.value.videoTracks.mapNotNull { it.height }.toSet().containsAll(setOf(90, 180))
            })

            instrumentation.runOnMainSync { connection.load(hlsLive, playWhenReady = false) }
            assertTrue("Live HLS did not expose Media3 live state", await(15_000L) {
                connection.state.value.mediaId == hlsLive.stableId &&
                    (connection.state.value.isLive || connection.state.value.error != null)
            })
            assertNull("Live HLS failed: ${connection.state.value.error}", connection.state.value.error)
            assertTrue(connection.state.value.isLive)
            assertTrue("Live HLS playlist did not advance its media sequence", await(5_000L) {
                server.latestLiveMediaSequence.get() >= 1
            })
            instrumentation.runOnMainSync { connection.seekTo(0L) }
            assertTrue("Live HLS did not expose a seekable offset behind the live edge", await(5_000L) {
                connection.state.value.currentPositionMs <= 1_000L &&
                    (connection.state.value.liveOffsetMs ?: 0L) >= 6_000L
            })
            val behindLiveOffsetMs = connection.state.value.liveOffsetMs ?: Long.MAX_VALUE
            instrumentation.runOnMainSync { connection.goLive() }
            assertTrue("Go Live did not move playback toward the live edge", await(5_000L) {
                val liveOffsetMs = connection.state.value.liveOffsetMs
                connection.state.value.mediaId == hlsLive.stableId &&
                    liveOffsetMs != null && liveOffsetMs < behindLiveOffsetMs
            })
        } finally {
            instrumentation.runOnMainSync {
                connection.pause()
                connection.playerOrNull()?.stop()
                connection.disconnect()
            }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            server.server.close()
        }
    }

    private fun assetServer(context: Context): LiveAssetServer {
        val latestLiveMediaSequence = AtomicInteger()
        val liveStartedAt = Instant.now().minusSeconds(LIVE_WINDOW_SEGMENTS.toLong())
        val liveStartedRealtimeMs = SystemClock.elapsedRealtime()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath.removePrefix("/")
                    if (path == "step7_hls/live.m3u8") {
                        val mediaSequence = ((SystemClock.elapsedRealtime() - liveStartedRealtimeMs) / 1_000L).toInt()
                        latestLiveMediaSequence.set(mediaSequence)
                        return MockResponse.Builder()
                            .code(200)
                            .setHeader("Content-Type", "application/x-mpegURL")
                            .body(livePlaylist(mediaSequence, liveStartedAt.plusSeconds(mediaSequence.toLong())))
                            .build()
                    }
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
        return LiveAssetServer(server, latestLiveMediaSequence)
    }

    private fun livePlaylist(mediaSequence: Int, windowStartedAt: Instant): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:6")
        appendLine("#EXT-X-TARGETDURATION:1")
        appendLine("#EXT-X-MEDIA-SEQUENCE:$mediaSequence")
        appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
        appendLine("#EXT-X-PROGRAM-DATE-TIME:${DateTimeFormatter.ISO_INSTANT.format(windowStartedAt)}")
        repeat(LIVE_WINDOW_SEGMENTS) { index ->
            val sequence = mediaSequence + index
            appendLine("#EXTINF:1.000000,")
            appendLine("low/segment${sequence % 2}.ts?sequence=$sequence")
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

    private data class LiveAssetServer(
        val server: MockWebServer,
        val latestLiveMediaSequence: AtomicInteger,
    )

    private companion object {
        const val LIVE_WINDOW_SEGMENTS = 8
    }
}

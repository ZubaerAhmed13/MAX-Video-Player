package com.zubaer.maxvideoplayer.playback

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderBackendType
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderFailureCode
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Capability-aware Step-6 certification using the real service-owned production playback path.
 * No Assume/skip is used: when a backend is unavailable, the test requires the corresponding
 * truthful unavailable state instead of pretending the mode passed.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class Step6DecoderIntegrationTest {

    @Test
    fun autoAndExplicitModesReportActualInitializedDecoderAndPreservePosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as MaxVideoPlayerApplication
        val repository = app.container.decoderRepository
        val fixture = AndroidTestMediaFixture.writeShortH264Mp4(context, "step6_decoder_fixture.mp4")
        val media = AppMedia(
            stableId = "step6-decoder-fixture",
            uri = Uri.fromFile(fixture).toString(),
            title = "Step 6 decoder fixture",
            mimeType = "video/mp4",
            durationMs = 2_000L,
            sizeBytes = fixture.length(),
            width = 160,
            height = 90,
            videoCodec = "h264",
            sourceType = MediaSourceType.SAF,
        )
        val platform = MediaCodecSelector.DEFAULT.getDecoderInfos("video/avc", false, false)
        val softwareNames = platform.filter { it.softwareOnly }.map { it.name }.toSet()
        val hardwareNames = platform.filter { it.hardwareAccelerated }.map { it.name }.toSet()

        // The full instrumentation matrix intentionally shares one application process. Ensure this
        // certification starts with a fresh service rather than inheriting a source/error from the
        // immediately preceding regression test whose temporary fixture has already been deleted.
        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()

        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        val connection = PlaybackConnection(context)
        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })

            // requestModeForCurrentMedia is deliberately a current-media API. First make the
            // Step-6 fixture authoritative in both MediaController and DecoderRepository. Issuing
            // a mode request for a future media ID would reconfigure the preceding item's queue and
            // can race its snapshot/restore over this load in a shared instrumentation process.
            instrumentation.runOnMainSync { connection.load(media, startPositionMs = 0L, playWhenReady = true) }
            assertTrue("Step-6 fixture did not become the authoritative current media", await(10_000L) {
                connection.state.value.mediaId == media.stableId && repository.state.value.mediaId == media.stableId
            })
            repository.requestModeForCurrentMedia(media.stableId, DecoderMode.AUTO)
            assertTrue("Auto did not initialize a decoder or report a failure for the Step-6 fixture", await(15_000L) {
                val playback = connection.state.value
                playback.mediaId == media.stableId &&
                    playback.error == null && (
                    repository.state.value.diagnostics.activeDecoderName != null ||
                        repository.state.value.diagnostics.lastFailure != null
                    )
            })
            assertEquals(media.stableId, connection.state.value.mediaId)
            assertNull("Auto playback failed: ${connection.state.value.error}", connection.state.value.error)
            val auto = repository.state.value.diagnostics
            assertEquals(DecoderMode.AUTO, auto.requestedMode)
            assertTrue("Auto did not record the actual initialized decoder", auto.activeDecoderName != null)
            assertTrue(
                "Auto effective backend did not match the initialized decoder classification",
                when (auto.effectiveBackend) {
                    DecoderBackendType.HARDWARE -> auto.activeDecoderName in hardwareNames
                    DecoderBackendType.SOFTWARE -> auto.activeDecoderName in softwareNames
                    DecoderBackendType.UNKNOWN -> auto.activeDecoderName !in softwareNames && auto.activeDecoderName !in hardwareNames
                    null -> false
                },
            )

            instrumentation.runOnMainSync { connection.pause(); connection.seekTo(1_000L) }
            assertTrue("Seek before decoder switch failed", await(5_000L) {
                connection.state.value.currentPositionMs in 700L..1_300L
            })

            repository.requestModeForCurrentMedia(media.stableId, DecoderMode.SOFTWARE)
            if (softwareNames.isNotEmpty()) {
                assertTrue("Software mode did not activate a real software decoder", await(15_000L) {
                    repository.state.value.diagnostics.activeDecoderName in softwareNames &&
                        repository.state.value.diagnostics.effectiveBackend == DecoderBackendType.SOFTWARE
                })
                val software = repository.state.value.diagnostics
                assertEquals(DecoderMode.SOFTWARE, software.requestedMode)
                assertTrue(software.activeDecoderName in softwareNames)
                assertEquals(DecoderBackendType.SOFTWARE, software.effectiveBackend)
                assertTrue("Decoder switch restarted the movie from zero", connection.state.value.currentPositionMs in 650L..1_350L)
            } else {
                assertTrue("Software absence was not reported truthfully", await(10_000L) {
                    repository.state.value.diagnostics.lastFailure?.code == DecoderFailureCode.SOFTWARE_BACKEND_UNAVAILABLE
                })
                assertTrue(repository.state.value.diagnostics.activeDecoderName !in hardwareNames)
            }

            repository.requestModeForCurrentMedia(media.stableId, DecoderMode.HARDWARE)
            if (hardwareNames.isNotEmpty()) {
                assertTrue("Hardware mode did not activate a real hardware decoder", await(15_000L) {
                    repository.state.value.diagnostics.activeDecoderName in hardwareNames &&
                        repository.state.value.diagnostics.effectiveBackend == DecoderBackendType.HARDWARE
                })
                val hardware = repository.state.value.diagnostics
                assertEquals(DecoderMode.HARDWARE, hardware.requestedMode)
                assertTrue(hardware.activeDecoderName in hardwareNames)
                assertTrue(hardware.activeDecoderName !in softwareNames)
                assertEquals(DecoderBackendType.HARDWARE, hardware.effectiveBackend)
            } else {
                assertTrue("Hardware absence was not reported truthfully", await(10_000L) {
                    repository.state.value.diagnostics.lastFailure?.code == DecoderFailureCode.NO_COMPATIBLE_DECODER
                })
                assertTrue(repository.state.value.diagnostics.activeDecoderName !in softwareNames)
            }

            repository.requestModeForCurrentMedia(media.stableId, DecoderMode.ENHANCED_HARDWARE)
            if (hardwareNames.isNotEmpty()) {
                assertTrue("Enhanced Hardware leaked to software or failed to activate hardware", await(15_000L) {
                    repository.state.value.requestedMode == DecoderMode.ENHANCED_HARDWARE &&
                        repository.state.value.diagnostics.activeDecoderName in hardwareNames &&
                        repository.state.value.diagnostics.effectiveBackend == DecoderBackendType.HARDWARE
                })
                val enhanced = repository.state.value.diagnostics
                assertEquals(DecoderMode.ENHANCED_HARDWARE, enhanced.requestedMode)
                assertTrue(enhanced.activeDecoderName in hardwareNames)
                assertTrue(enhanced.activeDecoderName !in softwareNames)
                assertEquals(DecoderBackendType.HARDWARE, enhanced.effectiveBackend)
            } else {
                assertTrue("Enhanced Hardware absence was not reported truthfully", await(10_000L) {
                    repository.state.value.diagnostics.lastFailure?.code == DecoderFailureCode.NO_COMPATIBLE_DECODER
                })
                assertTrue(repository.state.value.diagnostics.activeDecoderName !in softwareNames)
            }
        } finally {
            repository.useGlobalForCurrentMedia(media.stableId)
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
}

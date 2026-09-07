package com.zubaer.maxvideoplayer.feature.audio

import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.MediaExtractor
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleFileDescriptor
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleFormat
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Step-5 production-path certification. Fixtures are synthetic and redistribution-safe.
 * Commands travel through PlaybackConnection -> MediaController -> PlaybackService -> Media3.
 */
@RunWith(AndroidJUnit4::class)
class ProfessionalAudioIntegrationTest {

    @Test
    fun productionAudioPathSupportsTracksExternalAudioDspSyncAudioOnlyAndSubtitleCoexistence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val app = context.applicationContext as MaxVideoPlayerApplication
        val container = app.container
        val connection = container.playbackConnection
        val audio = container.audioRepository
        val controller = container.audioPlaybackController
        val originalLanguages = audio.state.value.preferredLanguages

        val multiAudio = copyAsset(context, testContext, "step5_multi_audio.mp4")
        val externalAudio = createExternalPcmWav(context.cacheDir)
        val externalSubtitle = copyAsset(context, testContext, "step5_external.srt")
        certifyFixtureStructure(multiAudio)
        certifyExternalAudioFixture(externalAudio)

        val mediaId = "step5-professional-audio-${System.currentTimeMillis()}"
        val media = AppMedia(
            stableId = mediaId,
            uri = Uri.fromFile(multiAudio).toString(),
            title = "Step 5 synthetic multi-audio fixture",
            mimeType = "video/mp4",
            durationMs = 2_000L,
            sizeBytes = multiAudio.length(),
            width = 160,
            height = 90,
            sourceType = MediaSourceType.SAF,
        )

        controller.setEqualizerEnabled(false)
        controller.setPreamp(0f)
        controller.setBoost(0f)
        controller.setBalance(0f)
        controller.setChannelMode(AudioChannelMode.STEREO)
        instrumentation.runOnMainSync { controller.setPitch(1f) }
        controller.setBackgroundMode(BackgroundPlaybackMode.CONTINUE_AUDIO)
        controller.setDisableVideoInBackground(false)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })
            instrumentation.runOnMainSync { connection.load(media, 0L, false) }
            assertTrue("Synthetic fixture did not load", await(15_000L) {
                connection.state.value.durationMs > 0L || connection.state.value.error != null
            })
            assertTrue("Synthetic fixture playback error: ${connection.state.value.error}", connection.state.value.error == null)

            instrumentation.runOnMainSync { controller.bind() }
            assertTrue("Embedded multi-audio tracks were not discovered", await(10_000L) {
                audio.state.value.tracks.count { !it.external && it.supported } >= 2
            })
            val embedded = audio.state.value.tracks.filter { !it.external && it.supported }
            assertTrue("Expected at least two real embedded audio tracks", embedded.size >= 2)
            assertTrue("Track labels must be human-readable", embedded.all { it.label.isNotBlank() })

            instrumentation.runOnMainSync {
                controller.selectAuto()
                controller.setPreferredLanguage("bn")
            }
            assertEquals("bn", audio.state.value.preferredLanguages.firstOrNull())
            assertTrue("Preferred Auto language did not reach Media3", await(3_000L) {
                onMain(instrumentation) {
                    connection.playerOrNull()?.trackSelectionParameters?.preferredAudioLanguages?.firstOrNull() == "bn"
                }
            })

            val secondTrack = embedded[1]
            instrumentation.runOnMainSync { controller.selectTrack(secondTrack.key) }
            assertTrue("Manual embedded audio selection did not reach Media3", await(5_000L) {
                audio.state.value.selectionMode == AudioSelectionMode.MANUAL &&
                    audio.state.value.tracks.any { it.key == secondTrack.key && it.selected }
            })

            instrumentation.runOnMainSync {
                controller.setEqualizerEnabled(true)
                controller.setPreset(EqualizerPreset.VOCAL)
                controller.setBoost(3f)
            }
            assertTrue("Production AudioSink does not report installed DSP", audio.state.value.dspPipelineInstalled)
            assertTrue("EQ did not reach realtime DSP parameters", audio.realtimeParameters.get().equalizerEnabled)
            assertTrue("Boost did not reach realtime DSP parameters", audio.realtimeParameters.get().boostDb > 0f)

            instrumentation.runOnMainSync {
                controller.setAudioDelay(250L)
                controller.setRouteCompensation(100L)
            }
            assertEquals(250L, audio.state.value.audioDelayMs)
            assertEquals(100L, audio.state.value.routeCompensationMs)
            assertEquals(350L, audio.state.value.effectiveAudioDelayMs)

            instrumentation.runOnMainSync { controller.setPitch(1.2f) }
            assertTrue("Pitch did not reach playback parameters", await(3_000L) {
                onMain(instrumentation) {
                    connection.playerOrNull()?.playbackParameters?.pitch?.let { abs(it - 1.2f) < 0.01f } == true
                }
            })

            val subtitleDescriptor = SubtitleFileDescriptor(
                uri = Uri.fromFile(externalSubtitle).toString(),
                displayName = externalSubtitle.name,
                mimeType = MimeTypes.APPLICATION_SUBRIP,
                format = SubtitleFormat.SRT,
            )
            instrumentation.runOnMainSync { connection.attachExternalSubtitle(subtitleDescriptor) }
            assertTrue("External subtitle association was not persisted", await(5_000L) {
                container.subtitleRepository.externalAttachmentsFor(mediaId).isNotEmpty()
            })

            val externalDescriptor = audio.describe(Uri.fromFile(externalAudio))
            assertNotNull("Runtime-generated external WAV fixture was not recognized", externalDescriptor)
            assertEquals("bn", externalDescriptor?.displayName?.substringAfterLast('_')?.substringBeforeLast('.'))
            instrumentation.runOnMainSync { controller.attachExternal(externalDescriptor!!) }

            // Persistence and playback selection are deliberately certified as separate contracts.
            // Repository association state can publish before/after MediaSession track updates, so
            // do not require a transient cached `tracks.selected` projection to change in the same
            // polling iteration as the authoritative controller-visible Media3 Tracks state.
            assertTrue("External audio association/selection did not persist", await(10_000L) {
                val state = audio.state.value
                val selectedId = state.selectedExternalId
                selectedId != null && state.externalAudio.any { it.id == selectedId && it.preferred }
            })
            assertTrue("External audio did not become the selected Media3 audio track", await(15_000L) {
                onMain(instrumentation) {
                    connection.playerOrNull()?.currentTracks?.groups?.any { group ->
                        group.type == C.TRACK_TYPE_AUDIO &&
                            isExternalMedia3Group(group) &&
                            (0 until group.length).any { index -> group.isTrackSelected(index) }
                    } == true
                }
            })
            assertTrue("Embedded audio remained selected after explicit external-audio selection", await(5_000L) {
                onMain(instrumentation) {
                    connection.playerOrNull()?.currentTracks?.groups
                        ?.filter { group -> group.type == C.TRACK_TYPE_AUDIO && !isExternalMedia3Group(group) }
                        ?.none { group -> (0 until group.length).any { index -> group.isTrackSelected(index) } } == true
                }
            })
            assertTrue("External audio triggered a playback error: ${connection.state.value.error}", connection.state.value.error == null)
            assertTrue(
                "External audio replaced Step-4 subtitle association",
                container.subtitleRepository.externalAttachmentsFor(mediaId).isNotEmpty(),
            )
            assertEquals(mediaId, onMain(instrumentation) { connection.playerOrNull()?.currentMediaItem?.mediaId })

            val positionBefore = onMain(instrumentation) { connection.playerOrNull()?.currentPosition ?: 0L }
            instrumentation.runOnMainSync { controller.setAudioOnly(true) }
            assertTrue("Audio-only did not disable the video track", await(3_000L) {
                onMain(instrumentation) {
                    connection.playerOrNull()?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) == true
                }
            })
            instrumentation.runOnMainSync { controller.setAudioOnly(false) }
            assertTrue("Video track was not restored", await(3_000L) {
                onMain(instrumentation) {
                    connection.playerOrNull()?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) == false
                }
            })
            assertEquals(mediaId, onMain(instrumentation) { connection.playerOrNull()?.currentMediaItem?.mediaId })
            val positionAfterAudioOnly = onMain(instrumentation) { connection.playerOrNull()?.currentPosition ?: 0L }
            assertTrue(
                "Audio-only unexpectedly restarted from zero",
                positionAfterAudioOnly >= (positionBefore - 500L).coerceAtLeast(0L),
            )

            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertTrue("Playback session was lost across Activity recreation", await(5_000L) {
                connection.state.value.connected && onMain(instrumentation) {
                    connection.playerOrNull()?.currentMediaItem?.mediaId == mediaId
                }
            })
            assertTrue("Audio state was lost across Activity recreation", audio.state.value.equalizerEnabled)
            assertEquals(250L, audio.state.value.audioDelayMs)
            assertTrue("External audio association was lost across recreation", audio.state.value.selectedExternalId != null)

            instrumentation.runOnMainSync {
                controller.setBackgroundMode(BackgroundPlaybackMode.PIP_WHEN_POSSIBLE)
                controller.setDisableVideoInBackground(true)
                controller.setRouteCompensation(180L)
            }
            val recreatedPreferences = AudioRepository(context)
            assertEquals(BackgroundPlaybackMode.PIP_WHEN_POSSIBLE, recreatedPreferences.state.value.backgroundMode)
            assertTrue(recreatedPreferences.state.value.disableVideoInBackground)
            assertEquals(180L, recreatedPreferences.routeCompensationFor(audio.state.value.currentRoute.type))
            assertEquals("bn", recreatedPreferences.state.value.preferredLanguages.firstOrNull())
        } finally {
            instrumentation.runOnMainSync {
                originalLanguages.firstOrNull()?.let(controller::setPreferredLanguage)
                controller.setAudioOnly(false)
                controller.setBackgroundVideoDisabled(false)
                controller.setEqualizerEnabled(false)
                controller.setBoost(0f)
                controller.setAudioDelay(0L)
                controller.setRouteCompensation(0L)
                controller.setPitch(1f)
                connection.pause()
                controller.unbind()
                connection.disconnect()
            }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            multiAudio.delete()
            externalAudio.delete()
            externalSubtitle.delete()
        }
    }

    @Test
    fun routeClassificationCoversProfessionalOutputFamiliesWithoutPhysicalHardware() {
        assertEquals(AudioRouteType.SPEAKER, AudioRouteMonitor.routeTypeForDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(AudioRouteType.WIRED_HEADSET, AudioRouteMonitor.routeTypeForDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertEquals(AudioRouteType.WIRED_HEADPHONES, AudioRouteMonitor.routeTypeForDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(AudioRouteType.BLUETOOTH_A2DP, AudioRouteMonitor.routeTypeForDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(AudioRouteType.USB, AudioRouteMonitor.routeTypeForDeviceType(AudioDeviceInfo.TYPE_USB_DEVICE))
        assertEquals(AudioRouteType.HDMI, AudioRouteMonitor.routeTypeForDeviceType(AudioDeviceInfo.TYPE_HDMI))
        assertEquals(AudioRouteType.UNKNOWN, AudioRouteMonitor.routeTypeForDeviceType(Int.MAX_VALUE))
    }

    /**
     * MergingMediaPeriod prefixes child-1 Format IDs with "1:". MediaSession may replace the
     * TrackGroup ID with a controller-unique ID, so certification intentionally accepts either
     * service-side group identity or the preserved merged Format identity.
     */
    private fun isExternalMedia3Group(group: androidx.media3.common.Tracks.Group): Boolean {
        if (group.mediaTrackGroup.id.startsWith("1:")) return true
        return (0 until group.length).any { index ->
            group.getTrackFormat(index).id?.startsWith("1:") == true
        }
    }

    private fun certifyFixtureStructure(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var videoTracks = 0
            var audioTracks = 0
            repeat(extractor.trackCount) { index ->
                val mime = extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) videoTracks++
                if (mime.startsWith("audio/")) audioTracks++
            }
            assertTrue("Synthetic fixture must contain a video track", videoTracks >= 1)
            assertTrue("Synthetic fixture must contain at least two audio tracks", audioTracks >= 2)
        } finally {
            extractor.release()
        }
    }

    private fun certifyExternalAudioFixture(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTracks = 0
            var videoTracks = 0
            repeat(extractor.trackCount) { index ->
                val mime = extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) audioTracks++
                if (mime.startsWith("video/")) videoTracks++
            }
            assertEquals("External fixture must be audio-only", 0, videoTracks)
            assertEquals("External fixture must expose exactly one audio track", 1, audioTracks)
        } finally {
            extractor.release()
        }
    }

    /** Creates a deterministic 4-second, 48 kHz, mono PCM16 WAV without opaque binary assets. */
    private fun createExternalPcmWav(directory: File): File {
        val sampleRate = 48_000
        val channels = 1
        val bitsPerSample = 16
        val seconds = 4
        val bytesPerSample = bitsPerSample / 8
        val dataSize = sampleRate * channels * bytesPerSample * seconds
        val file = File(directory, "step5_external_bn.wav")
        file.outputStream().buffered().use { output ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * bytesPerSample)
            header.putShort((channels * bytesPerSample).toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            output.write(header.array())

            val silence = ByteArray(8 * 1024)
            var remaining = dataSize
            while (remaining > 0) {
                val count = minOf(remaining, silence.size)
                output.write(silence, 0, count)
                remaining -= count
            }
        }
        assertEquals(44L + dataSize.toLong(), file.length())
        return file
    }

    private fun copyAsset(
        targetContext: android.content.Context,
        testContext: android.content.Context,
        name: String,
    ): File = File(targetContext.cacheDir, "cert-$name").apply {
        testContext.assets.open(name).use { input ->
            outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun <T> onMain(
        instrumentation: android.app.Instrumentation,
        block: () -> T,
    ): T {
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

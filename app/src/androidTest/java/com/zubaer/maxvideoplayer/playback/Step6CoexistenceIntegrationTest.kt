package com.zubaer.maxvideoplayer.playback

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
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
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import com.zubaer.maxvideoplayer.feature.audio.AudioChannelMode
import com.zubaer.maxvideoplayer.feature.audio.EqualizerPreset
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderBackendType
import com.zubaer.maxvideoplayer.feature.decoder.runtime.DecoderRepository
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleFileDescriptor
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleFormat
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Step-6 coexistence hardening.
 *
 * These tests intentionally exercise the real service-owned playback graph rather than policy
 * helpers or mocked players. They close the cross-step certification gaps between Step 6 decoder
 * reconfiguration and the Step 3 queue/session, Step 4 external subtitles, and Step 5 audio/DSP.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class Step6CoexistenceIntegrationTest {

    @Test
    fun queueSidecarsDspPoliciesAndRecreationSurviveDecoderSwitch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val app = context.applicationContext as MaxVideoPlayerApplication
        val container = app.container
        val connection = container.playbackConnection
        val audio = container.audioRepository
        val audioController = container.audioPlaybackController
        val decoder = container.decoderRepository
        val subtitleRepository = container.subtitleRepository

        val platform = MediaCodecSelector.DEFAULT.getDecoderInfos("video/avc", false, false)
        val softwareNames = platform.filter { it.softwareOnly }.map { it.name }.toSet()
        val hardwareNames = platform.filter { it.hardwareAccelerated }.map { it.name }.toSet()
        assertTrue(
            "API-35 coexistence certification requires at least one classified AVC decoder",
            softwareNames.isNotEmpty() || hardwareNames.isNotEmpty(),
        )

        val video = copyAsset(context, testContext, "step5_multi_audio.mp4", "step6_coexistence_video.mp4")
        val externalSubtitle = copyAsset(context, testContext, "step5_external.srt", "step6_coexistence_external.srt")
        val externalAudio = createExternalPcmWav(context.cacheDir, "step6_coexistence_external_bn.wav")
        val runId = System.currentTimeMillis()
        val queue = listOf("A", "B", "C").map { suffix ->
            AppMedia(
                stableId = "step6-coexistence-$runId-${suffix.lowercase()}",
                uri = Uri.fromFile(video).toString(),
                title = "Step 6 coexistence $suffix",
                mimeType = "video/mp4",
                durationMs = 2_000L,
                sizeBytes = video.length(),
                width = 160,
                height = 90,
                videoCodec = "h264",
                sourceType = MediaSourceType.SAF,
            )
        }
        val mediaB = queue[1]
        val subtitleDescriptor = SubtitleFileDescriptor(
            uri = Uri.fromFile(externalSubtitle).toString(),
            displayName = externalSubtitle.name,
            mimeType = MimeTypes.APPLICATION_SUBRIP,
            format = SubtitleFormat.SRT,
        )
        var selectedExternalAudioId: String? = null

        instrumentation.runOnMainSync {
            audioController.unbind()
            connection.disconnect()
        }
        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })

            instrumentation.runOnMainSync {
                connection.setQueue(queue, startIndex = 1, startPositionMs = 0L, playWhenReady = false)
            }
            assertTrue("A/B/C queue did not load B", await(15_000L) {
                connection.state.value.mediaId == mediaB.stableId &&
                    connection.state.value.mediaItemCount == 3 &&
                    connection.state.value.error == null &&
                    decoder.state.value.mediaId == mediaB.stableId
            })
            assertEquals(listOf(queue[0].stableId, mediaB.stableId, queue[2].stableId), queueIds(instrumentation, connection))
            assertEquals(1, connection.state.value.currentMediaItemIndex)

            instrumentation.runOnMainSync { audioController.bind() }
            assertTrue("Embedded audio tracks were not discovered", await(10_000L) {
                audio.state.value.tracks.count { !it.external && it.supported } >= 2
            })

            instrumentation.runOnMainSync { connection.attachExternalSubtitle(subtitleDescriptor) }
            assertTrue("External subtitle did not become selected", await(10_000L) {
                val subtitles = connection.state.value.subtitles
                subtitles.externalAttached && subtitles.tracks.any { it.external && it.selected }
            })

            val externalDescriptor = audio.describe(Uri.fromFile(externalAudio))
            assertTrue("External WAV fixture was not recognized", externalDescriptor != null)
            instrumentation.runOnMainSync { audioController.attachExternal(externalDescriptor!!) }
            assertTrue("External audio association was not selected", await(10_000L) {
                audio.state.value.selectedExternalId != null
            })
            selectedExternalAudioId = audio.state.value.selectedExternalId
            assertTrue("External audio did not become the selected Media3 track", await(15_000L) {
                externalAudioSelected(instrumentation, connection)
            })

            instrumentation.runOnMainSync {
                audioController.setEqualizerEnabled(true)
                audioController.setPreset(EqualizerPreset.VOCAL)
                audioController.setPreamp(2f)
                audioController.setBoost(3f)
                audioController.setChannelMode(AudioChannelMode.STEREO)
                audioController.setBalance(0f)
                audioController.setAudioDelay(250L)
                audioController.setRouteCompensation(100L)
                connection.setPlaybackSpeed(1.5f)
                audioController.setPitch(1.2f)
                connection.setRepeatMode(RepeatMode.ALL)
                connection.setShuffleEnabled(false)
                connection.seekTo(800L)
            }
            assertTrue("Pre-switch seek failed", await(5_000L) {
                authoritativePosition(instrumentation, connection) in 500L..1_150L
            })

            decoder.requestModeForCurrentMedia(mediaB.stableId, DecoderMode.AUTO)
            assertTrue("Auto did not initialize before coexistence switch", await(15_000L) {
                activeMode(decoder, DecoderMode.AUTO, softwareNames, hardwareNames)
            })

            val primaryMode = when {
                softwareNames.isNotEmpty() -> DecoderMode.SOFTWARE
                hardwareNames.isNotEmpty() -> DecoderMode.ENHANCED_HARDWARE
                else -> DecoderMode.AUTO
            }
            decoder.requestModeForCurrentMedia(mediaB.stableId, primaryMode)
            assertTrue("Primary decoder switch did not complete", await(15_000L) {
                activeMode(decoder, primaryMode, softwareNames, hardwareNames)
            })

            assertQueueAndPolicies(
                instrumentation = instrumentation,
                connection = connection,
                expectedIds = queue.map { it.stableId },
                expectedIndex = 1,
                repeatMode = RepeatMode.ALL,
                shuffle = false,
                speed = 1.5f,
                pitch = 1.2f,
            )
            assertTrue(
                "Decoder switch did not preserve B position",
                authoritativePosition(instrumentation, connection) in 450L..1_250L,
            )
            assertAudioAndSubtitleCoexistence(
                instrumentation = instrumentation,
                connection = connection,
                externalAudioId = selectedExternalAudioId!!,
                expectedAudioDelayMs = 250L,
                expectedRouteDelayMs = 100L,
                app = app,
            )

            // Prove the Step-4 side-loaded cue still renders after decoder reconfiguration.
            instrumentation.runOnMainSync {
                connection.seekTo(0L)
                connection.play()
            }
            assertTrue("External subtitle stopped rendering after decoder switch", await(8_000L) {
                onMain(instrumentation) {
                    connection.playerOrNull()?.currentCues?.cues?.any {
                        it.text?.toString()?.contains("Step 5 subtitle coexistence") == true
                    } == true
                }
            })
            instrumentation.runOnMainSync { connection.pause() }

            // Previous/Next must still use the original A/B/C timeline after the decoder switch.
            instrumentation.runOnMainSync { connection.seekToNext() }
            assertTrue("Next after decoder switch did not reach C", await(7_000L) {
                connection.state.value.mediaId == queue[2].stableId && connection.state.value.error == null
            })
            assertEquals(queue.map { it.stableId }, queueIds(instrumentation, connection))
            instrumentation.runOnMainSync { connection.seekToPrevious() }
            assertTrue("Previous after decoder switch did not return to B", await(10_000L) {
                connection.state.value.mediaId == mediaB.stableId &&
                    decoder.state.value.mediaId == mediaB.stableId &&
                    connection.state.value.error == null
            })
            assertTrue("B decoder override was not restored after queue navigation", await(15_000L) {
                activeMode(decoder, primaryMode, softwareNames, hardwareNames)
            })
            assertAudioAndSubtitleCoexistence(
                instrumentation = instrumentation,
                connection = connection,
                externalAudioId = selectedExternalAudioId!!,
                expectedAudioDelayMs = 250L,
                expectedRouteDelayMs = 100L,
                app = app,
            )

            // Now explicitly certify shuffle + repeat preservation through a second decoder switch.
            instrumentation.runOnMainSync { connection.setShuffleEnabled(true) }
            assertTrue("Shuffle did not enable before second switch", await(3_000L) {
                connection.state.value.shuffleEnabled
            })
            val secondaryMode = when {
                hardwareNames.isNotEmpty() && primaryMode != DecoderMode.HARDWARE -> DecoderMode.HARDWARE
                softwareNames.isNotEmpty() && primaryMode != DecoderMode.SOFTWARE -> DecoderMode.SOFTWARE
                primaryMode != DecoderMode.AUTO -> DecoderMode.AUTO
                else -> DecoderMode.ENHANCED_HARDWARE
            }
            decoder.requestModeForCurrentMedia(mediaB.stableId, secondaryMode)
            assertTrue("Second decoder switch did not complete", await(15_000L) {
                activeMode(decoder, secondaryMode, softwareNames, hardwareNames)
            })
            assertQueueAndPolicies(
                instrumentation = instrumentation,
                connection = connection,
                expectedIds = queue.map { it.stableId },
                expectedIndex = 1,
                repeatMode = RepeatMode.ALL,
                shuffle = true,
                speed = 1.5f,
                pitch = 1.2f,
            )
            assertAudioAndSubtitleCoexistence(
                instrumentation = instrumentation,
                connection = connection,
                externalAudioId = selectedExternalAudioId!!,
                expectedAudioDelayMs = 250L,
                expectedRouteDelayMs = 100L,
                app = app,
            )

            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertTrue("Activity recreation lost the service-owned B queue item", await(7_000L) {
                connection.state.value.connected &&
                    connection.state.value.mediaId == mediaB.stableId &&
                    connection.state.value.mediaItemCount == 3 &&
                    connection.state.value.error == null
            })
            assertTrue("Selected decoder mode was lost across Activity recreation", await(15_000L) {
                activeMode(decoder, secondaryMode, softwareNames, hardwareNames)
            })
            assertQueueAndPolicies(
                instrumentation = instrumentation,
                connection = connection,
                expectedIds = queue.map { it.stableId },
                expectedIndex = 1,
                repeatMode = RepeatMode.ALL,
                shuffle = true,
                speed = 1.5f,
                pitch = 1.2f,
            )
            assertAudioAndSubtitleCoexistence(
                instrumentation = instrumentation,
                connection = connection,
                externalAudioId = selectedExternalAudioId!!,
                expectedAudioDelayMs = 250L,
                expectedRouteDelayMs = 100L,
                app = app,
            )
        } finally {
            runCatching {
                instrumentation.runOnMainSync {
                    audioController.setAudioOnly(false)
                    connection.setShuffleEnabled(false)
                    connection.setRepeatMode(RepeatMode.OFF)
                    connection.setPlaybackSpeed(1f)
                    audioController.setPitch(1f)
                    audioController.setEqualizerEnabled(false)
                    audioController.setPreset(EqualizerPreset.FLAT)
                    audioController.setPreamp(0f)
                    audioController.setBoost(0f)
                    audioController.setAudioDelay(0L)
                    audioController.setRouteCompensation(0L)
                    selectedExternalAudioId?.let(audioController::removeExternal)
                    connection.clearExternalSubtitle()
                    decoder.useGlobalForCurrentMedia(mediaB.stableId)
                    connection.pause()
                    audioController.unbind()
                    connection.disconnect()
                }
            }
            runCatching { scenario.close() }
            context.stopService(Intent(context, PlaybackService::class.java))
            subtitleRepository.clearExternalAttachment(mediaB.stableId)
            video.delete()
            externalSubtitle.delete()
            externalAudio.delete()
        }
    }

    @Test
    fun decoderChangeWhileAudioOnlyRestoresVideoUsingNewlyRequestedDecoder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val app = context.applicationContext as MaxVideoPlayerApplication
        val container = app.container
        val connection = container.playbackConnection
        val audio = container.audioRepository
        val audioController = container.audioPlaybackController
        val decoder = container.decoderRepository

        val platform = MediaCodecSelector.DEFAULT.getDecoderInfos("video/avc", false, false)
        val softwareNames = platform.filter { it.softwareOnly }.map { it.name }.toSet()
        val hardwareNames = platform.filter { it.hardwareAccelerated }.map { it.name }.toSet()
        assertTrue(
            "Audio-only decoder certification requires a classified AVC backend",
            softwareNames.isNotEmpty() || hardwareNames.isNotEmpty(),
        )
        val targetMode = if (hardwareNames.isNotEmpty()) DecoderMode.HARDWARE else DecoderMode.SOFTWARE

        val video = copyAsset(context, testContext, "step5_multi_audio.mp4", "step6_audio_only_decoder.mp4")
        val media = AppMedia(
            stableId = "step6-audio-only-decoder-${System.currentTimeMillis()}",
            uri = Uri.fromFile(video).toString(),
            title = "Step 6 audio-only decoder",
            mimeType = "video/mp4",
            durationMs = 2_000L,
            sizeBytes = video.length(),
            width = 160,
            height = 90,
            videoCodec = "h264",
            sourceType = MediaSourceType.SAF,
        )

        instrumentation.runOnMainSync {
            audioController.unbind()
            connection.disconnect()
        }
        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()

        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })
            instrumentation.runOnMainSync { connection.load(media, 0L, false) }
            assertTrue("Audio-only fixture did not load", await(15_000L) {
                connection.state.value.mediaId == media.stableId &&
                    decoder.state.value.mediaId == media.stableId &&
                    connection.state.value.error == null
            })
            instrumentation.runOnMainSync { audioController.bind() }

            decoder.requestModeForCurrentMedia(media.stableId, DecoderMode.AUTO)
            assertTrue("Initial Auto decoder did not become active", await(15_000L) {
                activeMode(decoder, DecoderMode.AUTO, softwareNames, hardwareNames)
            })
            instrumentation.runOnMainSync { connection.seekTo(750L) }
            assertTrue("Pre-audio-only seek failed", await(5_000L) {
                authoritativePosition(instrumentation, connection) in 500L..1_100L
            })

            instrumentation.runOnMainSync { audioController.setAudioOnly(true) }
            assertTrue("Audio-only did not disable video", await(5_000L) {
                audio.state.value.audioOnlyMode && videoDisabled(instrumentation, connection)
            })

            // Change the decoder policy while video is intentionally disabled. The mode change is
            // authoritative immediately, but video must remain disabled and playback must not fail.
            decoder.requestModeForCurrentMedia(media.stableId, targetMode)
            assertTrue("Requested decoder mode was not accepted while Audio-only was enabled", await(10_000L) {
                decoder.state.value.requestedMode == targetMode &&
                    videoDisabled(instrumentation, connection) &&
                    connection.state.value.error == null
            })
            assertTrue("Decoder reconfigure re-enabled video behind Audio-only", videoDisabled(instrumentation, connection))
            assertTrue(
                "Audio-only decoder change lost playback position",
                authoritativePosition(instrumentation, connection) in 400L..1_250L,
            )

            instrumentation.runOnMainSync { audioController.setAudioOnly(false) }
            assertTrue("Video was not re-enabled", await(5_000L) {
                !audio.state.value.audioOnlyMode && !videoDisabled(instrumentation, connection)
            })
            assertTrue("Restored video did not use the newly requested decoder mode", await(15_000L) {
                activeMode(decoder, targetMode, softwareNames, hardwareNames)
            })
            assertEquals(targetMode, decoder.state.value.requestedMode)
            assertNull("Playback failed while restoring video", connection.state.value.error)
            assertTrue(
                "Restoring video restarted the item from zero",
                authoritativePosition(instrumentation, connection) in 350L..1_300L,
            )
        } finally {
            runCatching {
                instrumentation.runOnMainSync {
                    audioController.setAudioOnly(false)
                    decoder.useGlobalForCurrentMedia(media.stableId)
                    connection.pause()
                    audioController.unbind()
                    connection.disconnect()
                }
            }
            runCatching { scenario.close() }
            context.stopService(Intent(context, PlaybackService::class.java))
            video.delete()
        }
    }

    private fun assertQueueAndPolicies(
        instrumentation: android.app.Instrumentation,
        connection: PlaybackConnection,
        expectedIds: List<String>,
        expectedIndex: Int,
        repeatMode: RepeatMode,
        shuffle: Boolean,
        speed: Float,
        pitch: Float,
    ) {
        assertEquals(expectedIds, queueIds(instrumentation, connection))
        assertEquals(expectedIndex, connection.state.value.currentMediaItemIndex)
        assertEquals(expectedIds.size, connection.state.value.mediaItemCount)
        assertEquals(repeatMode, connection.state.value.repeatMode)
        assertEquals(shuffle, connection.state.value.shuffleEnabled)
        val parameters = onMain(instrumentation) { connection.playerOrNull()?.playbackParameters }
        assertTrue("Playback speed was not preserved", parameters != null && abs(parameters.speed - speed) < 0.01f)
        assertTrue("Pitch was not preserved", parameters != null && abs(parameters.pitch - pitch) < 0.01f)
    }

    private fun assertAudioAndSubtitleCoexistence(
        instrumentation: android.app.Instrumentation,
        connection: PlaybackConnection,
        externalAudioId: String,
        expectedAudioDelayMs: Long,
        expectedRouteDelayMs: Long,
        app: MaxVideoPlayerApplication,
    ) {
        val audio = app.container.audioRepository
        val subtitles = connection.state.value.subtitles
        assertTrue("External subtitle association disappeared", subtitles.externalAttached)
        assertTrue("External subtitle track is no longer selected", subtitles.tracks.any { it.external && it.selected })
        assertTrue("External audio association disappeared", audio.state.value.selectedExternalId == externalAudioId)
        assertTrue("External audio track is no longer selected in Media3", externalAudioSelected(instrumentation, connection))
        assertTrue("Step-5 DSP pipeline is no longer installed", audio.state.value.dspPipelineInstalled)
        assertTrue("Equalizer was disabled by decoder switching", audio.state.value.equalizerEnabled)
        assertEquals(EqualizerPreset.VOCAL, audio.state.value.equalizerPreset)
        assertTrue("DSP boost was lost", audio.realtimeParameters.get().boostDb > 0f)
        assertTrue("DSP preamp was lost", audio.realtimeParameters.get().preampDb > 0f)
        assertEquals(expectedAudioDelayMs, audio.state.value.audioDelayMs)
        assertEquals(expectedRouteDelayMs, audio.state.value.routeCompensationMs)
        assertEquals(expectedAudioDelayMs + expectedRouteDelayMs, audio.state.value.effectiveAudioDelayMs)
        assertEquals(expectedAudioDelayMs + expectedRouteDelayMs, audio.realtimeParameters.get().delayMs)
    }

    private fun activeMode(
        repository: DecoderRepository,
        mode: DecoderMode,
        softwareNames: Set<String>,
        hardwareNames: Set<String>,
    ): Boolean {
        val state = repository.state.value
        val diagnostics = state.diagnostics
        if (state.requestedMode != mode || diagnostics.activeDecoderName == null || !diagnostics.videoDecoderActive) return false
        return when (mode) {
            DecoderMode.SOFTWARE -> diagnostics.effectiveBackend == DecoderBackendType.SOFTWARE &&
                diagnostics.activeDecoderName in softwareNames
            DecoderMode.HARDWARE,
            DecoderMode.ENHANCED_HARDWARE,
            -> diagnostics.effectiveBackend == DecoderBackendType.HARDWARE && diagnostics.activeDecoderName in hardwareNames
            DecoderMode.AUTO -> when (diagnostics.effectiveBackend) {
                DecoderBackendType.SOFTWARE -> diagnostics.activeDecoderName in softwareNames
                DecoderBackendType.HARDWARE -> diagnostics.activeDecoderName in hardwareNames
                DecoderBackendType.UNKNOWN -> diagnostics.activeDecoderName !in softwareNames && diagnostics.activeDecoderName !in hardwareNames
                null -> false
            }
        }
    }

    private fun queueIds(
        instrumentation: android.app.Instrumentation,
        connection: PlaybackConnection,
    ): List<String> = onMain(instrumentation) {
        val player = connection.playerOrNull() ?: return@onMain emptyList()
        List(player.mediaItemCount) { index -> player.getMediaItemAt(index).mediaId }
    }

    private fun authoritativePosition(
        instrumentation: android.app.Instrumentation,
        connection: PlaybackConnection,
    ): Long = onMain(instrumentation) { connection.playerOrNull()?.currentPosition ?: Long.MIN_VALUE }

    private fun videoDisabled(
        instrumentation: android.app.Instrumentation,
        connection: PlaybackConnection,
    ): Boolean = onMain(instrumentation) {
        connection.playerOrNull()?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) == true
    }

    private fun externalAudioSelected(
        instrumentation: android.app.Instrumentation,
        connection: PlaybackConnection,
    ): Boolean = onMain(instrumentation) {
        connection.playerOrNull()?.currentTracks?.groups?.any { group ->
            group.type == C.TRACK_TYPE_AUDIO &&
                isExternalMedia3Group(group) &&
                (0 until group.length).any { index -> group.isTrackSelected(index) }
        } == true
    }

    private fun isExternalMedia3Group(group: androidx.media3.common.Tracks.Group): Boolean {
        if (group.mediaTrackGroup.id.startsWith("1:")) return true
        return (0 until group.length).any { index -> group.getTrackFormat(index).id?.startsWith("1:") == true }
    }

    private fun createExternalPcmWav(directory: File, name: String): File {
        val sampleRate = 48_000
        val channels = 1
        val bitsPerSample = 16
        val seconds = 4
        val bytesPerSample = bitsPerSample / 8
        val dataSize = sampleRate * channels * bytesPerSample * seconds
        return File(directory, name).apply {
            outputStream().buffered().use { output ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(36 + dataSize)
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)
                header.putShort(1)
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
        }
    }

    private fun copyAsset(
        targetContext: android.content.Context,
        testContext: android.content.Context,
        assetName: String,
        targetName: String,
    ): File = File(targetContext.cacheDir, targetName).apply {
        testContext.assets.open(assetName).use { input -> outputStream().use { output -> input.copyTo(output) } }
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

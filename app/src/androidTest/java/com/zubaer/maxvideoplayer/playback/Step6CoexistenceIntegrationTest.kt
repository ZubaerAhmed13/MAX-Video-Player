package com.zubaer.maxvideoplayer.playback

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
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
 * Step-6 cross-step coexistence certification.
 *
 * Uses the real service-owned Media3 graph. There is no mock player, no Assume/skip, and no
 * manual re-selection after a decoder switch. Sidecars must restore through the production
 * persistence/listener path by themselves before the assertion deadline.
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

        val codecPool = avcCodecPool()
        assertTrue("API-35 exposes no classified AVC decoder", codecPool.hasAny)

        val video = copyAsset(context, testContext, "step5_multi_audio.mp4", "step6_coexistence_video.mp4")
        val subtitle = copyAsset(context, testContext, "step5_external.srt", "step6_coexistence_external.srt")
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
            uri = Uri.fromFile(subtitle).toString(),
            displayName = subtitle.name,
            mimeType = MimeTypes.APPLICATION_SUBRIP,
            format = SubtitleFormat.SRT,
        )
        var externalAudioId: String? = null

        resetSharedPlayer(instrumentation, context, connection, audioController)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()

        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })

            instrumentation.runOnMainSync {
                connection.setQueue(queue, startIndex = 1, startPositionMs = 0L, playWhenReady = false)
            }
            assertTrue("A/B/C queue did not become authoritative on B", await(15_000L) {
                connection.state.value.mediaId == mediaB.stableId &&
                    connection.state.value.currentMediaItemIndex == 1 &&
                    connection.state.value.mediaItemCount == 3 &&
                    connection.state.value.error == null &&
                    decoder.state.value.mediaId == mediaB.stableId
            })
            assertEquals(queue.map { it.stableId }, queueIds(instrumentation, connection))

            instrumentation.runOnMainSync { audioController.bind() }
            assertTrue("Embedded audio tracks were not discovered", await(10_000L) {
                audio.state.value.tracks.count { !it.external && it.supported } >= 2
            })

            instrumentation.runOnMainSync { connection.attachExternalSubtitle(subtitleDescriptor) }
            assertTrue("External subtitle did not become selected", await(10_000L) {
                subtitleSelected(connection)
            })

            val descriptor = audio.describe(Uri.fromFile(externalAudio))
            assertTrue("External WAV fixture was not recognized", descriptor != null)
            instrumentation.runOnMainSync { audioController.attachExternal(descriptor!!) }
            assertTrue("External audio association was not stored", await(10_000L) {
                audio.state.value.selectedExternalId != null
            })
            externalAudioId = audio.state.value.selectedExternalId
            assertTrue("External audio did not become selected in Media3", await(15_000L) {
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
            assertTrue("Auto decoder did not settle", await(15_000L) {
                decoderSettled(decoder, DecoderMode.AUTO, codecPool)
            })
            val firstSwitch = if (codecPool.softwareNames.isNotEmpty()) {
                DecoderMode.SOFTWARE
            } else {
                DecoderMode.ENHANCED_HARDWARE
            }
            decoder.requestModeForCurrentMedia(mediaB.stableId, firstSwitch)
            assertTrue("First decoder switch did not settle", await(15_000L) {
                decoderSettled(decoder, firstSwitch, codecPool)
            })

            assertQueuePoliciesAndSidecars(
                instrumentation,
                app,
                connection,
                queue.map { it.stableId },
                expectedIndex = 1,
                expectedRepeat = RepeatMode.ALL,
                expectedShuffle = false,
                expectedSpeed = 1.5f,
                expectedPitch = 1.2f,
                externalAudioId = externalAudioId!!,
            )
            assertTrue(
                "Decoder switch restarted B from zero",
                authoritativePosition(instrumentation, connection) in 450L..1_250L,
            )

            // Rendering, not only association state: the Step-4 side-loaded cue must still render.
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

            // Previous/Next must still navigate the exact original queue after decoder reprepare.
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
            // Per-media decoder override must restore after leaving B and returning.
            assertTrue("B decoder mode was not restored after queue navigation", await(15_000L) {
                decoderSettled(decoder, firstSwitch, codecPool)
            })
            assertSidecarsAndDspEventually(instrumentation, app, connection, externalAudioId!!)

            // Turn shuffle on and perform another decoder switch to prove repeat+shuffle survive.
            instrumentation.runOnMainSync { connection.setShuffleEnabled(true) }
            assertTrue("Shuffle did not enable", await(3_000L) { connection.state.value.shuffleEnabled })
            val secondSwitch = when {
                codecPool.hardwareNames.isNotEmpty() && firstSwitch != DecoderMode.HARDWARE -> DecoderMode.HARDWARE
                codecPool.softwareNames.isNotEmpty() && firstSwitch != DecoderMode.SOFTWARE -> DecoderMode.SOFTWARE
                else -> DecoderMode.AUTO
            }
            decoder.requestModeForCurrentMedia(mediaB.stableId, secondSwitch)
            assertTrue("Second decoder switch did not settle", await(15_000L) {
                decoderSettled(decoder, secondSwitch, codecPool)
            })
            assertQueuePoliciesAndSidecars(
                instrumentation,
                app,
                connection,
                queue.map { it.stableId },
                expectedIndex = 1,
                expectedRepeat = RepeatMode.ALL,
                expectedShuffle = true,
                expectedSpeed = 1.5f,
                expectedPitch = 1.2f,
                externalAudioId = externalAudioId!!,
            )

            // Activity recreation must not replace the service-owned player/session or decoder mode.
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertTrue("Activity recreation lost B/queue state", await(7_000L) {
                connection.state.value.connected &&
                    connection.state.value.mediaId == mediaB.stableId &&
                    connection.state.value.mediaItemCount == 3 &&
                    connection.state.value.error == null
            })
            assertTrue("Selected decoder mode was lost across Activity recreation", await(15_000L) {
                decoderSettled(decoder, secondSwitch, codecPool)
            })
            assertQueuePoliciesAndSidecars(
                instrumentation,
                app,
                connection,
                queue.map { it.stableId },
                expectedIndex = 1,
                expectedRepeat = RepeatMode.ALL,
                expectedShuffle = true,
                expectedSpeed = 1.5f,
                expectedPitch = 1.2f,
                externalAudioId = externalAudioId!!,
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
                    externalAudioId?.let(audioController::removeExternal)
                    connection.clearExternalSubtitle()
                    connection.pause()
                    audioController.unbind()
                    connection.disconnect()
                }
            }
            runCatching { scenario.close() }
            context.stopService(Intent(context, PlaybackService::class.java))
            instrumentation.waitForIdleSync()
            subtitleRepository.clearExternalAttachment(mediaB.stableId)
            video.delete()
            subtitle.delete()
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
        val codecPool = avcCodecPool()
        assertTrue("API-35 exposes no classified AVC decoder", codecPool.hasAny)
        val requestedWhileHidden = if (codecPool.hardwareNames.isNotEmpty()) {
            DecoderMode.HARDWARE
        } else {
            DecoderMode.SOFTWARE
        }

        val video = copyAsset(context, testContext, "step5_multi_audio.mp4", "step6_audio_only_decoder.mp4")
        val media = AppMedia(
            stableId = "step6-audio-only-${System.currentTimeMillis()}",
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

        resetSharedPlayer(instrumentation, context, connection, audioController)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })
            instrumentation.runOnMainSync { connection.load(media, 0L, false) }
            assertTrue("Audio-only fixture did not become authoritative", await(15_000L) {
                connection.state.value.mediaId == media.stableId &&
                    decoder.state.value.mediaId == media.stableId &&
                    connection.state.value.error == null
            })
            instrumentation.runOnMainSync { audioController.bind() }

            decoder.requestModeForCurrentMedia(media.stableId, DecoderMode.AUTO)
            assertTrue("Initial Auto decoder did not settle", await(15_000L) {
                decoderSettled(decoder, DecoderMode.AUTO, codecPool)
            })
            assertTrue("Controller did not become seek-ready after decoder settle", await(15_000L) {
                onMain(instrumentation) {
                    val player = connection.playerOrNull()
                    player?.currentMediaItem?.mediaId == media.stableId &&
                        player.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                        player.playbackState != androidx.media3.common.Player.STATE_IDLE
                }
            })
            instrumentation.runOnMainSync { connection.seekTo(750L) }
            assertTrue("Pre-audio-only seek failed", await(10_000L) {
                authoritativePosition(instrumentation, connection) in 500L..1_100L
            })

            instrumentation.runOnMainSync { audioController.setAudioOnly(true) }
            assertTrue("Audio-only did not disable video", await(5_000L) {
                audio.state.value.audioOnlyMode && videoDisabled(instrumentation, connection)
            })

            decoder.requestModeForCurrentMedia(media.stableId, requestedWhileHidden)
            assertTrue("Decoder request was not retained while video was disabled", await(10_000L) {
                decoder.state.value.requestedMode == requestedWhileHidden &&
                    videoDisabled(instrumentation, connection) &&
                    connection.state.value.error == null
            })
            assertTrue(
                "Decoder request while Audio-only lost position",
                authoritativePosition(instrumentation, connection) in 400L..1_250L,
            )

            instrumentation.runOnMainSync { audioController.setAudioOnly(false) }
            assertTrue("Video was not restored", await(5_000L) {
                !audio.state.value.audioOnlyMode && !videoDisabled(instrumentation, connection)
            })
            assertTrue("Restored video did not use the newly requested decoder", await(15_000L) {
                decoderSettled(decoder, requestedWhileHidden, codecPool)
            })
            assertEquals(requestedWhileHidden, decoder.state.value.requestedMode)
            assertNull("Playback failed while restoring video", connection.state.value.error)
            assertTrue(
                "Restoring video restarted playback from zero",
                authoritativePosition(instrumentation, connection) in 350L..1_300L,
            )
        } finally {
            runCatching {
                instrumentation.runOnMainSync {
                    audioController.setAudioOnly(false)
                    connection.pause()
                    audioController.unbind()
                    connection.disconnect()
                }
            }
            runCatching { scenario.close() }
            context.stopService(Intent(context, PlaybackService::class.java))
            instrumentation.waitForIdleSync()
            video.delete()
        }
    }

    private fun assertQueuePoliciesAndSidecars(
        instrumentation: android.app.Instrumentation,
        app: MaxVideoPlayerApplication,
        connection: PlaybackConnection,
        expectedIds: List<String>,
        expectedIndex: Int,
        expectedRepeat: RepeatMode,
        expectedShuffle: Boolean,
        expectedSpeed: Float,
        expectedPitch: Float,
        externalAudioId: String,
    ) {
        assertEquals(expectedIds, queueIds(instrumentation, connection))
        assertEquals(expectedIndex, connection.state.value.currentMediaItemIndex)
        assertEquals(expectedIds.size, connection.state.value.mediaItemCount)
        assertEquals(expectedRepeat, connection.state.value.repeatMode)
        assertEquals(expectedShuffle, connection.state.value.shuffleEnabled)
        val parameters = onMain(instrumentation) { connection.playerOrNull()?.playbackParameters }
        assertTrue("Playback speed was not preserved", parameters != null && abs(parameters.speed - expectedSpeed) < 0.01f)
        assertTrue("Pitch was not preserved", parameters != null && abs(parameters.pitch - expectedPitch) < 0.01f)
        assertSidecarsAndDspEventually(instrumentation, app, connection, externalAudioId)
    }

    private fun assertSidecarsAndDspEventually(
        instrumentation: android.app.Instrumentation,
        app: MaxVideoPlayerApplication,
        connection: PlaybackConnection,
        externalAudioId: String,
    ) {
        val audio = app.container.audioRepository
        assertTrue("External subtitle was not automatically restored", await(15_000L) {
            subtitleSelected(connection)
        })
        assertTrue("External audio association was lost", await(5_000L) {
            audio.state.value.selectedExternalId == externalAudioId
        })
        assertTrue("External audio track was not automatically re-selected in Media3", await(15_000L) {
            externalAudioSelected(instrumentation, connection)
        })
        assertTrue("Step-5 DSP pipeline is no longer installed", audio.state.value.dspPipelineInstalled)
        assertTrue("Equalizer was disabled by decoder switch", audio.state.value.equalizerEnabled)
        assertEquals(EqualizerPreset.VOCAL, audio.state.value.equalizerPreset)
        assertTrue("Preamp was lost", audio.realtimeParameters.get().preampDb > 0f)
        assertTrue("Boost was lost", audio.realtimeParameters.get().boostDb > 0f)
        assertEquals(250L, audio.state.value.audioDelayMs)
        assertEquals(100L, audio.state.value.routeCompensationMs)
        assertEquals(350L, audio.state.value.effectiveAudioDelayMs)
        assertEquals(350L, audio.realtimeParameters.get().delayMs)
    }

    private fun subtitleSelected(connection: PlaybackConnection): Boolean {
        val subtitles = connection.state.value.subtitles
        return subtitles.externalAttached && subtitles.tracks.any { it.external && it.selected }
    }

    private fun decoderSettled(
        repository: DecoderRepository,
        mode: DecoderMode,
        pool: CodecPool,
    ): Boolean {
        val state = repository.state.value
        val diagnostics = state.diagnostics
        val name = diagnostics.activeDecoderName ?: return false
        if (state.requestedMode != mode || !diagnostics.videoDecoderActive || diagnostics.switching) return false
        return when (mode) {
            DecoderMode.SOFTWARE -> diagnostics.effectiveBackend == DecoderBackendType.SOFTWARE && name in pool.softwareNames
            DecoderMode.HARDWARE,
            DecoderMode.ENHANCED_HARDWARE,
            -> diagnostics.effectiveBackend == DecoderBackendType.HARDWARE && name in pool.hardwareNames
            DecoderMode.AUTO -> when (diagnostics.effectiveBackend) {
                DecoderBackendType.SOFTWARE -> name in pool.softwareNames
                DecoderBackendType.HARDWARE -> name in pool.hardwareNames
                DecoderBackendType.UNKNOWN -> name !in pool.softwareNames && name !in pool.hardwareNames
                null -> false
            }
        }
    }

    private fun avcCodecPool(): CodecPool {
        val infos = MediaCodecSelector.DEFAULT.getDecoderInfos("video/avc", false, false)
        return CodecPool(
            softwareNames = infos.filter { it.softwareOnly }.map { it.name }.toSet(),
            hardwareNames = infos.filter { it.hardwareAccelerated }.map { it.name }.toSet(),
        )
    }

    private fun resetSharedPlayer(
        instrumentation: android.app.Instrumentation,
        context: android.content.Context,
        connection: PlaybackConnection,
        audioController: com.zubaer.maxvideoplayer.feature.audio.AudioPlaybackController,
    ) {
        instrumentation.runOnMainSync {
            audioController.unbind()
            connection.disconnect()
        }
        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()
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
        testContext.assets.open(assetName).use { input ->
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

    private data class CodecPool(
        val softwareNames: Set<String>,
        val hardwareNames: Set<String>,
    ) {
        val hasAny: Boolean get() = softwareNames.isNotEmpty() || hardwareNames.isNotEmpty()
    }
}
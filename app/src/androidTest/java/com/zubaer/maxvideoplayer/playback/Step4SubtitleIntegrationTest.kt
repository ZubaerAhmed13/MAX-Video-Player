package com.zubaer.maxvideoplayer.playback

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleFileDescriptor
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Step4SubtitleIntegrationTest {
    @Test
    fun sideLoadedSrtRendersThroughServiceOwnedPlayerAndCanBeDisabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("subtitle_preferences_v1", 0).edit().clear().commit()

        val videoFile = File(context.cacheDir, "step4_subtitle_video.mp4").apply {
            writeBytes(Base64.decode(SAMPLE_MP4_BASE64, Base64.NO_WRAP))
        }
        val subtitleFile = File(context.cacheDir, "step4_fixture.en.srt").apply {
            writeText(
                """1
00:00:00,100 --> 00:00:01,700
Step 4 subtitle fixture
""".trimIndent(),
            )
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        instrumentation.waitForIdleSync()
        val repository = SubtitleRepository(context)
        val connection = PlaybackConnection(context, repository)
        val media = AppMedia(
            stableId = "step4-subtitle-fixture",
            uri = Uri.fromFile(videoFile).toString(),
            title = "Step 4 subtitle fixture",
            mimeType = "video/mp4",
            durationMs = 2_000L,
            sizeBytes = videoFile.length(),
            width = 160,
            height = 90,
            videoCodec = "h264",
            sourceType = MediaSourceType.SAF,
        )
        val descriptor = SubtitleFileDescriptor(
            uri = Uri.fromFile(subtitleFile).toString(),
            displayName = subtitleFile.name,
            mimeType = MimeTypes.APPLICATION_SUBRIP,
        )

        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })

            instrumentation.runOnMainSync { connection.load(media, playWhenReady = false) }
            assertTrue("Video did not become ready", await(15_000L) {
                connection.state.value.durationMs >= 1_500L || connection.state.value.error != null
            })
            assertNull("Video failed before subtitles were attached", connection.state.value.error)

            instrumentation.runOnMainSync { connection.attachExternalSubtitle(descriptor) }
            assertTrue("External subtitle association was not published", await(10_000L) {
                connection.state.value.subtitles.externalAttached
            })
            assertTrue("Media3 did not expose the side-loaded text track", await(10_000L) {
                connection.state.value.subtitles.tracks.any { it.external }
            })
            assertTrue("External text track was not selected", await(10_000L) {
                connection.state.value.subtitles.tracks.any { it.external && it.selected }
            })

            // Start before the cue boundary so this test verifies actual subtitle rendering
            // without racing a freshly rebuilt side-loaded source against an immediate seek
            // into the middle of its first active cue.
            instrumentation.runOnMainSync {
                connection.seekTo(0L)
                connection.play()
            }
            assertTrue("Side-loaded SRT cue was not rendered by the service-owned player", await(8_000L) {
                var rendered = false
                instrumentation.runOnMainSync {
                    rendered = connection.playerOrNull()?.currentCues?.cues?.any {
                        it.text?.toString()?.contains("Step 4 subtitle fixture") == true
                    } == true
                }
                rendered
            })

            instrumentation.runOnMainSync { connection.setSubtitlesEnabled(false) }
            assertTrue("Subtitle disable state did not propagate", await(5_000L) {
                !connection.state.value.subtitles.enabled
            })

            instrumentation.runOnMainSync { connection.setSubtitlesEnabled(true) }
            assertTrue("Subtitle enable state did not propagate", await(5_000L) {
                connection.state.value.subtitles.enabled
            })
            assertNull("Playback failed during subtitle operations", connection.state.value.error)
        } finally {
            instrumentation.runOnMainSync {
                connection.pause()
                connection.disconnect()
            }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            repository.clearExternalAttachment(media.stableId)
            videoFile.delete()
            subtitleFile.delete()
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

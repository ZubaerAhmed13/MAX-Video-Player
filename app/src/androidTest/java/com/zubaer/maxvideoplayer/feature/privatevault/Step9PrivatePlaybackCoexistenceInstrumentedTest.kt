package com.zubaer.maxvideoplayer.feature.privatevault

import android.content.Intent
import android.media.MediaExtractor
import android.net.Uri
import androidx.media3.common.C
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultContainerFormat
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * Production-path coexistence certification using the existing redistribution-safe Step-5 MP4.
 * The fixture is encrypted by the real vault writer and then loaded through PlaybackConnection ->
 * PlaybackService -> Media3 -> EncryptedVaultDataSource. No second player is created for Step 9.
 */
@RunWith(AndroidJUnit4::class)
class Step9PrivatePlaybackCoexistenceInstrumentedTest {
    @Test
    fun encryptedFixtureUsesExistingServiceDecoderAndEmbeddedAudioPipelineAcrossActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val app = context.applicationContext as MaxVideoPlayerApplication
        val container = app.container
        val connection = container.playbackConnection
        val session = container.privateVaultSession
        val storage = container.privateVaultStorage

        val fixture = File(context.cacheDir, "step9_private_multi_audio.mp4").also { target ->
            testContext.assets.open("step5_multi_audio.mp4").use { input -> target.outputStream().use(input::copyTo) }
        }
        certifyFixtureContainsAvcAndAudio(fixture)

        val vaultId = UUID.randomUUID()
        val master = PrivateVaultCrypto.randomKey()
        val partial = storage.partialFile(vaultId.toString())
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            session.setConfigured(true)
            session.unlock(master)
            FileInputStream(fixture).use { input ->
                PrivateVaultContainerFormat.write(
                    input = input,
                    sourceLength = fixture.length(),
                    destination = partial,
                    vaultId = vaultId,
                    metadata = PrivateMediaMetadata(
                        originalDisplayName = "TOP_SECRET_PRIVATE_MOVIE_839247.mp4",
                        title = "TOP_SECRET_PRIVATE_MOVIE_839247",
                        mimeType = "video/mp4",
                        durationMs = 2_000L,
                        width = 160,
                        height = 90,
                    ),
                    masterSecret = master,
                )
            }
            storage.commitPartial(vaultId.toString())
            val media = AppMedia(
                stableId = "maxvault://$vaultId",
                uri = "maxvault://$vaultId",
                title = "Private media",
                mimeType = "video/mp4",
                durationMs = 2_000L,
                sizeBytes = fixture.length(),
                width = 160,
                height = 90,
                sourceId = "private-vault",
                sourceType = MediaSourceType.PRIVATE,
            )

            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })
            instrumentation.runOnMainSync { connection.load(media, 0L, true) }
            assertTrue("Encrypted private fixture did not initialize: ${connection.state.value.error}", await(15_000L) {
                connection.state.value.error != null || onMain(instrumentation) {
                    connection.playerOrNull()?.currentTracks?.groups?.any { it.type == C.TRACK_TYPE_VIDEO } == true
                }
            })
            assertTrue("Private playback reported an error: ${connection.state.value.error}", connection.state.value.error == null)
            assertTrue("Normal decoder pipeline did not expose the H.264 video track", onMain(instrumentation) {
                connection.playerOrNull()?.currentTracks?.groups?.any { group ->
                    group.type == C.TRACK_TYPE_VIDEO && (0 until group.length).any { group.isTrackSupported(it) }
                } == true
            })
            assertTrue("Embedded audio tracks were lost on the private source", onMain(instrumentation) {
                connection.playerOrNull()?.currentTracks?.groups?.any { group ->
                    group.type == C.TRACK_TYPE_AUDIO && (0 until group.length).any { group.isTrackSupported(it) }
                } == true
            })

            val controllerBefore = onMain(instrumentation) { connection.playerOrNull() }
            assertNotNull(controllerBefore)
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertTrue("Service-owned private playback was lost across Activity recreation", await(5_000L) {
                connection.state.value.connected && onMain(instrumentation) {
                    connection.playerOrNull()?.currentMediaItem?.mediaId == media.stableId
                }
            })
            val controllerAfter = onMain(instrumentation) { connection.playerOrNull() }
            assertSame("Activity recreation created a second playback controller", controllerBefore, controllerAfter)
        } finally {
            instrumentation.runOnMainSync {
                connection.pause()
                connection.clearQueue()
                connection.disconnect()
            }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            session.lock()
            storage.delete(vaultId.toString())
            partial.delete()
            PrivateVaultCrypto.zero(master)
            fixture.delete()
        }
    }

    private fun certifyFixtureContainsAvcAndAudio(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var hasAvc = false
            var hasAudio = false
            repeat(extractor.trackCount) { index ->
                val mime = extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                if (mime == "video/avc") hasAvc = true
                if (mime.startsWith("audio/")) hasAudio = true
            }
            assertTrue("Existing redistribution-safe fixture is not H.264/AVC", hasAvc)
            assertTrue("Existing redistribution-safe fixture has no embedded audio", hasAudio)
        } finally {
            extractor.release()
        }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            Thread.sleep(50L)
        }
        return condition()
    }

    private fun <T> onMain(instrumentation: android.app.Instrumentation, block: () -> T): T {
        val result = java.util.concurrent.atomic.AtomicReference<T>()
        val error = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        instrumentation.runOnMainSync {
            try { result.set(block()) } catch (failure: Throwable) { error.set(failure) }
        }
        error.get()?.let { throw it }
        return result.get()
    }
}

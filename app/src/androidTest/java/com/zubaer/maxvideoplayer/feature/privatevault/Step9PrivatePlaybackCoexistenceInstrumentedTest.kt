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
import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Production-path coexistence certification using the existing redistribution-safe Step-5 MP4.
 * The fixture is imported by the real PrivateVaultRepository and then loaded through
 * PlaybackConnection -> PlaybackService -> Media3 -> EncryptedVaultDataSource. No second player is
 * created for Step 9.
 *
 * A completed private container is valid only after its opaque Room index row is committed. Writing
 * an unindexed `.maxvault` file would correctly make it an orphan eligible for recovery cleanup and
 * would not represent a production-created vault item.
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
        val repository = container.privateVaultRepository
        val storage = container.privateVaultStorage

        // Other certification classes intentionally exercise strict decoder policies. This
        // independent private-source coexistence case starts from the normal production Auto policy.
        container.decoderRepository.resetDecoderPreferences()
        assertEquals(DecoderMode.AUTO, container.decoderRepository.requestedMode())

        val fixture = File(context.cacheDir, "step9_private_multi_audio.mp4").also { target ->
            testContext.assets.open("step5_multi_audio.mp4").use { input -> target.outputStream().use(input::copyTo) }
        }
        certifyFixtureContainsAvcAndAudio(fixture)

        val master = PrivateVaultCrypto.randomKey()
        var vaultId: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            session.setConfigured(true)
            session.unlock(master)

            val imported = runBlocking {
                repository.import(Uri.fromFile(fixture), PrivateImportMode.COPY)
            }
            assertTrue("Production Private Vault import failed: $imported", imported is PrivateImportResult.Success)
            imported as PrivateImportResult.Success
            vaultId = imported.item.vaultId
            assertTrue(
                "Indexed encrypted playback fixture was not committed",
                storage.containerFile(imported.item.vaultId).isFile,
            )
            val media = repository.toAppMedia(imported.item)
            assertTrue("Private playback media must use an opaque maxvault URI", media.uri.startsWith("maxvault://"))
            assertEquals("Private media", media.title)

            scenario = ActivityScenario.launch(MainActivity::class.java)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })
            instrumentation.runOnMainSync { connection.load(media, 0L, true) }
            assertTrue("Encrypted private fixture did not initialize: ${connection.state.value.error}", await(15_000L) {
                connection.state.value.error != null || onMain(instrumentation) {
                    connection.playerOrNull()?.currentTracks?.groups?.any { it.type == C.TRACK_TYPE_VIDEO } == true
                }
            })
            assertTrue("Private playback reported an error: ${connection.state.value.error}", connection.state.value.error == null)
            assertTrue("Normal decoder pipeline did not expose a supported H.264 video track", onMain(instrumentation) {
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
            requireNotNull(scenario).recreate()
            instrumentation.waitForIdleSync()
            assertTrue("Service-owned private playback was lost across Activity recreation", await(5_000L) {
                connection.state.value.connected && onMain(instrumentation) {
                    connection.playerOrNull()?.currentMediaItem?.mediaId == media.stableId
                }
            })
            val controllerAfter = onMain(instrumentation) { connection.playerOrNull() }
            assertSame("Activity recreation created a second playback controller", controllerBefore, controllerAfter)
        } finally {
            runCatching {
                instrumentation.runOnMainSync {
                    connection.pause()
                    connection.disconnect()
                }
            }
            runCatching { scenario?.close() }
            context.stopService(Intent(context, PlaybackService::class.java))
            instrumentation.waitForIdleSync()
            vaultId?.let { id -> runCatching { runBlocking { repository.delete(id) } } }
            session.lock()
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

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
import com.zubaer.maxvideoplayer.feature.privatevault.auth.VaultAuthResult
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
 * The vault itself is created through PrivateVaultAuthenticator before import. This deliberately
 * exercises the persisted authenticated master-key envelope that MainActivity/AppLockController
 * later observes instead of manually injecting an in-memory key that could be invalidated when the
 * authenticator is lazily initialized.
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
        val authenticator = container.privateVaultAuthenticator
        val repository = container.privateVaultRepository
        val storage = container.privateVaultStorage

        // Other certification classes intentionally exercise strict decoder/app-lock policies. This
        // independent private-source coexistence case starts from normal production defaults.
        container.decoderRepository.resetDecoderPreferences()
        container.settingsRepository.setAppLockEnabled(false)
        assertEquals(DecoderMode.AUTO, container.decoderRepository.requestedMode())

        val fixture = File(context.cacheDir, "step9_private_multi_audio.mp4").also { target ->
            testContext.assets.open("step5_multi_audio.mp4").use { input -> target.outputStream().use(input::copyTo) }
        }
        certifyFixtureContainsAvcAndAudio(fixture)

        var vaultId: String? = null
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            // The strict named-class certification runs after the complete connected suite on the
            // same installed test app. Clear only the ephemeral test credential envelope, then create
            // a fresh production-valid envelope through the real authenticator so later lazy app-lock
            // access cannot overwrite a manually injected session secret.
            authenticator.clearAuthenticationAfterVaultErase()
            val created = authenticator.createVault("947261".toCharArray())
            assertTrue("Could not create authenticated certification vault: $created", created is VaultAuthResult.Success)
            assertTrue("Authenticated certification vault did not unlock", session.state.value == PrivateVaultState.UNLOCKED)

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
            assertTrue("Activity launch unexpectedly relocked the authenticated vault", session.state.value == PrivateVaultState.UNLOCKED)
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })
            instrumentation.runOnMainSync { connection.load(media, 0L, true) }

            // The retained full suite intentionally exercises unavailable sources before this class.
            // A delayed callback from that old item must never be mistaken for a failure of the new
            // private item. Wait until the MediaSession has transitioned to this exact media ID, then
            // accept either its own error or initialized video tracks as the decisive result.
            assertTrue("Encrypted private fixture did not initialize: ${connection.state.value.error}", await(15_000L) {
                val currentState = connection.state.value
                val currentPrivateItem = currentState.mediaId == media.stableId
                currentPrivateItem && (
                    currentState.error != null || onMain(instrumentation) {
                        connection.playerOrNull()?.currentMediaItem?.mediaId == media.stableId &&
                            connection.playerOrNull()?.currentTracks?.groups?.any { it.type == C.TRACK_TYPE_VIDEO } == true
                    }
                )
            })
            assertEquals("PlaybackConnection did not transition to the private item", media.stableId, connection.state.value.mediaId)
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
            // Stop the service while the Activity is still foregrounded so the vault session remains
            // unlocked long enough to delete this indexed fixture through the production repository.
            context.stopService(Intent(context, PlaybackService::class.java))
            instrumentation.waitForIdleSync()
            vaultId?.let { id -> runCatching { runBlocking { repository.delete(id) } } }
            authenticator.clearAuthenticationAfterVaultErase()
            runCatching { scenario?.close() }
            instrumentation.waitForIdleSync()
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

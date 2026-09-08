package com.zubaer.maxvideoplayer.playback

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.MainActivity
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.network.model.NetworkConnectionState
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntryType
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkDataSourceRouter
import com.zubaer.maxvideoplayer.feature.network.protocol.ftp.FtpProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.smb.SmbProtocolClient
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.playback.session.PlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs only in the dedicated CI lane, which provisions isolated Samba, FTP and RTSP servers on
 * the runner host. The ordinary connected suite remains self-contained for local development.
 */
@RunWith(AndroidJUnit4::class)
class Step7ProtocolServerIntegrationTest {
    @OptIn(UnstableApi::class)
    @Test
    fun realSmbFtpAndRtspServersBrowseSeekAndPlayThroughProductionMedia3() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("step7ProtocolServers") != "true") return

        val context = instrumentation.targetContext
        val app = context.applicationContext as MaxVideoPlayerApplication
        val fixture = instrumentation.context.assets.open("step5_multi_audio.mp4").use { it.readBytes() }
        val host = arguments.getString("step7ServerHost") ?: "10.0.2.2"
        val username = "maxci"
        val password = "max-network-ci"
        val credential = NetworkCredential(username = username, password = password)

        val smbLocation = NetworkLocation(
            id = "step7-ci-smb",
            displayName = "Step 7 Samba",
            protocol = NetworkProtocol.SMB,
            host = host,
            port = 1445,
            basePath = "media",
        )
        val ftpLocation = NetworkLocation(
            id = "step7-ci-ftp",
            displayName = "Step 7 FTP",
            protocol = NetworkProtocol.FTP,
            host = host,
            port = 2121,
            ftpSecurityAcknowledged = true,
        )

        certifySmb(app, smbLocation, credential, fixture)
        certifyFtp(app, ftpLocation, credential, fixture)

        val smbMedia = mediaFor(
            id = "step7-ci-smb-media",
            uri = SmbProtocolClient().playbackUri(smbLocation, FIXTURE_NAME),
            sourceId = smbLocation.id,
        )
        val ftpMedia = mediaFor(
            id = "step7-ci-ftp-media",
            uri = FtpProtocolClient().playbackUri(ftpLocation, FIXTURE_NAME),
            sourceId = ftpLocation.id,
        )
        app.container.networkRequestRegistry.registerUri(smbMedia.uri, smbLocation, credential)
        app.container.networkRequestRegistry.registerUri(ftpMedia.uri, ftpLocation, credential)
        val rtspMedia = app.container.networkRepository.prepareDirect("rtsp://$host:8554/step7", "Step 7 RTSP")

        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val connection = PlaybackConnection(context)
        try {
            instrumentation.runOnMainSync { connection.connect() }
            assertTrue("MediaController did not connect", await(10_000L) { connection.state.value.connected })
            certifyPlayback(instrumentation, connection, smbMedia, NetworkProtocol.SMB)
            certifyPlayback(instrumentation, connection, ftpMedia, NetworkProtocol.FTP)
            certifyPlayback(instrumentation, connection, rtspMedia, NetworkProtocol.RTSP, timeoutMs = 35_000L)
        } finally {
            instrumentation.runOnMainSync {
                connection.pause()
                connection.playerOrNull()?.stop()
                connection.disconnect()
            }
            scenario.close()
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }

    @OptIn(UnstableApi::class)
    private fun certifySmb(
        app: MaxVideoPlayerApplication,
        location: NetworkLocation,
        credential: NetworkCredential,
        fixture: ByteArray,
    ) {
        val client = SmbProtocolClient()
        val entries = runBlocking { client.list(location, credential, "") }
        assertTrue(entries.any { it.name == FIXTURE_NAME && it.type == NetworkEntryType.VIDEO })
        assertTrue(entries.any { it.name == UNICODE_DIRECTORY && it.isDirectory })
        assertFalse(runBlocking { client.testConnection(location, credential.copy(password = "wrong")) }.connected)

        val uri = client.playbackUri(location, FIXTURE_NAME)
        app.container.networkRequestRegistry.registerUri(uri, location, credential)
        certifyRandomRead(app, uri, fixture)
        certifyLargeOffset(app, client.playbackUri(location, LARGE_FILE), location, credential)
    }

    @OptIn(UnstableApi::class)
    private fun certifyFtp(
        app: MaxVideoPlayerApplication,
        location: NetworkLocation,
        credential: NetworkCredential,
        fixture: ByteArray,
    ) {
        val client = FtpProtocolClient()
        val entries = runBlocking { client.list(location, credential, "") }
        assertTrue(entries.any { it.name == FIXTURE_NAME && it.type == NetworkEntryType.VIDEO })
        assertTrue(entries.any { it.name == UNICODE_DIRECTORY && it.isDirectory })
        assertFalse(runBlocking { client.testConnection(location, credential.copy(password = "wrong")) }.connected)

        val uri = client.playbackUri(location, FIXTURE_NAME)
        app.container.networkRequestRegistry.registerUri(uri, location, credential)
        certifyRandomRead(app, uri, fixture)
        certifyLargeOffset(app, client.playbackUri(location, LARGE_FILE), location, credential)
    }

    @OptIn(UnstableApi::class)
    private fun certifyRandomRead(app: MaxVideoPlayerApplication, uri: String, expected: ByteArray) {
        val source = NetworkDataSourceRouter.Factory(app, app.container.networkRequestRegistry).createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(uri).setPosition(RANDOM_READ_OFFSET.toLong()).setLength(RANDOM_READ_LENGTH.toLong()).build())
            val actual = ByteArray(RANDOM_READ_LENGTH)
            var total = 0
            while (total < actual.size) {
                val count = source.read(actual, total, actual.size - total)
                if (count < 0) break
                total += count
            }
            assertEquals(RANDOM_READ_LENGTH, total)
            assertArrayEquals(expected.copyOfRange(RANDOM_READ_OFFSET, RANDOM_READ_OFFSET + RANDOM_READ_LENGTH), actual)
        } finally {
            source.close()
        }
    }

    @OptIn(UnstableApi::class)
    private fun certifyLargeOffset(
        app: MaxVideoPlayerApplication,
        uri: String,
        location: NetworkLocation,
        credential: NetworkCredential,
    ) {
        app.container.networkRequestRegistry.registerUri(uri, location, credential)
        val source = NetworkDataSourceRouter.Factory(app, app.container.networkRequestRegistry).createDataSource()
        try {
            assertEquals(1L, source.open(DataSpec.Builder().setUri(uri).setPosition(LARGE_OFFSET).setLength(1L).build()))
            val byte = ByteArray(1)
            assertEquals(1, source.read(byte, 0, 1))
            assertEquals(0x5A.toByte(), byte[0])
        } finally {
            source.close()
        }
    }

    private fun certifyPlayback(
        instrumentation: android.app.Instrumentation,
        connection: PlaybackConnection,
        media: AppMedia,
        protocol: NetworkProtocol,
        timeoutMs: Long = 20_000L,
    ) {
        val shouldPlay = protocol == NetworkProtocol.RTSP
        instrumentation.runOnMainSync { connection.load(media, playWhenReady = shouldPlay) }
        assertTrue("$protocol did not reach Media3 READY/error state", await(timeoutMs) {
            val snapshot = playerSnapshotOnMain(instrumentation, connection)
            (snapshot.mediaId == media.stableId && snapshot.playbackState == Player.STATE_READY) ||
                connection.state.value.error != null
        })
        assertNull("$protocol playback failed: ${connection.state.value.error}", connection.state.value.error)
        assertEquals(media.stableId, playerSnapshotOnMain(instrumentation, connection).mediaId)
        if (shouldPlay) {
            assertTrue("RTSP reached READY but did not play through the service-owned player", await(5_000L) {
                connection.state.value.isPlaying
            })
        }
        assertEquals(protocol, connection.state.value.network.protocol)
        assertEquals(NetworkConnectionState.CONNECTED, connection.state.value.network.connectionState)
    }

    private fun playerSnapshotOnMain(
        instrumentation: android.app.Instrumentation,
        connection: PlaybackConnection,
    ): PlayerSnapshot {
        var snapshot = PlayerSnapshot(Player.STATE_IDLE, null)
        instrumentation.runOnMainSync {
            val player = connection.playerOrNull()
            snapshot = PlayerSnapshot(
                playbackState = player?.playbackState ?: Player.STATE_IDLE,
                mediaId = player?.currentMediaItem?.mediaId,
            )
        }
        return snapshot
    }

    private data class PlayerSnapshot(val playbackState: Int, val mediaId: String?)

    private fun mediaFor(id: String, uri: String, sourceId: String) = AppMedia(
        stableId = id,
        uri = uri,
        title = id,
        mimeType = "video/mp4",
        sourceId = sourceId,
        sourceType = MediaSourceType.NETWORK,
    )

    private fun await(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50L)
        }
        return condition()
    }

    private companion object {
        const val FIXTURE_NAME = "step5_multi_audio.mp4"
        const val LARGE_FILE = "sparse-large.bin"
        const val UNICODE_DIRECTORY = "বাংলা-日本語"
        const val RANDOM_READ_OFFSET = 512
        const val RANDOM_READ_LENGTH = 2_048
        const val LARGE_OFFSET = 3_221_225_472L
    }
}

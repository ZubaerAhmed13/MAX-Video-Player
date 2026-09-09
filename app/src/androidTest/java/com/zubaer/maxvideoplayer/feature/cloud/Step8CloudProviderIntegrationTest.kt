package com.zubaer.maxvideoplayer.feature.cloud

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFileIdentity
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudProvider
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudDataSource
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudPlaybackRegistry
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudUriCodec
import com.zubaer.maxvideoplayer.feature.cloud.provider.CloudAccessTokenProvider
import com.zubaer.maxvideoplayer.feature.cloud.provider.DropboxClient
import com.zubaer.maxvideoplayer.feature.cloud.provider.GoogleDriveClient
import com.zubaer.maxvideoplayer.feature.cloud.provider.OneDriveClient
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deterministic contract certification for the real Step-8 provider clients. Only provider hosts
 * are substituted with MockWebServer; OAuth headers, JSON parsing, stable identity, CloudDataSource
 * resolution and Media3 byte-range requests all use production code.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class Step8CloudProviderIntegrationTest {
    @Test
    fun googleDrive_browse_auth_and_range_above_2GiB_use_production_adapter() = runBlocking {
        val server = MockWebServer()
        server.start()
        val token = RecordingTokenProvider()
        val client = GoogleDriveClient(
            accountId = "google-account",
            tokenProvider = token,
            apiBase = server.url("/drive/v3").toString(),
        )
        try {
            server.enqueue(jsonResponse("""
                {"nextPageToken":"google-next","files":[
                  {"id":"g1","name":"Movie.mp4","mimeType":"video/mp4","size":"3221225472","modifiedTime":"2026-09-08T10:00:00Z","version":"7","capabilities":{"canDownload":true}}
                ]}
            """))
            val page = client.browse(pageSize = 25)
            assertEquals("google-next", page.nextPageToken)
            assertEquals("g1", page.entries.single().identity.providerFileId)
            val browseRequest = server.takeRequest()
            assertEquals("Bearer test-access-token", browseRequest.headers["Authorization"])
            assertTrue(browseRequest.url.encodedPath.endsWith("/drive/v3/files"))

            server.enqueue(jsonResponse("""
                {"id":"g1","name":"Movie.mp4","mimeType":"video/mp4","size":"3221225472","modifiedTime":"2026-09-08T10:00:00Z","version":"7","capabilities":{"canDownload":true}}
            """))
            server.enqueue(rangeResponse())
            certifyLargeRange(
                provider = CloudProvider.GOOGLE_DRIVE,
                accountId = "google-account",
                identity = page.entries.single().identity,
                revision = "7",
                client = client,
            )
            val metadata = server.takeRequest()
            val range = server.takeRequest()
            assertTrue(metadata.url.encodedPath.endsWith("/drive/v3/files/g1"))
            assertEquals(EXPECTED_RANGE, range.headers["Range"])
            assertEquals("Bearer test-access-token", range.headers["Authorization"])
        } finally {
            server.close()
        }
    }

    @Test
    fun oneDrive_browse_auth_and_range_above_2GiB_use_production_adapter() = runBlocking {
        val server = MockWebServer()
        server.start()
        val token = RecordingTokenProvider()
        val client = OneDriveClient(
            accountId = "onedrive-account",
            tokenProvider = token,
            graphBase = server.url("/v1.0").toString(),
        )
        try {
            server.enqueue(jsonResponse("""
                {"@odata.nextLink":"${server.url("/v1.0/next")}","value":[
                  {"id":"o1","name":"Movie.mp4","size":3221225472,"file":{"mimeType":"video/mp4"},"parentReference":{"id":"root","driveId":"driveA"},"lastModifiedDateTime":"2026-09-08T10:00:00Z","eTag":"etag-1"}
                ]}
            """))
            val page = client.browse(pageSize = 25)
            assertNotNull(page.nextPageToken)
            assertEquals("driveA", page.entries.single().identity.driveId)
            val browseRequest = server.takeRequest()
            assertEquals("Bearer test-access-token", browseRequest.headers["Authorization"])
            assertTrue(browseRequest.url.encodedPath.endsWith("/v1.0/me/drive/root/children"))

            server.enqueue(jsonResponse("""
                {"id":"o1","name":"Movie.mp4","size":3221225472,"file":{"mimeType":"video/mp4"},"parentReference":{"id":"root","driveId":"driveA"},"lastModifiedDateTime":"2026-09-08T10:00:00Z","eTag":"etag-1"}
            """))
            server.enqueue(jsonResponse("""
                {"id":"o1","size":3221225472,"file":{"mimeType":"video/mp4"},"eTag":"etag-1","@microsoft.graph.downloadUrl":"${server.url("/download/onedrive")}"}
            """))
            server.enqueue(rangeResponse())
            certifyLargeRange(
                provider = CloudProvider.ONEDRIVE,
                accountId = "onedrive-account",
                identity = page.entries.single().identity,
                revision = "etag-1",
                client = client,
            )
            val metadata = server.takeRequest()
            val resolve = server.takeRequest()
            val range = server.takeRequest()
            assertEquals("Bearer test-access-token", metadata.headers["Authorization"])
            assertEquals("Bearer test-access-token", resolve.headers["Authorization"])
            assertEquals(EXPECTED_RANGE, range.headers["Range"])
            // OneDrive's temporary download URL is intentionally credential-free.
            assertEquals(null, range.headers["Authorization"])
        } finally {
            server.close()
        }
    }

    @Test
    fun dropbox_browse_auth_and_range_above_2GiB_use_production_adapter() = runBlocking {
        val server = MockWebServer()
        server.start()
        val token = RecordingTokenProvider()
        val client = DropboxClient(
            accountId = "dropbox-account",
            tokenProvider = token,
            apiBase = server.url("/dropbox-api").toString(),
            contentBase = server.url("/dropbox-content").toString(),
        )
        try {
            server.enqueue(jsonResponse("""
                {"entries":[
                  {".tag":"file","id":"id:d1","name":"Movie.mp4","size":3221225472,"server_modified":"2026-09-08T10:00:00Z","rev":"r1","path_display":"/Movie.mp4"}
                ],"has_more":true,"cursor":"dropbox-next"}
            """))
            val page = client.browse(pageSize = 25)
            assertEquals("dropbox-next", page.nextPageToken)
            assertEquals("id:d1", page.entries.single().identity.providerFileId)
            val browseRequest = server.takeRequest()
            assertEquals("Bearer test-access-token", browseRequest.headers["Authorization"])
            assertTrue(browseRequest.url.encodedPath.endsWith("/dropbox-api/2/files/list_folder"))

            server.enqueue(jsonResponse("""
                {".tag":"file","id":"id:d1","name":"Movie.mp4","size":3221225472,"server_modified":"2026-09-08T10:00:00Z","rev":"r1","path_display":"/Movie.mp4"}
            """))
            server.enqueue(rangeResponse())
            certifyLargeRange(
                provider = CloudProvider.DROPBOX,
                accountId = "dropbox-account",
                identity = page.entries.single().identity,
                revision = "r1",
                client = client,
            )
            val metadata = server.takeRequest()
            val range = server.takeRequest()
            assertEquals("Bearer test-access-token", metadata.headers["Authorization"])
            assertEquals(EXPECTED_RANGE, range.headers["Range"])
            assertEquals("Bearer test-access-token", range.headers["Authorization"])
            assertTrue(range.headers["Dropbox-API-Arg"].orEmpty().contains("id:d1"))
        } finally {
            server.close()
        }
    }

    private fun certifyLargeRange(
        provider: CloudProvider,
        accountId: String,
        identity: CloudFileIdentity,
        revision: String,
        client: com.zubaer.maxvideoplayer.feature.cloud.provider.CloudProviderClient,
    ) {
        val registry = CloudPlaybackRegistry().apply { register(provider, accountId, client) }
        val source = CloudDataSource(registry)
        try {
            val opened = source.open(
                DataSpec.Builder()
                    .setUri(CloudUriCodec.encode(identity, revision))
                    .setPosition(LARGE_OFFSET)
                    .setLength(RANGE_LENGTH)
                    .build(),
            )
            assertEquals(RANGE_LENGTH, opened)
            val bytes = ByteArray(RANGE_LENGTH.toInt())
            var total = 0
            while (total < bytes.size) {
                val read = source.read(bytes, total, bytes.size - total)
                if (read < 0) break
                total += read
            }
            assertEquals(bytes.size, total)
            assertTrue(bytes.all { it == 0x5a.toByte() })
        } finally {
            source.close()
        }
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/json")
        .body(Buffer().writeUtf8(body.trimIndent()))
        .build()

    private fun rangeResponse(): MockResponse = MockResponse.Builder()
        .code(206)
        .setHeader("Content-Type", "video/mp4")
        .setHeader("Accept-Ranges", "bytes")
        .setHeader("Content-Length", RANGE_LENGTH)
        .setHeader("Content-Range", "bytes $LARGE_OFFSET-${LARGE_OFFSET + RANGE_LENGTH - 1}/3221225472")
        .body(Buffer().write(ByteArray(RANGE_LENGTH.toInt()) { 0x5a.toByte() }))
        .build()

    private class RecordingTokenProvider : CloudAccessTokenProvider {
        val calls = mutableListOf<Boolean>()
        override suspend fun accessToken(forceRefresh: Boolean): String {
            calls += forceRefresh
            return "test-access-token"
        }
    }

    private companion object {
        const val LARGE_OFFSET = 2_147_483_648L
        const val RANGE_LENGTH = 32L
        const val EXPECTED_RANGE = "bytes=2147483648-2147483679"
    }
}

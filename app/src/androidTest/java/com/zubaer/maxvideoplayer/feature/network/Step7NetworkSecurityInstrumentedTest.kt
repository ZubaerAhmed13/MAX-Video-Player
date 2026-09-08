package com.zubaer.maxvideoplayer.feature.network

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkPlaylistParser
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkRequestRegistry
import com.zubaer.maxvideoplayer.feature.network.presentation.NetworkLocationDraft
import com.zubaer.maxvideoplayer.feature.network.repository.NetworkLocationRepository
import com.zubaer.maxvideoplayer.feature.network.protocol.http.NetworkHttpClientFactory
import com.zubaer.maxvideoplayer.feature.network.protocol.webdav.SecureWebDavParser
import com.zubaer.maxvideoplayer.feature.network.protocol.webdav.WebDavProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.webdav.WEB_DAV_MAX_XML_BYTES
import com.zubaer.maxvideoplayer.feature.network.protocol.webdav.readBoundedWebDavBody
import com.zubaer.maxvideoplayer.feature.network.security.CredentialVault
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import com.zubaer.maxvideoplayer.core.database.MaxDatabase

@RunWith(AndroidJUnit4::class)
class Step7NetworkSecurityInstrumentedTest {
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun closeServers() {
        servers.forEach { runCatching { it.close() } }
        servers.clear()
    }

    @Test
    fun signedTokensAreRedactedAndDoNotChangeStableCanonicalIdentity() {
        val first = "https://Media.Example.test/video/Feature.mkv?quality=4k&token=first&X-Amz-Signature=secret-one"
        val refreshed = "HTTPS://media.example.test/video/Feature.mkv?X-Amz-Signature=secret-two&token=second&quality=4k"

        val sanitized = NetworkUriPolicy.sanitize(first)
        assertFalse(sanitized.contains("first"))
        assertFalse(sanitized.contains("secret-one"))
        assertTrue(sanitized.contains("quality=4k"))
        assertEquals(NetworkUriPolicy.canonicalIdentity(first), NetworkUriPolicy.canonicalIdentity(refreshed))
    }

    @Test
    fun boundedM3uPlaylistResolvesRelativeAndMixedNetworkItemsWithoutRecursion() {
        val playlist = """
            #EXTM3U
            #EXTINF:10,Relative video
            clips/video%201.mp4
            #EXTINF:-1,Camera
            rtsp://camera.example.test/live
            file:///must-not-open
            https://cdn.example.test/audio.m4a
        """.trimIndent()
        val items = NetworkPlaylistParser.parse(playlist, "https://media.example.test/lists/watch.m3u", maximumItems = 2)

        assertEquals(2, items.size)
        assertEquals("https://media.example.test/lists/clips/video%201.mp4", items[0].uri)
        assertEquals("Relative video", items[0].title)
        assertEquals("rtsp://camera.example.test/live", items[1].uri)
    }

    @Test
    fun traversalCannotEscapeSavedSourceRoot() {
        assertThrows(IllegalArgumentException::class.java) { NetworkUriPolicy.safeRemotePath("../../secret/video.mkv") }
        assertEquals("folder/video.mkv", NetworkUriPolicy.safeRemotePath("folder/./sub/../video.mkv"))
        assertThrows(IllegalArgumentException::class.java) {
            NetworkUriPolicy.normalizeForPlayback("https://user:password@media.example.test/private.mp4")
        }
    }

    @Test
    fun webDavPropfindUsesAuthenticationAndRejectsEntriesOutsideTheSavedRoot() {
        val server = server()
        val safeBody = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/media/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response><d:response><d:href>/dav/media/%E6%97%A5%E6%9C%AC%E8%AA%9E%20video.mp4</d:href><d:propstat><d:prop><d:getcontentlength>9941</d:getcontentlength><d:getcontenttype>video/mp4</d:getcontenttype></d:prop></d:propstat></d:response></d:multistatus>"""
        server.enqueue(MockResponse.Builder().code(207).setHeader("Content-Type", "application/xml").body(safeBody).build())
        val rewritingClient = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request.newBuilder().url(request.url.newBuilder().scheme("http").build()).build())
        }.build()
        val location = NetworkLocation(
            displayName = "WebDAV",
            protocol = NetworkProtocol.WEBDAV,
            host = "localhost",
            port = server.port,
            basePath = "dav/media",
        )
        val client = WebDavProtocolClient(rewritingClient)
        val entries = kotlinx.coroutines.runBlocking {
            client.list(location, NetworkCredential(username = "dav-user", password = "dav-password"), "")
        }
        assertEquals(listOf("日本語 video.mp4"), entries.map { java.net.URLDecoder.decode(it.name, Charsets.UTF_8.name()) })
        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.headers["Depth"])
        assertTrue(request.headers["Authorization"].orEmpty().startsWith("Basic "))

        val escapingBody = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/media/</d:href></d:response><d:response><d:href>/outside/secret.mp4</d:href></d:response></d:multistatus>"""
        server.enqueue(MockResponse.Builder().code(207).body(escapingBody).build())
        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking { client.list(location, null, "") }
        }
    }

    @Test
    fun savedHttpAndWebDavHttpBuildAndUseCleartextUrisOnlyAfterConsent() {
        val server = server()
        server.enqueue(MockResponse.Builder().code(207).body(
            """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/</d:href></d:response><d:response><d:href>/dav/movie.mp4</d:href></d:response></d:multistatus>""",
        ).build())
        val webDavLocation = NetworkLocationDraft(
            displayName = "LAN WebDAV",
            protocol = NetworkProtocol.WEBDAV_HTTP,
            host = "localhost",
            port = server.port.toString(),
            basePath = "dav",
            cleartextSecurityAcknowledged = true,
        ).location()
        val entries = kotlinx.coroutines.runBlocking { WebDavProtocolClient(okhttp3.OkHttpClient()).list(webDavLocation, null, "") }
        assertEquals("http://localhost:${server.port}/dav/movie.mp4", entries.single().uri)

        val httpLocation = NetworkLocationDraft(
            displayName = "LAN HTTP",
            protocol = NetworkProtocol.HTTP,
            host = "media.lan",
            port = "8080",
            basePath = "movie.mp4",
            cleartextSecurityAcknowledged = true,
        ).location()
        assertEquals(
            "http://media.lan:8080/movie.mp4",
            com.zubaer.maxvideoplayer.feature.network.protocol.http.HttpProtocolClient(okhttp3.OkHttpClient()).playbackUri(httpLocation, ""),
        )
        assertThrows(IllegalStateException::class.java) {
            NetworkLocationDraft(
                displayName = "Unsafe",
                protocol = NetworkProtocol.HTTP,
                host = "media.lan",
                port = "80",
            ).location()
        }
    }

    @Test
    fun savedHttpLocationAndCredentialRoundTripThroughRoomAndTheKeystoreVault() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MaxDatabase::class.java).allowMainThreadQueries().build()
        val vault = CredentialVault(context)
        val repository = NetworkLocationRepository(database.networkLocationDao(), vault)
        var credentialRef: String? = null
        try {
            val location = NetworkLocationDraft(
                id = "saved-http-test",
                displayName = "Saved HTTP",
                protocol = NetworkProtocol.HTTP,
                host = "media.lan",
                port = "8080",
                basePath = "library/movie.mp4",
                username = "alice",
                password = "saved-http-password",
                cleartextSecurityAcknowledged = true,
            ).location()
            val saved = kotlinx.coroutines.runBlocking {
                repository.save(location, NetworkCredential(username = "alice", password = "saved-http-password"), true)
            }
            credentialRef = saved.credentialRef
            val reloaded = kotlinx.coroutines.runBlocking { repository.all().single() }
            assertEquals(NetworkProtocol.HTTP, reloaded.protocol)
            assertTrue(reloaded.cleartextSecurityAcknowledged)
            assertEquals("saved-http-password", repository.credential(reloaded)?.password)
        } finally {
            credentialRef?.let(vault::delete)
            database.close()
        }
    }

    @Test
    fun webDavBodyBoundRejectsDeclaredAndStreamingOverflowBeforeOversizedAllocation() {
        var declaredBodyRead = false
        val declaredOversize = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = WEB_DAV_MAX_XML_BYTES.toLong() + 1L
            override fun source(): BufferedSource {
                declaredBodyRead = true
                return Buffer()
            }
        }
        assertThrows(Exception::class.java) { readBoundedWebDavBody(declaredOversize) }
        assertFalse(declaredBodyRead)

        val streamedOversize = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = -1L
            override fun source(): BufferedSource = Buffer().write(ByteArray(WEB_DAV_MAX_XML_BYTES + 1))
        }
        assertThrows(Exception::class.java) { readBoundedWebDavBody(streamedOversize) }
    }

    @Test
    fun rtspCredentialsAreProcessLocalAndCanonicalMediaUriStaysSecretFree() {
        val url = "rtsp://camera.example.test:8554/live"
        val registry = NetworkRequestRegistry().apply {
            registerUri(url, null, NetworkCredential(username = "camera user", password = "p@ss:word"))
        }
        val authenticated = registry.authenticatedRtspUri(url)
        assertEquals("camera user:p@ss:word", authenticated.userInfo)
        assertEquals("camera.example.test", authenticated.host)
        assertEquals(url, NetworkUriPolicy.persistenceSafeUri(url))
        assertFalse(url.contains("camera user"))
        assertFalse(NetworkUriPolicy.sanitize(authenticated.toString()).contains("p@ss:word"))
    }

    @Test
    fun authorizationIsScopedToTheExactOriginAndDirectoryBoundary() {
        val registry = NetworkRequestRegistry()
        val root = "https://media.example.test/library/a/master.m3u8"
        registry.registerUri(root, null, NetworkCredential(username = "alice", password = "secret"))

        assertTrue(registry.resolveHttp("https://media.example.test/library/a/segment-1.ts")?.requestHeaders()?.containsKey("Authorization") == true)
        assertNull(registry.resolveHttp("https://media.example.test/library/another/segment.ts"))
        assertNull(registry.resolveHttp("https://cdn.example.test/library/a/segment.ts"))
        assertFalse(registry.safeHeaders(root).values.any { it.contains("secret") })
    }

    @Test
    fun webDavParserRejectsExternalEntitiesAndParsesUnicodeWithoutMutation() {
        val malicious = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY leak SYSTEM="file:///etc/passwd">]><d:multistatus xmlns:d="DAV:"><d:response><d:href>&leak;</d:href></d:response></d:multistatus>"""
        assertThrows(Exception::class.java) { SecureWebDavParser.parse(malicious.toByteArray()) }

        val safe = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/ভিডিও/日本語%20video.mkv</d:href><d:propstat><d:prop><d:getcontentlength>4294967297</d:getcontentlength><d:getcontenttype>video/x-matroska</d:getcontenttype></d:prop></d:propstat></d:response></d:multistatus>"""
        val resource = SecureWebDavParser.parse(safe.toByteArray()).single()
        assertEquals("/ভিডিও/日本語%20video.mkv", resource.href)
        assertEquals(4_294_967_297L, resource.sizeBytes)
    }

    @Test
    fun credentialVaultEncryptsUpdatesDeletesAndRecoversFromInvalidPayload() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vault = CredentialVault(context)
        val ref = vault.save(NetworkCredential(username = "alice", password = "known-plain-password", bearerToken = "known-token"))

        assertEquals("known-plain-password", vault.get(ref)?.password)
        assertEquals(ref, vault.save(NetworkCredential(username = "alice", password = "updated-password"), ref))
        assertEquals("updated-password", vault.get(ref)?.password)
        val persisted = context.getSharedPreferences("network_credential_vault_v1", android.content.Context.MODE_PRIVATE).getString(ref, "").orEmpty()
        assertFalse(persisted.contains("updated-password"))

        context.getSharedPreferences("network_credential_vault_v1", android.content.Context.MODE_PRIVATE).edit().putString(ref, "invalid").commit()
        assertNull(vault.get(ref))
        assertFalse(vault.contains(ref))

        val disposable = vault.save(NetworkCredential(password = "delete-me"))
        vault.delete(disposable)
        assertNull(vault.get(disposable))
    }

    @Test
    fun plainFtpCredentialRequiresExplicitAcknowledgement() {
        val unsafe = NetworkLocationDraft(
            displayName = "FTP",
            protocol = NetworkProtocol.FTP,
            host = "ftp.example.test",
            port = "21",
            password = "secret",
            cleartextSecurityAcknowledged = false,
        )
        assertThrows(IllegalStateException::class.java) { unsafe.location() }
        assertEquals("ftp.example.test", unsafe.copy(cleartextSecurityAcknowledged = true).location().host)
    }

    @OptIn(UnstableApi::class)
    @Test
    fun media3HttpDataSourceUsesRangesAndPreservesAuthentication() {
        val server = server()
        server.enqueue(MockResponse.Builder().code(206).setHeader("Content-Range", "bytes 5-9/10").body("56789").build())
        val url = server.url("/media/movie.mp4").toString()
        val registry = NetworkRequestRegistry().apply {
            registerUri(url, null, NetworkCredential(username = "range-user", password = "range-password"))
        }
        val source = OkHttpDataSource.Factory(NetworkHttpClientFactory.create(registry)).createDataSource()

        val length = source.open(DataSpec.Builder().setUri(url).setPosition(5L).build())
        val buffer = ByteArray(8)
        val count = source.read(buffer, 0, buffer.size)
        source.close()

        val recorded = server.takeRequest()
        assertEquals("bytes=5-", recorded.headers["Range"])
        assertTrue(recorded.headers["Authorization"].orEmpty().startsWith("Basic "))
        assertEquals(5L, length)
        assertEquals("56789", String(buffer, 0, count))
    }

    @Test
    fun redirectsWorkButCrossHostAuthorizationIsNotForwarded() {
        val origin = server()
        val destination = server()
        origin.enqueue(MockResponse.Builder().code(302).setHeader("Location", destination.url("/final")).build())
        destination.enqueue(MockResponse.Builder().code(200).body("ok").build())
        val start = origin.url("/stream/start").toString()
        val registry = NetworkRequestRegistry().apply {
            registerUri(start, null, NetworkCredential(bearerToken = "must-not-leak"))
        }
        val response = NetworkHttpClientFactory.create(registry).newCall(Request.Builder().url(start).build()).execute()
        response.use { assertEquals(200, it.code) }

        assertEquals("Bearer must-not-leak", origin.takeRequest().headers["Authorization"])
        assertNull(destination.takeRequest().headers["Authorization"])
    }

    @Test
    fun redirectLoopsFailWithinTheHttpStacksBoundedPolicy() {
        val server = server()
        repeat(22) { server.enqueue(MockResponse.Builder().code(302).setHeader("Location", "/loop").build()) }
        val request = Request.Builder().url(server.url("/loop")).build()

        assertThrows(IOException::class.java) {
            NetworkHttpClientFactory.create(NetworkRequestRegistry()).newCall(request).execute().use { }
        }
        assertTrue(server.requestCount in 2..21)
    }

    @Test
    fun largeOffsetsRemainLongAcrossNetworkModels() {
        val location = NetworkLocation(displayName = "SMB", protocol = NetworkProtocol.SMB, host = "nas", basePath = "media")
        val spec = DataSpec.Builder().setUri("maxsmb://${location.id}/huge.mkv").setPosition(3_221_225_472L).setLength(C.LENGTH_UNSET.toLong()).build()
        assertNotEquals(spec.position.toInt().toLong(), spec.position)
        assertEquals(3_221_225_472L, spec.position)
    }

    private fun server(): MockWebServer = MockWebServer().also {
        it.start()
        servers += it
    }
}

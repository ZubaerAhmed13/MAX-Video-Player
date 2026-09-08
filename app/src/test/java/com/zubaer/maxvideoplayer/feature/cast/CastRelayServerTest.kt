package com.zubaer.maxvideoplayer.feature.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI

class CastRelayServerTest {
    @Test
    fun getAndHeadExposeOnlyActiveResource() {
        withRelay { _, endpoint ->
            open(endpoint.uri).use { connection ->
                assertEquals(200, connection.responseCode)
                assertEquals("video/mp4", connection.contentType)
                assertEquals(VIRTUAL_LENGTH, connection.contentLengthLong)
                assertEquals("bytes", connection.getHeaderField("Accept-Ranges"))
                assertArrayEquals(expected(0L, 32), connection.inputStream.readNBytes(32))
            }
            open(endpoint.uri, method = "HEAD").use { connection ->
                assertEquals(200, connection.responseCode)
                assertEquals(VIRTUAL_LENGTH, connection.contentLengthLong)
                assertEquals(-1, connection.inputStream.read())
            }
            val root = URI("http://${endpoint.uri.host}:${endpoint.uri.port}/")
            open(root).use { connection -> assertEquals(403, connection.responseCode) }
        }
    }

    @Test
    fun exactRangeBeyondTwoGiBUses64BitOffsets() {
        withRelay { _, endpoint ->
            val start = 2_147_483_648L + 1234L
            val end = start + 4095L
            open(endpoint.uri, range = "bytes=$start-$end").use { connection ->
                assertEquals(206, connection.responseCode)
                assertEquals("bytes $start-$end/$VIRTUAL_LENGTH", connection.getHeaderField("Content-Range"))
                assertEquals(4096L, connection.contentLengthLong)
                assertArrayEquals(expected(start, 4096), connection.inputStream.readBytes())
            }
        }
    }

    @Test
    fun wrongTokenTraversalAndMutationMethodsFail() {
        withRelay { _, endpoint ->
            val segments = endpoint.uri.path.split('/').toMutableList()
            segments[2] = "0".repeat(64)
            val wrong = URI(endpoint.uri.scheme, endpoint.uri.userInfo, endpoint.uri.host, endpoint.uri.port, segments.joinToString("/"), null, null)
            open(wrong).use { assertEquals(403, it.responseCode) }

            val traversal = URI("http://${endpoint.uri.host}:${endpoint.uri.port}/cast/${endpoint.uri.path.split('/')[2]}/%2e%2e")
            open(traversal).use { assertEquals(403, it.responseCode) }

            open(endpoint.uri, method = "POST").use { assertEquals(405, it.responseCode) }
        }
    }

    @Test
    fun stoppingSessionInvalidatesOldUrl() {
        val server = CastRelayServer(InetAddress.getLoopbackAddress())
        val endpoint = server.start(VirtualResource())
        open(endpoint.uri, range = "bytes=0-7").use { assertEquals(206, it.responseCode) }
        server.stop()
        assertFalse(runCatching { open(endpoint.uri).use { it.responseCode }; true }.getOrDefault(false))
        server.close()
    }

    private fun withRelay(block: (CastRelayServer, CastRelayEndpoint) -> Unit) {
        CastRelayServer(InetAddress.getLoopbackAddress()).use { server ->
            val endpoint = server.start(VirtualResource())
            assertTrue(endpoint.uri.path.matches(Regex("/cast/[a-f0-9]{64}/media")))
            block(server, endpoint)
        }
    }

    private fun open(uri: URI, method: String = "GET", range: String? = null): HttpURLConnection =
        (uri.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 2_000
            readTimeout = 2_000
            useCaches = false
            if (range != null) setRequestProperty("Range", range)
        }

    private fun expected(position: Long, count: Int): ByteArray = ByteArray(count) { index -> ((position + index) and 0xff).toByte() }

    private class VirtualResource : CastRelayResource {
        override val length: Long = VIRTUAL_LENGTH
        override val mimeType: String = "video/mp4"
        override val seekable: Boolean = true

        override fun open(position: Long, length: Long?): InputStream = object : InputStream() {
            private var cursor = position
            private var remaining = length ?: (VIRTUAL_LENGTH - position)

            override fun read(): Int {
                if (remaining <= 0L || cursor >= VIRTUAL_LENGTH) return -1
                val value = (cursor and 0xff).toInt()
                cursor++
                remaining--
                return value
            }

            override fun read(buffer: ByteArray, offset: Int, len: Int): Int {
                if (remaining <= 0L || cursor >= VIRTUAL_LENGTH) return -1
                val actual = minOf(len.toLong(), remaining, VIRTUAL_LENGTH - cursor).toInt()
                for (i in 0 until actual) buffer[offset + i] = ((cursor + i) and 0xff).toByte()
                cursor += actual.toLong()
                remaining -= actual.toLong()
                return actual
            }
        }
    }

    private companion object {
        const val VIRTUAL_LENGTH = 3_221_225_473L
    }
}

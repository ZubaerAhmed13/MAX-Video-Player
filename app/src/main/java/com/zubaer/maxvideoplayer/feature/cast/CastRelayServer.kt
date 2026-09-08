package com.zubaer.maxvideoplayer.feature.cast

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session-scoped Cast relay for sources a receiver cannot open directly (content://, SMB, FTP,
 * private WebDAV/cloud, etc.). It streams original bytes with bounded buffers and byte ranges. It
 * never transcodes, never creates a whole-file cache, never exposes credentials in the URL and
 * never exposes a directory listing.
 */
class CastRelayServer(
    private val bindAddress: InetAddress,
    private val receiverAddress: InetAddress? = null,
    private val maxResources: Int = 64,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val resources = ConcurrentHashMap<String, CastRelayResource>()
    private val workers = Executors.newFixedThreadPool(MAX_CLIENTS) { runnable ->
        Thread(runnable, "max-cast-relay-client").apply { isDaemon = true }
    }
    private var acceptThread: Thread? = null
    private var socket: ServerSocket? = null
    private var sessionToken: String? = null

    @Synchronized
    fun start(primary: CastRelayResource): CastRelayEndpoint {
        check(!running.get()) { "Cast relay is already running." }
        require(!bindAddress.isAnyLocalAddress) { "Cast relay must bind a concrete interface address." }
        sessionToken = CastRelaySecurity.newSessionToken()
        resources.clear()
        resources[PRIMARY_RESOURCE_ID] = primary
        val server = ServerSocket(0, BACKLOG, bindAddress).apply { reuseAddress = false }
        socket = server
        running.set(true)
        acceptThread = Thread({ acceptLoop(server) }, "max-cast-relay-accept").apply {
            isDaemon = true
            start()
        }
        return endpointFor(PRIMARY_RESOURCE_ID)
    }

    /** Registers another active-session resource, used by manifest/subtitle relays. */
    fun register(resource: CastRelayResource): CastRelayEndpoint {
        check(running.get()) { "Cast relay is not running." }
        check(resources.size < maxResources) { "Cast relay resource registry is full." }
        var id: String
        do {
            id = CastRelaySecurity.newSessionToken().take(24)
        } while (resources.putIfAbsent(id, resource) != null)
        return endpointFor(id)
    }

    fun endpointFor(resourceId: String): CastRelayEndpoint {
        require(RESOURCE_ID.matches(resourceId) && resourceId != "." && resourceId != "..")
        check(resources.containsKey(resourceId)) { "Unknown Cast relay resource." }
        val token = checkNotNull(sessionToken) { "Cast relay is not running." }
        val port = checkNotNull(socket) { "Cast relay is not running." }.localPort
        val host = when (bindAddress) {
            is Inet6Address -> "[${bindAddress.hostAddress.substringBefore('%')}]"
            else -> bindAddress.hostAddress
        }
        return CastRelayEndpoint(URI("http://$host:$port/cast/$token/$resourceId"), resourceId)
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { socket?.close() }
        socket = null
        sessionToken = null
        resources.clear()
        acceptThread = null
    }

    override fun close() {
        stop()
        workers.shutdownNow()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running.get()) {
            try {
                val client = server.accept()
                workers.execute { handle(client) }
            } catch (_: SocketException) {
                if (!running.get()) return
            } catch (_: Throwable) {
                if (!running.get()) return
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = CLIENT_TIMEOUT_MS
            if (receiverAddress != null && socket.inetAddress != receiverAddress) {
                writeSimple(socket, 403, "Forbidden")
                return
            }
            val input = BufferedInputStream(socket.getInputStream(), BUFFER_BYTES)
            val output = BufferedOutputStream(socket.getOutputStream(), BUFFER_BYTES)
            val request = try {
                readRequest(input)
            } catch (_: Throwable) {
                writeSimple(output, 400, "Bad Request")
                return
            }
            if (request.method != "GET" && request.method != "HEAD") {
                writeResponseHeaders(output, 405, "Method Not Allowed", mapOf("Allow" to "GET, HEAD", "Content-Length" to "0", "Connection" to "close"))
                output.flush()
                return
            }
            val parsed = parsePath(request.target)
            val token = sessionToken
            if (parsed == null || token == null || parsed.token != token) {
                writeSimple(output, 403, "Forbidden")
                return
            }
            val resource = resources[parsed.resourceId]
            if (resource == null) {
                writeSimple(output, 404, "Not Found")
                return
            }
            serve(resource, request, output)
        }
    }

    private fun serve(resource: CastRelayResource, request: RelayRequest, output: BufferedOutputStream) {
        val total = resource.length
        val rangeResult = CastRangeParser.parse(request.headers["range"], total)
        val range = when (rangeResult) {
            RangeParseResult.NotRequested -> null
            is RangeParseResult.Valid -> rangeResult.range
            is RangeParseResult.Unsatisfiable -> {
                val headers = buildMap {
                    put("Accept-Ranges", if (resource.seekable) "bytes" else "none")
                    if (rangeResult.totalLength != null) put("Content-Range", "bytes */${rangeResult.totalLength}")
                    put("Content-Length", "0")
                    put("Connection", "close")
                }
                writeResponseHeaders(output, 416, "Range Not Satisfiable", headers)
                output.flush()
                return
            }
        }
        if (range != null && !resource.seekable) {
            writeResponseHeaders(output, 416, "Range Not Satisfiable", mapOf("Accept-Ranges" to "none", "Content-Length" to "0", "Connection" to "close"))
            output.flush()
            return
        }
        val start = range?.startInclusive ?: 0L
        val requestedLength = range?.length ?: total
        val status = if (range == null) 200 else 206
        val headers = linkedMapOf(
            "Content-Type" to sanitizeMime(resource.mimeType),
            "Accept-Ranges" to if (resource.seekable) "bytes" else "none",
            "Cache-Control" to "no-store",
            "Connection" to "close",
            "X-Content-Type-Options" to "nosniff",
        )
        if (requestedLength != null) headers["Content-Length"] = requestedLength.toString()
        if (range != null && total != null) headers["Content-Range"] = "bytes ${range.startInclusive}-${range.endInclusive}/$total"
        writeResponseHeaders(output, status, if (status == 206) "Partial Content" else "OK", headers)
        output.flush()
        if (request.method == "HEAD") return
        resource.open(start, requestedLength).use { source -> copyBounded(source, output, requestedLength) }
        output.flush()
    }

    private fun copyBounded(source: InputStream, output: BufferedOutputStream, requestedLength: Long?) {
        val buffer = ByteArray(BUFFER_BYTES)
        var remaining = requestedLength
        while (running.get() && (remaining == null || remaining > 0L)) {
            val maxRead = if (remaining == null) buffer.size else minOf(buffer.size.toLong(), remaining).toInt()
            val read = source.read(buffer, 0, maxRead)
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            remaining = remaining?.minus(read.toLong())
        }
    }

    private fun readRequest(input: InputStream): RelayRequest {
        val requestLine = readAsciiLine(input, MAX_REQUEST_LINE_BYTES)
        val parts = requestLine.split(' ', limit = 3)
        require(parts.size == 3 && parts[2].startsWith("HTTP/1."))
        val headers = linkedMapOf<String, String>()
        var consumed = requestLine.length
        while (true) {
            val line = readAsciiLine(input, MAX_HEADER_LINE_BYTES)
            consumed += line.length + 2
            require(consumed <= MAX_HEADERS_BYTES)
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            require(separator > 0)
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            require(name.isNotBlank())
            headers[name] = value
        }
        return RelayRequest(parts[0].uppercase(), parts[1], headers)
    }

    private fun readAsciiLine(input: InputStream, maxBytes: Int): String {
        val bytes = ByteArray(maxBytes)
        var size = 0
        while (size < maxBytes) {
            val byte = input.read()
            if (byte < 0) throw EOFException()
            if (byte == '\n'.code) {
                val actual = if (size > 0 && bytes[size - 1] == '\r'.code.toByte()) size - 1 else size
                return String(bytes, 0, actual, StandardCharsets.US_ASCII)
            }
            bytes[size++] = byte.toByte()
        }
        throw IllegalArgumentException("HTTP line exceeds safety bound.")
    }

    private fun parsePath(target: String): RelayPath? {
        if (!target.startsWith('/') || target.contains("\\")) return null
        if (target.contains("%2e", ignoreCase = true) || target.contains("%2f", ignoreCase = true) || target.contains("%5c", ignoreCase = true)) return null
        val path = target.substringBefore('?')
        val segments = path.split('/')
        if (segments.size != 4 || segments[0].isNotEmpty() || segments[1] != "cast") return null
        val token = segments[2]
        val resourceId = segments[3]
        if (!TOKEN.matches(token) || !RESOURCE_ID.matches(resourceId) || resourceId == "." || resourceId == "..") return null
        return RelayPath(token, resourceId)
    }

    private fun sanitizeMime(value: String?): String = value?.trim()?.takeIf { MIME.matches(it) } ?: "application/octet-stream"

    private fun writeSimple(socket: Socket, status: Int, reason: String) {
        BufferedOutputStream(socket.getOutputStream(), BUFFER_BYTES).use { writeSimple(it, status, reason) }
    }

    private fun writeSimple(output: BufferedOutputStream, status: Int, reason: String) {
        writeResponseHeaders(output, status, reason, mapOf("Content-Length" to "0", "Connection" to "close"))
        output.flush()
    }

    private fun writeResponseHeaders(output: BufferedOutputStream, status: Int, reason: String, headers: Map<String, String>) {
        val builder = StringBuilder("HTTP/1.1 $status $reason\r\n")
        headers.forEach { (name, value) ->
            if (!name.contains('\r') && !name.contains('\n') && !value.contains('\r') && !value.contains('\n')) {
                builder.append(name).append(": ").append(value).append("\r\n")
            }
        }
        builder.append("\r\n")
        output.write(builder.toString().toByteArray(StandardCharsets.US_ASCII))
    }

    private data class RelayRequest(val method: String, val target: String, val headers: Map<String, String>)
    private data class RelayPath(val token: String, val resourceId: String)

    companion object {
        private const val PRIMARY_RESOURCE_ID = "media"
        private const val BACKLOG = 8
        private const val MAX_CLIENTS = 8
        private const val BUFFER_BYTES = 64 * 1024
        private const val CLIENT_TIMEOUT_MS = 30_000
        private const val MAX_REQUEST_LINE_BYTES = 4 * 1024
        private const val MAX_HEADER_LINE_BYTES = 8 * 1024
        private const val MAX_HEADERS_BYTES = 16 * 1024
        private val TOKEN = Regex("[a-f0-9]{64}")
        private val RESOURCE_ID = Regex("[A-Za-z0-9_-]{1,64}")
        private val MIME = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")
    }
}

data class CastRelayEndpoint(val uri: URI, val resourceId: String)

interface CastRelayResource {
    val length: Long?
    val mimeType: String?
    val seekable: Boolean
    fun open(position: Long, length: Long?): InputStream
}

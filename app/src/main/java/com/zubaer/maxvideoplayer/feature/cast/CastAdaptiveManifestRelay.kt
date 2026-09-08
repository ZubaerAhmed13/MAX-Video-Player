package com.zubaer.maxvideoplayer.feature.cast

import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Authenticated adaptive-stream relay. The receiver only sees a session-scoped LAN URL. Original
 * Authorization/cookie headers remain inside the existing NetworkDataSourceRouter. HLS child
 * playlists and DASH URL templates are rewritten back through this same resource, including live
 * playlist refreshes and DASH $Number$/$Time$ substitutions.
 */
@UnstableApi
class CastAdaptiveManifestRelayResource(
    private val dataSourceFactory: DataSource.Factory,
    private val sourceUri: Uri,
    private val sourceMimeType: String?,
) : CastRelayRequestAwareResource {
    @Volatile
    private var endpoint: String? = null
    private val manifestCache = ConcurrentHashMap<String, CachedManifest>()

    fun bindEndpoint(endpointUri: URI) {
        endpoint = endpointUri.toString()
    }

    override val mimeType: String
        get() = manifestMime(sourceMimeType, sourceUri.toString())

    override val seekable: Boolean = true

    override val length: Long
        get() = primaryManifestBytes().size.toLong()

    override fun open(position: Long, length: Long?): InputStream =
        slice(primaryManifestBytes(), position, length)

    override fun lengthForRequest(requestTarget: String): Long? {
        val target = targetFromRequest(requestTarget) ?: return length
        return if (isManifest(target.toString())) {
            manifestBytes(target).size.toLong()
        } else {
            probeLength(Uri.parse(target.toString()))
        }
    }

    override fun openForRequest(requestTarget: String, position: Long, length: Long?): InputStream {
        val target = targetFromRequest(requestTarget) ?: return open(position, length)
        if (isManifest(target.toString())) return slice(manifestBytes(target), position, length)
        return openDataSource(Uri.parse(target.toString()), position, length)
    }

    private fun primaryManifestBytes(): ByteArray = manifestBytes(URI(sourceUri.toString()))

    private fun manifestBytes(target: URI): ByteArray {
        val now = System.currentTimeMillis()
        manifestCache[target.toString()]?.takeIf { now - it.createdAtMs <= MANIFEST_CACHE_MS }?.let { return it.bytes }
        val raw = readBounded(Uri.parse(target.toString()), MAX_MANIFEST_BYTES)
        val text = raw.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val relay = requireNotNull(endpoint) { "Cast manifest relay endpoint is not bound." }
        val rewritten = CastManifestRewriter.rewrite(
            manifest = text,
            manifestUri = target,
            relayEndpoint = relay,
        ).toByteArray(Charsets.UTF_8)
        if (rewritten.size > MAX_REWRITTEN_MANIFEST_BYTES) {
            throw IOException("Rewritten Cast manifest exceeds the 8 MB safety limit.")
        }
        manifestCache[target.toString()] = CachedManifest(rewritten, now)
        return rewritten
    }

    private fun targetFromRequest(requestTarget: String): URI? {
        val encoded = requestTarget.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.substringBefore('=') == "p" }
            ?.substringAfter('=', "")
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (encoded.length > MAX_ENCODED_TARGET_CHARS) throw IOException("Cast relay target is too long.")
        val decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        if (decoded.length > MAX_DECODED_TARGET_CHARS) throw IOException("Cast relay target is too long.")
        val resolved = URI(sourceUri.toString()).resolve(decoded).normalize()
        validateTarget(resolved)
        return resolved
    }

    private fun validateTarget(target: URI) {
        if (target.scheme?.lowercase() !in setOf("http", "https")) {
            throw IOException("Adaptive Cast relay only proxies HTTP(S) child resources.")
        }
        if (target.userInfo != null) throw IOException("Credentials in adaptive child URLs are not allowed.")
        if (CastManifestRewriter.hasSensitiveQuery(target)) {
            throw IOException("Sensitive adaptive child query parameters cannot be exposed to the receiver.")
        }
    }

    private fun probeLength(uri: Uri): Long? {
        val source = dataSourceFactory.createDataSource()
        return try {
            source.open(DataSpec.Builder().setUri(uri).build()).takeIf { it >= 0L }
        } finally {
            runCatching { source.close() }
        }
    }

    private fun openDataSource(uri: Uri, position: Long, length: Long?): InputStream {
        require(position >= 0L)
        require(length == null || length >= 0L)
        val source = dataSourceFactory.createDataSource()
        val spec = DataSpec.Builder().setUri(uri).setPosition(position).apply {
            if (length != null) setLength(length)
        }.build()
        try {
            source.open(spec)
        } catch (error: Throwable) {
            runCatching { source.close() }
            throw error
        }
        return RelayDataSourceInputStream(source)
    }

    private fun readBounded(uri: Uri, maxBytes: Long): ByteArray {
        val source = dataSourceFactory.createDataSource()
        val announced = try {
            source.open(DataSpec.Builder().setUri(uri).build())
        } catch (error: Throwable) {
            runCatching { source.close() }
            throw error
        }
        if (announced > maxBytes) {
            source.close()
            throw IOException("Adaptive Cast manifest exceeds the safety limit.")
        }
        val output = ByteArrayOutputStream(if (announced in 1..maxBytes) announced.toInt() else 8192)
        val buffer = ByteArray(16 * 1024)
        try {
            var total = 0L
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) continue
                total += read.toLong()
                if (total > maxBytes) throw IOException("Adaptive Cast manifest exceeds the safety limit.")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        } finally {
            source.close()
        }
    }

    private fun slice(bytes: ByteArray, position: Long, length: Long?): InputStream {
        if (position >= bytes.size.toLong()) return ByteArrayInputStream(ByteArray(0))
        val start = position.toInt()
        val requested = length ?: (bytes.size - start).toLong()
        val count = minOf(requested, (bytes.size - start).toLong()).toInt()
        return ByteArrayInputStream(bytes, start, count)
    }

    private data class CachedManifest(val bytes: ByteArray, val createdAtMs: Long)

    private companion object {
        const val MAX_MANIFEST_BYTES = 4L * 1024L * 1024L
        const val MAX_REWRITTEN_MANIFEST_BYTES = 8 * 1024 * 1024
        const val MAX_ENCODED_TARGET_CHARS = 16 * 1024
        const val MAX_DECODED_TARGET_CHARS = 8 * 1024
        const val MANIFEST_CACHE_MS = 1_000L

        fun manifestMime(mime: String?, uri: String): String = when {
            mime?.equals(MimeTypes.APPLICATION_MPD, ignoreCase = true) == true || uri.substringBefore('?').endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
            else -> MimeTypes.APPLICATION_M3U8
        }

        fun isManifest(uri: String): Boolean {
            val path = uri.substringBefore('?').lowercase()
            return path.endsWith(".m3u8") || path.endsWith(".mpd")
        }
    }
}

@UnstableApi
private class RelayDataSourceInputStream(private val source: DataSource) : InputStream() {
    private val single = ByteArray(1)
    private var closed = false

    override fun read(): Int {
        val count = read(single, 0, 1)
        return if (count < 0) -1 else single[0].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, offset: Int, len: Int): Int {
        if (closed) throw IOException("Relay source is closed.")
        if (len == 0) return 0
        return source.read(buffer, offset, len)
    }

    override fun close() {
        if (closed) return
        closed = true
        source.close()
    }
}

/** Pure-JVM manifest rewriting policy, intentionally independent of android.net.Uri. */
object CastManifestRewriter {
    private val uriAttribute = Regex("URI=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
    private val dashUrlAttribute = Regex("\\b(media|initialization|sourceURL|href)=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
    private val baseUrlElement = Regex("(<BaseURL(?:\\s[^>]*)?>)([^<]+)(</BaseURL>)", RegexOption.IGNORE_CASE)
    private val sensitiveQueryNames = setOf(
        "sig",
        "signature",
        "token",
        "access_token",
        "auth",
        "authorization",
        "se",
        "sp",
        "sv",
        "x-amz-signature",
        "x-goog-signature",
    )

    fun rewrite(manifest: String, manifestUri: URI, relayEndpoint: String): String {
        val trimmed = manifest.trimStart()
        return if (trimmed.startsWith("#EXTM3U")) {
            rewriteHls(manifest, manifestUri, relayEndpoint)
        } else {
            rewriteDash(manifest, manifestUri, relayEndpoint)
        }
    }

    fun hasSensitiveQuery(uri: URI): Boolean {
        val raw = uri.rawQuery ?: return false
        return raw.split('&')
            .map { component -> URLDecoder.decode(component.substringBefore('='), StandardCharsets.UTF_8.name()).lowercase() }
            .any { key ->
                key in sensitiveQueryNames ||
                    key.endsWith("_token") ||
                    key.endsWith("-token") ||
                    key.endsWith("_signature") ||
                    key.endsWith("-signature")
            }
    }

    private fun rewriteHls(manifest: String, base: URI, endpoint: String): String =
        manifest.lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith('#') -> uriAttribute.replace(line) { match ->
                    "URI=\"${relayReference(match.groupValues[1], base, endpoint)}\""
                }
                else -> relayReference(line.trim(), base, endpoint)
            }
        } + if (manifest.endsWith('\n')) "\n" else ""

    private fun rewriteDash(manifest: String, manifestBase: URI, endpoint: String): String {
        val firstBase = baseUrlElement.find(manifest)?.groupValues?.getOrNull(2)?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { manifestBase.resolve(it) }
            ?: manifestBase
        var rewritten = baseUrlElement.replace(manifest) { match ->
            val value = match.groupValues[2].trim()
            match.groupValues[1] + relayReference(value, manifestBase, endpoint) + match.groupValues[3]
        }
        rewritten = dashUrlAttribute.replace(rewritten) { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[2]
            "$name=\"${relayReference(value, firstBase, endpoint)}\""
        }
        return rewritten
    }

    private fun relayReference(reference: String, base: URI, endpoint: String): String {
        val resolved = base.resolve(reference).normalize()
        require(resolved.scheme?.lowercase() in setOf("http", "https")) { "Adaptive child must resolve to HTTP(S)." }
        require(resolved.userInfo == null) { "Adaptive child URL must not contain user-info credentials." }
        require(!hasSensitiveQuery(resolved)) { "Sensitive adaptive query cannot be copied into a Cast URL." }

        val encoded = URLEncoder.encode(resolved.toString(), StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%24", "$")
        return "$endpoint?p=$encoded"
    }
}

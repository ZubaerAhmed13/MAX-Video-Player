package com.zubaer.maxvideoplayer.feature.network.protocol.webdav

import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntry
import com.zubaer.maxvideoplayer.feature.network.model.NetworkFailure
import com.zubaer.maxvideoplayer.feature.network.model.NetworkFilePolicy
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
import com.zubaer.maxvideoplayer.feature.network.protocol.ConnectionTestResult
import com.zubaer.maxvideoplayer.feature.network.protocol.NetworkProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.NetworkProtocolException
import com.zubaer.maxvideoplayer.feature.network.protocol.http.credentialHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class WebDavProtocolClient(
    private val client: OkHttpClient,
) : NetworkProtocolClient {
    override suspend fun testConnection(location: NetworkLocation, credential: NetworkCredential?): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            runCatching { propFind(location, credential, "", depth = 0); ConnectionTestResult(true, "Connected") }
                .getOrElse { error ->
                    val protocol = error as? NetworkProtocolException
                    ConnectionTestResult(false, protocol?.message ?: "Server unavailable", protocol?.failure ?: NetworkFailure.ServerNotFound)
                }
        }

    override suspend fun list(location: NetworkLocation, credential: NetworkCredential?, remotePath: String): List<NetworkEntry> =
        withContext(Dispatchers.IO) {
            val requestedUrl = playbackUri(location, NetworkUriPolicy.safeRemotePath(remotePath)).ensureTrailingSlash()
            propFind(location, credential, remotePath, 1)
                .filterNot { canonicalUrl(it.href) == canonicalUrl(requestedUrl) }
                .map { item -> item.toEntry(location, requestedUrl) }
                .sortedWith(compareBy<NetworkEntry> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

    override suspend fun stat(location: NetworkLocation, credential: NetworkCredential?, remotePath: String): NetworkEntry? =
        withContext(Dispatchers.IO) {
            propFind(location, credential, remotePath, 0).firstOrNull()?.toEntry(location, playbackUri(location, remotePath))
        }

    override fun playbackUri(location: NetworkLocation, remotePath: String): String {
        val authority = if (location.port == 443) location.host else "${location.host}:${location.port}"
        val combined = listOf(location.basePath, remotePath).filter { it.isNotBlank() }.joinToString("/")
        return "https://$authority/${combined.trimStart('/')}"
    }

    private fun propFind(location: NetworkLocation, credential: NetworkCredential?, remotePath: String, depth: Int): List<WebDavResource> {
        val url = playbackUri(location, remotePath).ensureTrailingSlash()
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).method("PROPFIND", body).header("Depth", depth.toString()).apply {
            header("User-Agent", "MAXVideoPlayer/0.7 Android")
            credentialHeaders(credential).forEach { (name, value) -> header(name, value) }
        }.build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> throw NetworkProtocolException(NetworkFailure.AuthenticationFailed, "Authentication failed")
                403 -> throw NetworkProtocolException(NetworkFailure.PermissionDenied, "Access denied")
                404 -> throw NetworkProtocolException(NetworkFailure.PathNotFound, "Server path not found")
            }
            if (response.code !in setOf(200, 207)) {
                throw NetworkProtocolException(NetworkFailure.ServerRejected, "WebDAV server rejected the request (${response.code})")
            }
            val responseBody = response.body
            val bytes = responseBody.bytes()
            if (bytes.size > MAX_XML_BYTES) throw NetworkProtocolException(NetworkFailure.MalformedResponse, "WebDAV directory response is too large")
            return SecureWebDavParser.parse(bytes)
        }
    }

    private fun WebDavResource.toEntry(location: NetworkLocation, requestUrl: String): NetworkEntry {
        val absolute = URI(requestUrl).resolve(href).toString()
        val decodedPath = runCatching { URI(absolute).path }.getOrDefault(href)
        val rootPath = URI(playbackUri(location, "")).path.trimEnd('/')
        val remote = decodedPath.removePrefix(rootPath).trim('/')
        val name = decodedPath.trimEnd('/').substringAfterLast('/').ifBlank { location.displayName }
        return NetworkEntry(
            name = name,
            uri = absolute,
            type = NetworkFilePolicy.type(name, directory),
            sizeBytes = sizeBytes,
            modifiedAtMs = modifiedAtMs,
            mimeType = contentType ?: NetworkFilePolicy.mimeType(name),
            sourceId = location.id,
            remotePath = remote,
        )
    }

    private fun String.ensureTrailingSlash() = if (endsWith('/')) this else "$this/"
    private fun canonicalUrl(url: String) = runCatching { URI(url).normalize().toString().trimEnd('/') }.getOrDefault(url.trimEnd('/'))

    private companion object {
        const val MAX_XML_BYTES = 4 * 1024 * 1024
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getcontenttype/><d:getlastmodified/></d:prop></d:propfind>"""
    }
}

internal data class WebDavResource(
    val href: String,
    val directory: Boolean,
    val sizeBytes: Long?,
    val contentType: String?,
    val modifiedAtMs: Long?,
)

internal object SecureWebDavParser {
    fun parse(bytes: ByteArray): List<WebDavResource> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(ByteArrayInputStream(bytes), Charsets.UTF_8.name())
        }
        val resources = mutableListOf<WebDavResource>()
        var current: MutableWebDavResource? = null
        var currentTextElement: String? = null
        val text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.DOCDECL -> throw IllegalArgumentException("WebDAV XML document types are not allowed")
                XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.ROOT)) {
                    "response" -> current = MutableWebDavResource()
                    "collection" -> current?.directory = true
                    "href", "getcontentlength", "getcontenttype", "getlastmodified" -> {
                        currentTextElement = parser.name.lowercase(Locale.ROOT)
                        text.setLength(0)
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (currentTextElement != null) text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase(Locale.ROOT)
                    if (name == currentTextElement) {
                        current?.set(name, text.toString().trim())
                        currentTextElement = null
                        text.setLength(0)
                    }
                    if (name == "response") {
                        current?.build()?.let(resources::add)
                        current = null
                    }
                }
            }
            event = parser.nextToken()
        }
        return resources
    }

    private class MutableWebDavResource {
        var href: String? = null
        var directory: Boolean = false
        var sizeBytes: Long? = null
        var contentType: String? = null
        var modifiedAtMs: Long? = null

        fun set(name: String, value: String) {
            when (name) {
                "href" -> href = value.takeIf(String::isNotBlank)
                "getcontentlength" -> sizeBytes = value.toLongOrNull()
                "getcontenttype" -> contentType = value.takeIf(String::isNotBlank)
                "getlastmodified" -> modifiedAtMs = runCatching {
                    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                        isLenient = false
                    }.parse(value)?.time
                }.getOrNull()
            }
        }

        fun build(): WebDavResource? = href?.let {
            WebDavResource(it, directory, sizeBytes, contentType, modifiedAtMs)
        }
    }
}

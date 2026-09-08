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
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

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
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            setExpandEntityReferences(false)
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        return document.getElementsByTagNameNS("DAV:", "response").asSequence().mapNotNull { node ->
            val response = node as? Element ?: return@mapNotNull null
            val href = response.text("href")?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val contentLength = response.text("getcontentlength")?.trim()?.toLongOrNull()
            val contentType = response.text("getcontenttype")?.trim()?.takeIf { it.isNotBlank() }
            val modified = response.text("getlastmodified")?.let {
                runCatching {
                    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                        isLenient = false
                    }.parse(it.trim())?.time
                }.getOrNull()
            }
            WebDavResource(
                href = href,
                directory = response.getElementsByTagNameNS("DAV:", "collection").length > 0,
                sizeBytes = contentLength,
                contentType = contentType,
                modifiedAtMs = modified,
            )
        }.toList()
    }

    private fun Element.text(localName: String): String? = getElementsByTagNameNS("DAV:", localName).item(0)?.textContent
    private fun org.w3c.dom.NodeList.asSequence(): Sequence<Node> = sequence {
        for (index in 0 until length) yield(item(index))
    }
}

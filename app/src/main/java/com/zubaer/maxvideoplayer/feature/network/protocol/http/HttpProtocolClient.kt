package com.zubaer.maxvideoplayer.feature.network.protocol.http

import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntry
import com.zubaer.maxvideoplayer.feature.network.model.NetworkFailure
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
import com.zubaer.maxvideoplayer.feature.network.protocol.ConnectionTestResult
import com.zubaer.maxvideoplayer.feature.network.protocol.NetworkProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.NetworkProtocolException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

class HttpProtocolClient(
    private val client: OkHttpClient,
) : NetworkProtocolClient {
    override suspend fun testConnection(location: NetworkLocation, credential: NetworkCredential?): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val url = playbackUri(location, "")
            val request = Request.Builder().url(url).head().applyHeaders(credentialHeaders(credential)).build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        401 -> ConnectionTestResult(false, "Authentication failed", NetworkFailure.AuthenticationFailed)
                        403 -> ConnectionTestResult(false, "Access denied", NetworkFailure.PermissionDenied)
                        404 -> ConnectionTestResult(false, "Server path not found", NetworkFailure.PathNotFound)
                        in 200..399, 405 -> ConnectionTestResult(true, "Connected")
                        else -> ConnectionTestResult(false, "Server rejected the request (${response.code})", NetworkFailure.ServerRejected)
                    }
                }
            }.getOrElse { failureResult(it) }
        }

    override suspend fun list(
        location: NetworkLocation,
        credential: NetworkCredential?,
        remotePath: String,
    ): List<NetworkEntry> = throw NetworkProtocolException(
        NetworkFailure.ProtocolUnsupported,
        "This HTTP source does not expose a WebDAV directory. Use a direct stream URL.",
    )

    override suspend fun stat(location: NetworkLocation, credential: NetworkCredential?, remotePath: String): NetworkEntry? = null

    override fun playbackUri(location: NetworkLocation, remotePath: String): String {
        val scheme = if (location.protocol.name == "HTTP") "http" else "https"
        val authority = if (location.port == location.protocol.defaultPort) location.host else "${location.host}:${location.port}"
        val path = listOf(location.basePath, remotePath).filter { it.isNotBlank() }.joinToString("/")
        return NetworkUriPolicy.normalizeForPlayback("$scheme://$authority/${path.trimStart('/')}")
    }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder = apply {
        header("User-Agent", "MAXVideoPlayer/0.7 Android")
        headers.forEach { (name, value) -> header(name, value) }
    }

    private fun failureResult(error: Throwable): ConnectionTestResult = when (error) {
        is SocketTimeoutException -> ConnectionTestResult(false, "Connection timed out", NetworkFailure.ConnectionTimeout)
        is SSLException -> ConnectionTestResult(false, "Secure connection could not be verified", NetworkFailure.TlsFailure)
        is IOException -> ConnectionTestResult(false, "Server unavailable", NetworkFailure.ServerNotFound)
        else -> ConnectionTestResult(false, "Connection failed", NetworkFailure.Other(error.javaClass.simpleName))
    }
}

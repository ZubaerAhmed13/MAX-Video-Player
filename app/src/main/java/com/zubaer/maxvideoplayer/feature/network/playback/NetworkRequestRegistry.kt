package com.zubaer.maxvideoplayer.feature.network.playback

import android.net.Uri
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkPlaybackRequest
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.security.SensitiveHeaderPolicy
import java.util.concurrent.ConcurrentHashMap

data class NetworkAccessContext(
    val location: NetworkLocation?,
    val credential: NetworkCredential?,
    val headers: Map<String, String>,
    val rootUri: String,
) {
    fun requestHeaders(): Map<String, String> = buildMap {
        putAll(headers)
        credential?.headers?.let(::putAll)
        credential?.bearerToken?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
        val user = credential?.username.orEmpty()
        val password = credential?.password.orEmpty()
        if (user.isNotBlank() && !containsKey("Authorization")) {
            val raw = "$user:$password".toByteArray(Charsets.UTF_8)
            put("Authorization", "Basic ${android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)}")
        }
    }
}

/** Process-local routing state. Secrets never enter MediaItem metadata, a URI, Room, or logs. */
class NetworkRequestRegistry {
    private val exact = ConcurrentHashMap<String, NetworkAccessContext>()
    private val httpRoots = ConcurrentHashMap<String, NetworkAccessContext>()

    fun register(request: NetworkPlaybackRequest, location: NetworkLocation?, credential: NetworkCredential?) {
        val context = NetworkAccessContext(location, credential, request.headers, request.media.uri)
        exact[request.media.uri] = context
        if (request.media.uri.startsWith("http://") || request.media.uri.startsWith("https://")) {
            httpRoots[httpDirectoryRoot(request.media.uri)] = context
        }
    }

    fun registerUri(uri: String, location: NetworkLocation?, credential: NetworkCredential?, headers: Map<String, String> = emptyMap()) {
        val context = NetworkAccessContext(location, credential, headers, uri)
        exact[uri] = context
        if (uri.startsWith("http://") || uri.startsWith("https://")) httpRoots[httpDirectoryRoot(uri)] = context
    }

    fun resolve(uri: String): NetworkAccessContext? = exact[uri] ?: resolveHttp(uri)

    fun resolveHttp(uri: String): NetworkAccessContext? {
        val candidate = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        if (candidate.scheme !in setOf("http", "https")) return null
        return httpRoots.values
            .filter { context ->
                val root = Uri.parse(context.rootUri)
                root.scheme == candidate.scheme && root.host.equals(candidate.host, ignoreCase = true) &&
                    effectivePort(root) == effectivePort(candidate) && pathFallsUnder(
                        candidate.path.orEmpty(),
                        root.path.orEmpty().substringBeforeLast('/', ""),
                    )
            }
            .maxByOrNull { Uri.parse(it.rootUri).path.orEmpty().length }
    }

    fun clear(uri: String) {
        exact.remove(uri)
        httpRoots.remove(httpDirectoryRoot(uri))
    }

    fun safeHeaders(uri: String): Map<String, String> =
        resolve(uri)?.requestHeaders()?.mapValues { (name, value) -> if (SensitiveHeaderPolicy.isSensitive(name)) "***" else value }.orEmpty()

    /**
     * Media3 RTSP supports BASIC/DIGEST credentials only through URI user-info. Keep that URI
     * process-local at the final media-source boundary; callers persist and expose only [uri].
     */
    fun authenticatedRtspUri(uri: String): Uri {
        val parsed = Uri.parse(uri)
        if (!parsed.scheme.equals("rtsp", ignoreCase = true) || parsed.userInfo != null) return parsed
        val credential = resolve(uri)?.credential ?: return parsed
        val username = credential.username.takeIf { it.isNotBlank() } ?: return parsed
        val host = parsed.host ?: return parsed
        val encodedHost = if (':' in host && !host.startsWith('[')) "[$host]" else host
        val hostPort = encodedHost + if (parsed.port > 0) ":${parsed.port}" else ""
        val userInfo = "${Uri.encode(username)}:${Uri.encode(credential.password)}"
        return parsed.buildUpon().encodedAuthority("$userInfo@$hostPort").build()
    }

    private fun httpDirectoryRoot(uri: String): String {
        val parsed = Uri.parse(uri)
        val directory = parsed.path.orEmpty().substringBeforeLast('/', "").trimEnd('/')
        return "${parsed.scheme}://${parsed.host}:${effectivePort(parsed)}$directory"
    }

    private fun pathFallsUnder(candidatePath: String, directoryPath: String): Boolean {
        val root = directoryPath.trimEnd('/')
        return root.isEmpty() || candidatePath == root || candidatePath.startsWith("$root/")
    }

    private fun effectivePort(uri: Uri): Int = when {
        uri.port > 0 -> uri.port
        uri.scheme == "https" -> 443
        else -> 80
    }
}

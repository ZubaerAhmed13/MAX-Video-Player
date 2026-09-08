package com.zubaer.maxvideoplayer.feature.network.model

import android.net.Uri
import com.zubaer.maxvideoplayer.core.media.StableMediaIdentity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import java.util.Locale
import java.util.UUID

enum class NetworkProtocol(val defaultPort: Int, val browsable: Boolean, val encrypted: Boolean) {
    HTTP(80, false, false),
    HTTPS(443, false, true),
    HLS(443, false, true),
    DASH(443, false, true),
    RTSP(554, false, false),
    SMB(445, true, false),
    WEBDAV(443, true, true),
    FTP(21, true, false),
    FTPS(21, true, true),
}

data class NetworkLocation(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int = protocol.defaultPort,
    val basePath: String = "",
    val credentialRef: String? = null,
    val usernameHint: String? = null,
    val useGuest: Boolean = false,
    val ftpPassiveMode: Boolean = true,
    val ftpSecurityAcknowledged: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val lastConnectedAtMs: Long? = null,
) {
    init {
        require(displayName.isNotBlank())
        require(host.isNotBlank())
        require(port in 1..65535)
    }
}

data class NetworkCredential(
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val bearerToken: String = "",
    val headers: Map<String, String> = emptyMap(),
) {
    fun isEmpty(): Boolean = username.isBlank() && password.isBlank() && bearerToken.isBlank() && headers.isEmpty()
}

enum class NetworkEntryType { DIRECTORY, VIDEO, AUDIO, SUBTITLE, PLAYLIST, OTHER }

data class NetworkEntry(
    val name: String,
    val uri: String,
    val type: NetworkEntryType,
    val sizeBytes: Long? = null,
    val modifiedAtMs: Long? = null,
    val mimeType: String? = null,
    val isDirectory: Boolean = type == NetworkEntryType.DIRECTORY,
    val sourceId: String,
    val remotePath: String,
) {
    fun toAppMedia(): AppMedia {
        val identity = StableMediaIdentity.forNetworkSource(sourceId, remotePath)
        return AppMedia(
            stableId = identity,
            uri = uri,
            title = name,
            fileName = name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            dateModifiedMs = modifiedAtMs,
            relativePath = remotePath,
            sourceId = sourceId,
            sourceType = MediaSourceType.NETWORK,
        )
    }
}

data class NetworkPlaybackRequest(
    val media: AppMedia,
    val locationId: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val credentialRef: String? = null,
    val protocol: NetworkProtocol,
    val canonicalIdentity: String = media.stableId,
)

enum class NetworkConnectionState { IDLE, CONNECTING, CONNECTED, OFFLINE, AUTHENTICATION_REQUIRED, FAILED }
enum class NetworkPlaybackPhase { IDLE, INITIAL_LOADING, PLAYING, BUFFERING, RECONNECTING, PAUSED, FAILED }
enum class NetworkTransport { WIFI, CELLULAR, ETHERNET, VPN, OTHER, NONE }

sealed interface NetworkFailure {
    data object NetworkUnavailable : NetworkFailure
    data object DnsFailure : NetworkFailure
    data object ConnectionTimeout : NetworkFailure
    data object ReadTimeout : NetworkFailure
    data object TlsFailure : NetworkFailure
    data object AuthenticationFailed : NetworkFailure
    data object PermissionDenied : NetworkFailure
    data object ServerNotFound : NetworkFailure
    data object PathNotFound : NetworkFailure
    data object RangeUnsupported : NetworkFailure
    data object ProtocolUnsupported : NetworkFailure
    data object ServerRejected : NetworkFailure
    data object RemoteFileChanged : NetworkFailure
    data object Disconnected : NetworkFailure
    data object MalformedResponse : NetworkFailure
    data class Other(val safeMessage: String) : NetworkFailure
}

data class NetworkDiagnostics(
    val protocol: NetworkProtocol? = null,
    val host: String? = null,
    val sanitizedUri: String? = null,
    val connectionState: NetworkConnectionState = NetworkConnectionState.IDLE,
    val phase: NetworkPlaybackPhase = NetworkPlaybackPhase.IDLE,
    val transport: NetworkTransport = NetworkTransport.NONE,
    val networkAvailable: Boolean = true,
    val validatedInternet: Boolean = false,
    val metered: Boolean = false,
    val contentType: String? = null,
    val contentLength: Long? = null,
    val seekable: Boolean? = null,
    val bufferedDurationMs: Long? = null,
    val estimatedBandwidthBitsPerSecond: Long? = null,
    val responseStatus: Int? = null,
    val retryCount: Int = 0,
    val lastReconnectAtMs: Long? = null,
    val events: List<String> = emptyList(),
)

object NetworkUriPolicy {
    private val sensitiveQueryNames = setOf(
        "token", "access_token", "auth", "authorization", "signature", "sig", "key", "api_key", "apikey", "password",
    )
    private val sensitiveHeaders = setOf("authorization", "cookie", "proxy-authorization", "x-api-key")

    private fun isSensitiveQuery(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower in sensitiveQueryNames || lower.contains("token") || lower.contains("signature") ||
            lower.contains("credential") || lower.startsWith("x-amz-") || lower.startsWith("x-goog-")
    }

    fun normalizeForPlayback(raw: String): String {
        val trimmed = raw.trim()
        val uri = Uri.parse(trimmed)
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https", "rtsp", "smb", "ftp", "ftps", "maxsmb", "maxftp", "maxftps")) {
            "Unsupported network protocol."
        }
        require(!uri.host.isNullOrBlank()) { "A server host is required." }
        require(uri.userInfo == null) { "Credentials must be entered separately from the network URL." }
        return trimmed
    }

    fun sanitize(raw: String): String = runCatching {
        val uri = Uri.parse(raw)
        val builder = uri.buildUpon()
        if (uri.userInfo != null) {
            val user = uri.userInfo?.substringBefore(':').orEmpty()
            val hostPort = uri.host.orEmpty() + if (uri.port > 0) ":${uri.port}" else ""
            builder.encodedAuthority(if (user.isBlank()) hostPort else "$user:***@$hostPort")
        }
        builder.clearQuery()
        uri.queryParameterNames.forEach { name ->
            val value = if (isSensitiveQuery(name)) "***" else uri.getQueryParameter(name).orEmpty()
            builder.appendQueryParameter(name, value)
        }
        builder.build().toString()
    }.getOrElse { "invalid-network-uri" }

    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> = headers.mapValues { (name, value) ->
        if (name.lowercase(Locale.ROOT) in sensitiveHeaders) "***" else value
    }

    fun canonicalIdentity(raw: String): String {
        val uri = Uri.parse(raw)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT).let { if (':' in it && !it.startsWith('[')) "[$it]" else it }
        val hostPort = host + if (uri.port > 0) ":${uri.port}" else ""
        val user = uri.userInfo?.substringBefore(':')?.takeIf(String::isNotBlank)?.let(Uri::encode)
        val builder = uri.buildUpon()
            .scheme(uri.scheme?.lowercase(Locale.ROOT))
            .encodedAuthority(if (user == null) hostPort else "$user@$hostPort")
            .fragment(null)
            .clearQuery()
        uri.queryParameterNames.sorted().forEach { name ->
            if (!isSensitiveQuery(name)) {
                uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
            }
        }
        return builder.build().toString()
    }

    fun persistenceSafeUri(raw: String): String = canonicalIdentity(raw)

    fun containsSensitiveMaterial(raw: String): Boolean = runCatching {
        val uri = Uri.parse(raw)
        uri.userInfo != null || uri.queryParameterNames.any(::isSensitiveQuery)
    }.getOrDefault(true)

    fun safeRemotePath(path: String): String {
        val normalized = path.replace('\\', '/').split('/').fold(mutableListOf<String>()) { parts, segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex) else throw IllegalArgumentException("Path escapes source root.")
                else -> parts += segment
            }
            parts
        }
        return normalized.joinToString("/")
    }
}

object NetworkFilePolicy {
    private val video = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "ts", "m2ts", "mpg", "mpeg")
    private val audio = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
    private val subtitle = setOf("srt", "vtt", "ass", "ssa", "ttml")
    private val playlist = setOf("m3u", "m3u8", "mpd")

    fun type(name: String, directory: Boolean): NetworkEntryType {
        if (directory) return NetworkEntryType.DIRECTORY
        return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            in video -> NetworkEntryType.VIDEO
            in audio -> NetworkEntryType.AUDIO
            in subtitle -> NetworkEntryType.SUBTITLE
            in playlist -> NetworkEntryType.PLAYLIST
            else -> NetworkEntryType.OTHER
        }
    }

    fun mimeType(name: String): String? = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "m3u8" -> "application/x-mpegURL"
        "mpd" -> "application/dash+xml"
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        else -> null
    }
}

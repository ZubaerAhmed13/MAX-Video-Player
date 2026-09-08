package com.zubaer.maxvideoplayer.feature.network.repository

import android.net.Uri
import com.zubaer.maxvideoplayer.core.media.StableMediaIdentity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntry
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkPlaybackRequest
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
import com.zubaer.maxvideoplayer.core.database.MediaHistoryEntity
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkRequestRegistry
import com.zubaer.maxvideoplayer.feature.network.protocol.ConnectionTestResult
import com.zubaer.maxvideoplayer.feature.network.protocol.NetworkProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.ftp.FtpProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.http.HttpProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.smb.SmbProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.webdav.WebDavProtocolClient
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

class NetworkRepository(
    private val locations: NetworkLocationRepository,
    private val requestRegistry: NetworkRequestRegistry,
    httpClient: OkHttpClient,
) {
    private val http = HttpProtocolClient(httpClient)
    private val webDav = WebDavProtocolClient(httpClient)
    private val smb = SmbProtocolClient()
    private val ftp = FtpProtocolClient()

    fun observeLocations(): Flow<List<NetworkLocation>> = locations.observeAll()

    suspend fun saveLocation(location: NetworkLocation, credential: NetworkCredential?, rememberCredential: Boolean): NetworkLocation =
        locations.save(location, credential, rememberCredential)

    suspend fun test(location: NetworkLocation, credential: NetworkCredential?): ConnectionTestResult {
        val result = client(location.protocol).testConnection(location, credential)
        if (result.connected) locations.markConnected(location.id)
        return result
    }

    suspend fun testSaved(location: NetworkLocation): ConnectionTestResult = test(location, locations.credential(location))

    suspend fun list(location: NetworkLocation, remotePath: String): List<NetworkEntry> =
        client(location.protocol).list(location, locations.credential(location), remotePath)

    suspend fun prepare(entry: NetworkEntry): AppMedia {
        val location = locations.get(entry.sourceId) ?: error("Saved network location is unavailable")
        val credential = locations.credential(location)
        requestRegistry.registerUri(entry.uri, location, credential)
        return entry.toAppMedia()
    }

    suspend fun prepareHistory(history: MediaHistoryEntity): AppMedia {
        val uri = Uri.parse(history.uri)
        val candidates = locations.all()
        val location = when (uri.scheme?.lowercase()) {
            "maxsmb" -> uri.host?.let { id -> candidates.firstOrNull { it.id == id } }
            "ftp", "ftps" -> candidates.firstOrNull {
                it.host.equals(uri.host, true) && it.port == (uri.port.takeIf { port -> port > 0 } ?: it.protocol.defaultPort) &&
                    it.protocol in setOf(NetworkProtocol.FTP, NetworkProtocol.FTPS)
            }
            "http", "https" -> candidates.filter { it.protocol == NetworkProtocol.WEBDAV || it.protocol == NetworkProtocol.HTTP || it.protocol == NetworkProtocol.HTTPS }
                .filter { it.host.equals(uri.host, true) }
                .maxByOrNull { it.basePath.length }
            else -> null
        }
        requestRegistry.registerUri(history.uri, location, location?.let(locations::credential))
        return AppMedia(
            stableId = history.stableMediaId,
            uri = history.uri,
            title = history.title,
            mimeType = history.mimeType,
            sizeBytes = history.sizeBytes,
            width = history.width,
            height = history.height,
            durationMs = history.durationMs.takeIf { it > 0L },
            sourceId = location?.id,
            sourceType = MediaSourceType.NETWORK,
        )
    }

    fun prepareDirect(
        rawUrl: String,
        title: String = "",
        credential: NetworkCredential? = null,
        headers: Map<String, String> = emptyMap(),
    ): AppMedia {
        val url = NetworkUriPolicy.normalizeForPlayback(rawUrl)
        val canonical = NetworkUriPolicy.canonicalIdentity(url)
        val uri = Uri.parse(url)
        val protocol = detectProtocol(url)
        val playbackUrl = if (protocol == NetworkProtocol.RTSP && uri.userInfo == null && credential?.username?.isNotBlank() == true) {
            val hostPort = uri.host.orEmpty() + if (uri.port > 0) ":${uri.port}" else ""
            uri.buildUpon().encodedAuthority(
                "${Uri.encode(credential.username)}:${Uri.encode(credential.password)}@$hostPort",
            ).build().toString()
        } else url
        val media = AppMedia(
            stableId = StableMediaIdentity.forNetwork(canonical),
            uri = playbackUrl,
            title = title.ifBlank { uri.lastPathSegment?.takeIf(String::isNotBlank) ?: uri.host ?: "Network stream" },
            mimeType = when (protocol) {
                NetworkProtocol.HLS -> "application/x-mpegURL"
                NetworkProtocol.DASH -> "application/dash+xml"
                else -> null
            },
            sourceType = MediaSourceType.NETWORK,
        )
        requestRegistry.register(
            NetworkPlaybackRequest(media, headers = headers, protocol = protocol, canonicalIdentity = media.stableId),
            location = null,
            credential = credential,
        )
        return media
    }

    suspend fun removeLocation(id: String) = locations.remove(id)
    suspend fun forgetCredentials(id: String) = locations.forgetCredentials(id)

    fun credential(location: NetworkLocation): NetworkCredential? = locations.credential(location)

    fun playbackUri(location: NetworkLocation, remotePath: String): String = client(location.protocol).playbackUri(location, remotePath)

    private fun client(protocol: NetworkProtocol): NetworkProtocolClient = when (protocol) {
        NetworkProtocol.SMB -> smb
        NetworkProtocol.WEBDAV -> webDav
        NetworkProtocol.FTP, NetworkProtocol.FTPS -> ftp
        NetworkProtocol.HTTP, NetworkProtocol.HTTPS, NetworkProtocol.HLS, NetworkProtocol.DASH, NetworkProtocol.RTSP -> http
    }

    companion object {
        fun detectProtocol(url: String): NetworkProtocol {
            val uri = Uri.parse(url)
            val path = uri.path.orEmpty().lowercase()
            return when {
                uri.scheme.equals("rtsp", true) -> NetworkProtocol.RTSP
                uri.scheme.equals("ftp", true) -> NetworkProtocol.FTP
                uri.scheme.equals("ftps", true) -> NetworkProtocol.FTPS
                uri.scheme.equals("smb", true) || uri.scheme.equals("maxsmb", true) -> NetworkProtocol.SMB
                path.endsWith(".m3u8") -> NetworkProtocol.HLS
                path.endsWith(".mpd") -> NetworkProtocol.DASH
                uri.scheme.equals("http", true) -> NetworkProtocol.HTTP
                else -> NetworkProtocol.HTTPS
            }
        }
    }
}

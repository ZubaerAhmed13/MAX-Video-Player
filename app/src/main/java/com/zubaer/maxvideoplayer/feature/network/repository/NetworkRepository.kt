package com.zubaer.maxvideoplayer.feature.network.repository

import android.net.Uri
import com.zubaer.maxvideoplayer.core.media.StableMediaIdentity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntry
import com.zubaer.maxvideoplayer.feature.network.model.NetworkFilePolicy
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkPlaylistParser
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

class NetworkRepository(
    private val locations: NetworkLocationRepository,
    private val requestRegistry: NetworkRequestRegistry,
    private val httpClient: OkHttpClient,
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

    suspend fun prepareLocation(location: NetworkLocation): AppMedia {
        val credential = locations.credential(location)
        val uri = client(location.protocol).playbackUri(location, "")
        val media = AppMedia(
            stableId = StableMediaIdentity.forNetworkSource(location.id, location.basePath),
            uri = uri,
            title = location.displayName,
            mimeType = when (location.protocol) {
                NetworkProtocol.HLS -> "application/x-mpegURL"
                NetworkProtocol.DASH -> "application/dash+xml"
                else -> NetworkFilePolicy.mimeType(location.basePath)
            },
            relativePath = location.basePath,
            sourceId = location.id,
            sourceType = MediaSourceType.NETWORK,
        )
        requestRegistry.registerUri(uri, location, credential)
        return media
    }

    suspend fun prepareQueue(entry: NetworkEntry): List<AppMedia> {
        val extension = entry.name.substringAfterLast('.', "").lowercase()
        if (entry.type != com.zubaer.maxvideoplayer.feature.network.model.NetworkEntryType.PLAYLIST || extension != "m3u") {
            return listOf(prepare(entry))
        }
        val location = locations.get(entry.sourceId) ?: error("Saved network location is unavailable")
        if (!entry.uri.startsWith("http://") && !entry.uri.startsWith("https://")) {
            error("M3U queue expansion is currently available for HTTP and WebDAV sources. Other playlist files can still be opened as media sources.")
        }
        val credential = locations.credential(location)
        requestRegistry.registerUri(entry.uri, location, credential)
        val content = withContext(Dispatchers.IO) {
            httpClient.newCall(Request.Builder().url(entry.uri).build()).execute().use { response ->
                if (!response.isSuccessful) error("Playlist server returned ${response.code}")
                response.body.byteStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (output.size() + read > MAX_PLAYLIST_BYTES) error("Network playlist exceeds the 2 MB safety limit")
                        output.write(buffer, 0, read)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            }
        }
        val items = NetworkPlaylistParser.parse(content, entry.uri, MAX_PLAYLIST_ITEMS)
        if (items.isEmpty()) error("The network playlist contains no supported media URLs")
        return items.map { playlistItem ->
            val media = prepareDirect(playlistItem.uri, playlistItem.title.orEmpty(), credential)
            requestRegistry.registerUri(media.uri, location, credential)
            media.copy(sourceId = location.id)
        }
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
        val media = AppMedia(
            stableId = StableMediaIdentity.forNetwork(canonical),
            uri = url,
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
        private const val MAX_PLAYLIST_BYTES = 2 * 1024 * 1024
        private const val MAX_PLAYLIST_ITEMS = 1_000

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

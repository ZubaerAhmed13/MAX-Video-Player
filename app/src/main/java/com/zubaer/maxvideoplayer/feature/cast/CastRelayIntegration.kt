package com.zubaer.maxvideoplayer.feature.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.google.android.gms.cast.MediaQueueItem
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudPlaybackRegistry
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkDataSourceRouter
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkRequestRegistry
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the short-lived LAN relay used only while a Cast receiver needs phone-mediated bytes.
 * URLs are process/session scoped and are never persisted. The same Step-7/Step-8 DataSource
 * router is used for local SAF, SMB, FTP/FTPS, WebDAV/HTTP and cloud sources.
 */
@UnstableApi
class CastRelayManager(
    context: Context,
    networkRequestRegistry: NetworkRequestRegistry,
    cloudPlaybackRegistry: CloudPlaybackRegistry,
) : Closeable {
    private val appContext = context.applicationContext
    private val dataSourceFactory: DataSource.Factory = NetworkDataSourceRouter.Factory(
        appContext,
        networkRequestRegistry,
        cloudPlaybackRegistry,
    )
    private val endpoints = ConcurrentHashMap<String, CastRelayEndpoint>()
    private var server: CastRelayServer? = null

    @Synchronized
    fun relayMedia(mediaItem: MediaItem): Uri {
        val local = requireNotNull(mediaItem.localConfiguration) { "Cast media item has no source." }
        val key = "media:${mediaItem.mediaId}:${local.uri}"
        endpoints[key]?.let { return Uri.parse(it.uri.toString()) }
        val resource = DataSourceCastRelayResource(
            dataSourceFactory = dataSourceFactory,
            sourceUri = local.uri,
            mimeType = local.mimeType,
        )
        val endpoint = register(resource)
        endpoints[key] = endpoint
        return Uri.parse(endpoint.uri.toString())
    }

    @Synchronized
    fun relaySubtitle(configuration: MediaItem.SubtitleConfiguration): MediaItem.SubtitleConfiguration {
        val key = "subtitle:${configuration.uri}:${configuration.mimeType}:${configuration.language}"
        val existing = endpoints[key]
        val endpoint = existing ?: register(
            CastSubtitleRelayResource(
                dataSourceFactory = dataSourceFactory,
                sourceUri = configuration.uri,
                sourceMimeType = configuration.mimeType,
            ),
        ).also { endpoints[key] = it }
        return configuration.buildUpon()
            .setUri(Uri.parse(endpoint.uri.toString()))
            .setMimeType(CastSubtitleRelayResource.castMimeType(configuration.mimeType, configuration.uri))
            .build()
    }

    @Synchronized
    fun stopSession() {
        endpoints.clear()
        server?.close()
        server = null
    }

    override fun close() = stopSession()

    @Synchronized
    private fun register(resource: CastRelayResource): CastRelayEndpoint {
        val active = server
        if (active != null) return active.register(resource)
        val bindAddress = LanAddressResolver.resolve(appContext)
            ?: throw IOException("No receiver-reachable LAN interface is available for Cast relay.")
        val created = CastRelayServer(bindAddress = bindAddress, maxResources = MAX_SESSION_RESOURCES)
        server = created
        return created.start(resource)
    }

    private companion object {
        const val MAX_SESSION_RESOURCES = 256
    }
}

/**
 * Converts app-private/local MediaItems into receiver-safe queue items while preserving the
 * original MediaItems for transfer back to the local player. No credential is copied into Cast
 * metadata or a Cast URL.
 */
@UnstableApi
class SecureCastMediaItemConverter(
    private val relayManager: CastRelayManager,
    private val networkRequestRegistry: NetworkRequestRegistry,
    private val resolver: CastSourceResolver = CastSourceResolver(),
) : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()
    private val originals = ConcurrentHashMap<String, MediaItem>()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val local = requireNotNull(mediaItem.localConfiguration) { "MediaItem is not playable." }
        originals[mediaItem.mediaId] = mediaItem
        val uri = local.uri
        val uriString = uri.toString()
        val privateHeaders = uri.scheme.equals("maxcloud", true) ||
            networkRequestRegistry.resolve(uriString)?.requestHeaders()?.isNotEmpty() == true
        val adaptive = isAdaptive(local.mimeType, uri)
        val receiverReachable = isReceiverReachableHttp(uri)
        val decision = resolver.resolve(uriString, adaptive, privateHeaders, receiverReachable)

        val castItem = when (decision.mode) {
            CastSourceMode.DIRECT_CAST -> withCastSafeSubtitles(mediaItem)
            CastSourceMode.LOCAL_RELAY -> mediaItem.buildUpon()
                .setUri(relayManager.relayMedia(mediaItem))
                .setSubtitleConfigurations(castSafeSubtitles(local.subtitleConfigurations))
                .build()
            CastSourceMode.MANIFEST_RELAY -> throw IllegalArgumentException(
                "Authenticated adaptive Cast source requires the manifest relay and cannot be exposed directly.",
            )
            CastSourceMode.UNSUPPORTED_CAST -> throw IllegalArgumentException(decision.reason)
        }
        return delegate.toMediaQueueItem(castItem)
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val converted = delegate.toMediaItem(mediaQueueItem)
        return originals[converted.mediaId] ?: converted
    }

    fun clearOriginalMappings() {
        originals.clear()
    }

    private fun withCastSafeSubtitles(mediaItem: MediaItem): MediaItem {
        val local = mediaItem.localConfiguration ?: return mediaItem
        val subtitles = castSafeSubtitles(local.subtitleConfigurations)
        return if (subtitles == local.subtitleConfigurations) mediaItem
        else mediaItem.buildUpon().setSubtitleConfigurations(subtitles).build()
    }

    private fun castSafeSubtitles(
        configurations: List<MediaItem.SubtitleConfiguration>,
    ): List<MediaItem.SubtitleConfiguration> = configurations.map { subtitle ->
        val scheme = subtitle.uri.scheme?.lowercase()
        val mime = subtitle.mimeType?.lowercase()
        val needsConversion = mime in CONVERT_TO_VTT_MIMES ||
            subtitle.uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in CONVERT_TO_VTT_EXTENSIONS
        val privateHeaders = scheme == "maxcloud" ||
            networkRequestRegistry.resolve(subtitle.uri.toString())?.requestHeaders()?.isNotEmpty() == true
        val direct = scheme in setOf("http", "https") && !privateHeaders && !needsConversion
        if (direct) subtitle else relayManager.relaySubtitle(subtitle)
    }

    private fun isAdaptive(mimeType: String?, uri: Uri): Boolean {
        val mime = mimeType?.lowercase()
        val path = uri.path.orEmpty().lowercase()
        return mime == MimeTypes.APPLICATION_M3U8 || mime == MimeTypes.APPLICATION_MPD ||
            path.endsWith(".m3u8") || path.endsWith(".mpd")
    }

    private fun isReceiverReachableHttp(uri: Uri): Boolean {
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host == "0.0.0.0" || host == "::" || host == "::1") return false
        return runCatching {
            InetAddress.getAllByName(host).none { it.isLoopbackAddress || it.isAnyLocalAddress }
        }.getOrDefault(true)
    }

    private companion object {
        val CONVERT_TO_VTT_EXTENSIONS = setOf("srt", "ass", "ssa")
        val CONVERT_TO_VTT_MIMES = setOf(
            "application/x-subrip",
            "text/srt",
            "text/x-ssa",
            "text/x-ass",
            "application/x-ass",
            "application/x-ssa",
        )
    }
}

/** DataSource-backed relay resource. Opening a byte range starts the real source at that Long offset. */
@UnstableApi
class DataSourceCastRelayResource(
    private val dataSourceFactory: DataSource.Factory,
    private val sourceUri: Uri,
    override val mimeType: String?,
    override val seekable: Boolean = true,
) : CastRelayResource {
    @Volatile
    private var cachedLength: Long? = null

    override val length: Long?
        get() = cachedLength ?: probeLength()?.also { cachedLength = it }

    override fun open(position: Long, length: Long?): InputStream {
        require(position >= 0L)
        require(length == null || length >= 0L)
        val source = dataSourceFactory.createDataSource()
        val builder = DataSpec.Builder().setUri(sourceUri).setPosition(position)
        if (length != null) builder.setLength(length)
        val resolved = try {
            source.open(builder.build())
        } catch (error: Throwable) {
            runCatching { source.close() }
            throw error
        }
        if (resolved >= 0L && position <= Long.MAX_VALUE - resolved) {
            val candidateTotal = position + resolved
            val current = cachedLength
            if (current == null || candidateTotal > current) cachedLength = candidateTotal
        }
        return DataSourceInputStream(source)
    }

    private fun probeLength(): Long? {
        val source = dataSourceFactory.createDataSource()
        return try {
            source.open(DataSpec.Builder().setUri(sourceUri).build()).takeIf { it >= 0L }
        } finally {
            runCatching { source.close() }
        }
    }
}

@UnstableApi
private class DataSourceInputStream(
    private val source: DataSource,
) : InputStream() {
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

/**
 * External subtitle relay. Subtitle files are intentionally bounded and may be normalized to
 * WebVTT because Cast receivers do not preserve advanced ASS/SSA styling.
 */
@UnstableApi
private class CastSubtitleRelayResource(
    private val dataSourceFactory: DataSource.Factory,
    private val sourceUri: Uri,
    private val sourceMimeType: String?,
) : CastRelayResource {
    @Volatile
    private var cached: ByteArray? = null

    override val length: Long get() = bytes().size.toLong()
    override val mimeType: String get() = castMimeType(sourceMimeType, sourceUri)
    override val seekable: Boolean = true

    override fun open(position: Long, length: Long?): InputStream {
        val data = bytes()
        if (position >= data.size.toLong()) return ByteArrayInputStream(ByteArray(0))
        val start = position.toInt()
        val requested = length ?: (data.size - start).toLong()
        val count = minOf(requested, (data.size - start).toLong()).toInt()
        return ByteArrayInputStream(data, start, count)
    }

    private fun bytes(): ByteArray {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val raw = readBounded()
            val result = when {
                shouldConvert(sourceMimeType, sourceUri) -> toWebVtt(raw.toString(Charsets.UTF_8))
                    .toByteArray(Charsets.UTF_8)
                else -> raw
            }
            cached = result
            return result
        }
    }

    private fun readBounded(): ByteArray {
        val source = dataSourceFactory.createDataSource()
        val length = try {
            source.open(DataSpec.Builder().setUri(sourceUri).build())
        } catch (error: Throwable) {
            runCatching { source.close() }
            throw error
        }
        if (length > MAX_SUBTITLE_BYTES) {
            source.close()
            throw IOException("Cast subtitle exceeds the 16 MB safety limit.")
        }
        val output = java.io.ByteArrayOutputStream(
            if (length in 1..MAX_SUBTITLE_BYTES) length.toInt() else 4096,
        )
        val buffer = ByteArray(16 * 1024)
        try {
            var total = 0L
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) continue
                total += read.toLong()
                if (total > MAX_SUBTITLE_BYTES) throw IOException("Cast subtitle exceeds the 16 MB safety limit.")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        } finally {
            source.close()
        }
    }

    private fun toWebVtt(text: String): String {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.trimStart().startsWith("WEBVTT")) return normalized
        if (normalized.lineSequence().any { it.trimStart().startsWith("Dialogue:", ignoreCase = true) }) {
            return assToWebVtt(normalized)
        }
        val body = normalized.lineSequence().joinToString("\n") { line ->
            if ("-->" in line) line.replace(Regex("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})"), "$1.$2") else line
        }
        return "WEBVTT\n\n$body\n"
    }

    private fun assToWebVtt(text: String): String {
        val cues = buildString {
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (!line.startsWith("Dialogue:", ignoreCase = true)) return@forEach
                val fields = line.substringAfter(':').trim().split(',', limit = 10)
                if (fields.size < 10) return@forEach
                val start = assTime(fields[1]) ?: return@forEach
                val end = assTime(fields[2]) ?: return@forEach
                val cueText = fields[9]
                    .replace(Regex("\\{[^}]*}"), "")
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace("\\h", " ")
                    .trim()
                if (cueText.isBlank()) return@forEach
                append(start).append(" --> ").append(end).append('\n')
                append(cueText).append("\n\n")
            }
        }
        return "WEBVTT\n\n$cues"
    }

    private fun assTime(value: String): String? {
        val match = Regex("(\\d+):(\\d{2}):(\\d{2})[.](\\d{1,2})").matchEntire(value.trim()) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toIntOrNull() ?: return null
        val seconds = match.groupValues[3].toIntOrNull() ?: return null
        val centiseconds = match.groupValues[4].padEnd(2, '0').take(2).toIntOrNull() ?: return null
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, centiseconds * 10)
    }

    companion object {
        private const val MAX_SUBTITLE_BYTES = 16L * 1024L * 1024L
        private val CONVERT_EXTENSIONS = setOf("srt", "ass", "ssa")
        private val CONVERT_MIMES = setOf(
            "application/x-subrip",
            "text/srt",
            "text/x-ssa",
            "text/x-ass",
            "application/x-ass",
            "application/x-ssa",
        )

        fun castMimeType(sourceMimeType: String?, uri: Uri): String =
            if (shouldConvert(sourceMimeType, uri)) MimeTypes.TEXT_VTT
            else sourceMimeType ?: MimeTypes.TEXT_VTT

        private fun shouldConvert(sourceMimeType: String?, uri: Uri): Boolean =
            sourceMimeType?.lowercase() in CONVERT_MIMES ||
                uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in CONVERT_EXTENSIONS
    }
}

private object LanAddressResolver {
    fun resolve(context: Context): InetAddress? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val active = connectivity?.activeNetwork
        val activeAddresses = active?.let(connectivity::getLinkProperties)?.linkAddresses
            ?.map { it.address }
            .orEmpty()
            .filter(::usable)
        activeAddresses.firstOrNull { it is Inet4Address }?.let { return it }
        activeAddresses.firstOrNull()?.let { return it }

        val fallback = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filter(::usable)
        }.getOrDefault(emptyList())
        return fallback.firstOrNull { it is Inet4Address } ?: fallback.firstOrNull()
    }

    private fun usable(address: InetAddress): Boolean =
        !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isMulticastAddress && !address.isLinkLocalAddress
}

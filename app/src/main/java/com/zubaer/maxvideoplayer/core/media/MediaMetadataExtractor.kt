package com.zubaer.maxvideoplayer.core.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaMetadataExtractor(private val context: Context) {
    suspend fun fromUri(uri: Uri, sourceType: MediaSourceType, forcedTitle: String? = null): AppMedia = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var title = forcedTitle ?: queryDisplayName(uri) ?: uri.lastPathSegment ?: "Video"
        var mime = resolver.getType(uri)
        var durationMs: Long? = null
        var width: Int? = null
        var height: Int? = null
        var rotation: Int? = null
        var frameRate: Float? = null
        var videoCodec: String? = null
        var audioCodec: String? = null
        var sampleRate: Int? = null
        var channels: Int? = null
        var colorStandard: Int? = null
        var colorRange: Int? = null
        var colorTransfer: Int? = null
        val size = querySize(uri)

        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { mime = it }
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
            }
        }

        runCatching {
            MediaExtractor().use { extractor ->
                extractor.setDataSource(context, uri, null)
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val trackMime = format.getString(MediaFormat.KEY_MIME)
                    when {
                        trackMime?.startsWith("video/") == true && videoCodec == null -> {
                            videoCodec = trackMime
                            width = width ?: format.intOrNull(MediaFormat.KEY_WIDTH)
                            height = height ?: format.intOrNull(MediaFormat.KEY_HEIGHT)
                            frameRate = format.floatOrNull(MediaFormat.KEY_FRAME_RATE)
                            colorStandard = format.intOrNull(MediaFormat.KEY_COLOR_STANDARD)
                            colorRange = format.intOrNull(MediaFormat.KEY_COLOR_RANGE)
                            colorTransfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
                        }
                        trackMime?.startsWith("audio/") == true && audioCodec == null -> {
                            audioCodec = trackMime
                            sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
                            channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }
        }

        val provisional = AppMedia(
            stableId = "",
            uri = uri.toString(),
            title = title,
            mimeType = mime,
            durationMs = durationMs,
            sizeBytes = size,
            width = width,
            height = height,
            rotationDegrees = rotation,
            frameRate = frameRate,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            audioSampleRate = sampleRate,
            audioChannelCount = channels,
            colorStandard = colorStandard,
            colorRange = colorRange,
            colorTransfer = colorTransfer,
            sourceType = sourceType,
        )
        provisional.copy(stableId = StableMediaIdentity.stableId(resolver, provisional))
    }

    fun fromNetworkUrl(url: String): AppMedia = AppMedia(
        stableId = StableMediaIdentity.forNetwork(url),
        uri = url,
        title = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() } ?: url,
        sourceType = MediaSourceType.NETWORK,
    )

    private fun queryDisplayName(uri: Uri): String? = queryString(uri, OpenableColumns.DISPLAY_NAME)
    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }.getOrNull()

    private fun queryString(uri: Uri, column: String): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
    private fun MediaFormat.floatOrNull(key: String): Float? = if (containsKey(key)) runCatching { getFloat(key) }.getOrNull() else null
}

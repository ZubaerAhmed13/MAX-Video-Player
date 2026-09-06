package com.zubaer.maxvideoplayer.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.zubaer.maxvideoplayer.core.model.AppMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ThumbnailRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val maxCacheBytes = 16 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    suspend fun load(media: AppMedia, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        val bounded = ThumbnailRequestPolicy.bound(width, height)
        val key = ThumbnailRequestPolicy.cacheKey(media.stableId, bounded.width, bounded.height)
        cache.get(key)?.let { return@withContext it }
        coroutineContext.ensureActive()

        val uri = runCatching { Uri.parse(media.uri) }.getOrNull() ?: return@withContext null
        if (uri.scheme != ContentResolverScheme.CONTENT) return@withContext null

        val bitmap = when (ThumbnailCompatibilityPolicy.strategy(Build.VERSION.SDK_INT)) {
            ThumbnailLoadStrategy.CONTENT_RESOLVER -> runCatching {
                resolver.loadThumbnail(uri, Size(bounded.width, bounded.height), null)
            }.getOrNull()

            ThumbnailLoadStrategy.SCALED_RETRIEVER -> runCatching {
                loadRetrieverThumbnail(uri, bounded.width, bounded.height, preferPlatformScaling = true)
            }.getOrNull()

            ThumbnailLoadStrategy.LEGACY_RETRIEVER -> runCatching {
                loadRetrieverThumbnail(uri, bounded.width, bounded.height, preferPlatformScaling = false)
            }.getOrNull()
        }

        coroutineContext.ensureActive()
        if (bitmap != null) cache.put(key, bitmap)
        bitmap
    }

    private fun loadRetrieverThumbnail(
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int,
        preferPlatformScaling: Boolean,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val frame = if (preferPlatformScaling && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    -1L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    maxWidth,
                    maxHeight,
                )
            } else {
                retriever.getFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: return null
            fitWithinBounds(frame, maxWidth, maxHeight)
        } finally {
            retriever.release()
        }
    }

    private fun fitWithinBounds(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (source.width <= maxWidth && source.height <= maxHeight) return source
        val scale = minOf(
            maxWidth.toFloat() / source.width.toFloat(),
            maxHeight.toFloat() / source.height.toFloat(),
        )
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        if (scaled !== source) source.recycle()
        return scaled
    }

    fun invalidate(stableMediaId: String) {
        val snapshot = cache.snapshot().keys.filter { it.startsWith("$stableMediaId:") }
        snapshot.forEach(cache::remove)
    }

    fun clear() = cache.evictAll()

    private object ContentResolverScheme {
        const val CONTENT = "content"
    }
}

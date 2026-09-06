package com.zubaer.maxvideoplayer.feature.library

import android.content.Context
import android.graphics.Bitmap
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
    private val resolver = context.applicationContext.contentResolver
    private val maxCacheBytes = 16 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    suspend fun load(media: AppMedia, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        val boundedWidth = width.coerceIn(64, 640)
        val boundedHeight = height.coerceIn(36, 480)
        val key = "${media.stableId}:$boundedWidth:$boundedHeight"
        cache.get(key)?.let { return@withContext it }
        coroutineContext.ensureActive()
        if (Build.VERSION.SDK_INT < 29) return@withContext null
        val uri = runCatching { Uri.parse(media.uri) }.getOrNull() ?: return@withContext null
        if (uri.scheme != "content") return@withContext null
        val bitmap = runCatching { resolver.loadThumbnail(uri, Size(boundedWidth, boundedHeight), null) }.getOrNull()
        coroutineContext.ensureActive()
        if (bitmap != null) cache.put(key, bitmap)
        bitmap
    }

    fun invalidate(stableMediaId: String) {
        val snapshot = cache.snapshot().keys.filter { it.startsWith("$stableMediaId:") }
        snapshot.forEach(cache::remove)
    }

    fun clear() = cache.evictAll()
}

package com.zubaer.maxvideoplayer.core.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val context: Context) {
    suspend fun videos(): List<AppMedia> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )
        val result = mutableListOf<AppMedia>()
        resolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                val title = cursor.getString(nameIndex) ?: "Video"
                val duration = cursor.longOrNull(durationIndex)
                val size = cursor.longOrNull(sizeIndex)
                val provisional = AppMedia(
                    stableId = "",
                    uri = uri.toString(),
                    title = title,
                    mimeType = cursor.stringOrNull(mimeIndex),
                    durationMs = duration,
                    sizeBytes = size,
                    width = cursor.intOrNull(widthIndex),
                    height = cursor.intOrNull(heightIndex),
                    sourceType = MediaSourceType.MEDIA_STORE,
                )
                result += provisional.copy(
                    stableId = StableMediaIdentity.fallbackKey(provisional.uri, size, duration, title)
                )
            }
        }
        result
    }

    private fun android.database.Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
    private fun android.database.Cursor.intOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)
    private fun android.database.Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
}

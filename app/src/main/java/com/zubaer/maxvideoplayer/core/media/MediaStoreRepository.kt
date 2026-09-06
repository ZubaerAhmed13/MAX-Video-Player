package com.zubaer.maxvideoplayer.core.media

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val context: Context) {
    companion object {
        const val SOURCE_ID = "mediastore:videos"
    }

    suspend fun videos(): List<AppMedia> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.TITLE)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.BUCKET_ID)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Video.Media.RELATIVE_PATH)
        }.toTypedArray()

        val result = ArrayList<AppMedia>()
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val bucketIdIndex = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameIndex = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val relativePathIndex = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(collection, id)
                val fileName = cursor.stringOrNull(nameIndex) ?: "Video"
                val title = cursor.stringOrNull(titleIndex)?.takeIf { it.isNotBlank() } ?: fileName
                val duration = cursor.longOrNull(durationIndex)
                val size = cursor.longOrNull(sizeIndex)
                val relativePath = cursor.stringOrNull(relativePathIndex)
                val bucketId = cursor.stringOrNull(bucketIdIndex)
                val bucketName = cursor.stringOrNull(bucketNameIndex)
                val folderKey = when {
                    !bucketId.isNullOrBlank() -> "mediastore:bucket:$bucketId"
                    !relativePath.isNullOrBlank() -> "mediastore:path:$relativePath"
                    else -> "mediastore:root"
                }
                val folderName = bucketName
                    ?: relativePath?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: "Root"

                val provisional = AppMedia(
                    stableId = "",
                    uri = uri.toString(),
                    title = title,
                    fileName = fileName,
                    mimeType = cursor.stringOrNull(mimeIndex),
                    durationMs = duration,
                    sizeBytes = size,
                    width = cursor.intOrNull(widthIndex),
                    height = cursor.intOrNull(heightIndex),
                    dateAddedMs = cursor.longOrNull(dateAddedIndex)?.times(1_000L),
                    dateModifiedMs = cursor.longOrNull(dateModifiedIndex)?.times(1_000L),
                    relativePath = relativePath,
                    folderKey = folderKey,
                    folderName = folderName,
                    sourceId = SOURCE_ID,
                    sourceType = MediaSourceType.MEDIA_STORE,
                )
                result += provisional.copy(
                    // Preserve the Step-1 identity algorithm so existing history/resume rows remain linked.
                    stableId = StableMediaIdentity.fallbackKey(provisional.uri, size, duration, fileName),
                )
            }
        }
        result
    }

    private fun android.database.Cursor.longOrNull(index: Int): Long? = if (index < 0 || isNull(index)) null else getLong(index)
    private fun android.database.Cursor.intOrNull(index: Int): Int? = if (index < 0 || isNull(index)) null else getInt(index)
    private fun android.database.Cursor.stringOrNull(index: Int): String? = if (index < 0 || isNull(index)) null else getString(index)
}

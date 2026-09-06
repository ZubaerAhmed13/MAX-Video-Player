package com.zubaer.maxvideoplayer.core.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Provider-aware SAF tree scanner. It never assumes a filesystem path exists and never reads whole files. */
class SafTreeScanner(private val resolver: ContentResolver) {
    suspend fun scan(treeUri: Uri, sourceId: String, rootDisplayName: String): List<AppMedia> = withContext(Dispatchers.IO) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<FolderNode>()
        pending.add(FolderNode(rootId, rootDisplayName))
        val result = ArrayList<AppMedia>()

        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val parent = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId)
            resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val documentId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex) ?: documentId
                    val mime = cursor.getString(mimeIndex)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(FolderNode(documentId, name))
                        continue
                    }
                    if (!isVideo(mime, name)) continue

                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val size = cursor.longOrNull(sizeIndex)
                    val folderKey = "saf:$sourceId:${parent.documentId}"
                    result += AppMedia(
                        stableId = StableMediaIdentity.fallbackKey(documentUri.toString(), size, null, name),
                        uri = documentUri.toString(),
                        title = name.substringBeforeLast('.', name),
                        fileName = name,
                        mimeType = mime,
                        sizeBytes = size,
                        dateModifiedMs = cursor.longOrNull(modifiedIndex),
                        folderKey = folderKey,
                        folderName = parent.displayName,
                        sourceId = sourceId,
                        sourceType = MediaSourceType.SAF,
                    )
                }
            }
        }
        result
    }

    suspend fun displayName(treeUri: Uri): String = withContext(Dispatchers.IO) {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return@withContext "Folder"
        val documentUri = runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId) }.getOrNull() ?: return@withContext "Folder"
        resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Folder"
    }

    private fun isVideo(mimeType: String?, name: String): Boolean {
        if (mimeType?.startsWith("video/", ignoreCase = true) == true) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    private fun android.database.Cursor.longOrNull(index: Int): Long? = if (index < 0 || isNull(index)) null else getLong(index)

    private data class FolderNode(val documentId: String, val displayName: String)

    companion object {
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "m2ts", "mts", "flv", "wmv", "mpg", "mpeg")
    }
}

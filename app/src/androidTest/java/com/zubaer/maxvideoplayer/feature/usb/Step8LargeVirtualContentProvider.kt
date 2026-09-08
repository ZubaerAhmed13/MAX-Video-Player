package com.zubaer.maxvideoplayer.feature.usb

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile

/** Test-only provider that exposes a seekable >3 GB sparse file without allocating 3 GB. */
class Step8LargeVirtualContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "video/mp4"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = metadataCursor(projection)

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: android.os.Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor = metadataCursor(projection)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri == REMOVED_URI) {
            throw FileNotFoundException("Virtual removable source disconnected")
        }
        if (uri != URI) {
            throw FileNotFoundException("Unknown Step 8 virtual source")
        }
        if (mode != "r") {
            throw FileNotFoundException("Step 8 virtual source is read-only")
        }
        return ParcelFileDescriptor.open(ensureSparseFixture(), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun metadataCursor(projection: Array<out String>?): Cursor {
        val requested = projection?.toList().orEmpty()
        val columns = if (requested.isEmpty()) listOf("_display_name", "_size") else requested
        return MatrixCursor(columns.toTypedArray()).apply {
            val row = newRow()
            columns.forEach { column ->
                when (column) {
                    "_display_name" -> row.add("step8-usb-3gb.mp4")
                    "_size" -> row.add(VIRTUAL_LENGTH)
                    else -> row.add(null)
                }
            }
        }
    }

    @Synchronized
    private fun ensureSparseFixture(): File {
        val fixture = File(requireNotNull(context).cacheDir, "step8-usb-3gb-sparse.mp4")
        if (fixture.length() == VIRTUAL_LENGTH) return fixture

        RandomAccessFile(fixture, "rw").use { file ->
            file.setLength(VIRTUAL_LENGTH)
            file.seek(VERIFICATION_OFFSET)
            file.write(
                ByteArray(VERIFICATION_LENGTH) { index ->
                    ((VERIFICATION_OFFSET + index.toLong()) and 0xff).toByte()
                },
            )
        }
        check(fixture.length() == VIRTUAL_LENGTH) {
            "Sparse Step 8 fixture has wrong logical length: ${fixture.length()}"
        }
        return fixture
    }

    companion object {
        const val AUTHORITY = "com.zubaer.maxvideoplayer.step8virtual"
        const val VIRTUAL_LENGTH = 3_221_225_473L
        const val VERIFICATION_OFFSET = 2_147_483_648L + 33_333L
        const val VERIFICATION_LENGTH = 8192
        val URI: Uri get() = Uri.parse("content://$AUTHORITY/removable/step8-usb-3gb.mp4")
        val REMOVED_URI: Uri get() = Uri.parse("content://$AUTHORITY/removed/step8-usb-3gb.mp4")
    }
}

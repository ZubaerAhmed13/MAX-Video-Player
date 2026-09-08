package com.zubaer.maxvideoplayer.feature.usb

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import java.io.FileNotFoundException

/** Test-only provider that exposes a seekable >3 GB file without allocating the file. */
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
        if (!available) throw FileNotFoundException("Virtual removable source disconnected")
        if (Build.VERSION.SDK_INT < 26) throw FileNotFoundException("Proxy file descriptor requires API 26")
        val manager = requireNotNull(context).getSystemService(StorageManager::class.java)
        return manager.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            VirtualFileCallback,
            null,
        )
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

    @RequiresApi(26)
    private object VirtualFileCallback : ProxyFileDescriptorCallback() {
        override fun onGetSize(): Long = VIRTUAL_LENGTH

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            if (!available) throw android.system.ErrnoException("usb-disconnected", android.system.OsConstants.ENODEV)
            if (offset >= VIRTUAL_LENGTH) return 0
            val count = minOf(size.toLong(), VIRTUAL_LENGTH - offset).toInt()
            for (index in 0 until count) data[index] = ((offset + index.toLong()) and 0xff).toByte()
            return count
        }

        override fun onRelease() = Unit
    }

    companion object {
        const val AUTHORITY = "com.zubaer.maxvideoplayer.step8virtual"
        const val VIRTUAL_LENGTH = 3_221_225_473L
        @Volatile var available: Boolean = true
        val URI: Uri get() = Uri.parse("content://$AUTHORITY/removable/step8-usb-3gb.mp4")
    }
}

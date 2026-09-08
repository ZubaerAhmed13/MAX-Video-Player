package com.zubaer.maxvideoplayer.feature.network.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.File
import com.zubaer.maxvideoplayer.feature.network.protocol.smb.SmbHandle
import com.zubaer.maxvideoplayer.feature.network.protocol.smb.SmbProtocolClient
import java.io.IOException
import java.util.EnumSet

@UnstableApi
class SmbDataSource(
    private val registry: NetworkRequestRegistry,
    private val protocolClient: SmbProtocolClient,
) : BaseDataSource(true) {
    private var dataSpec: DataSpec? = null
    private var openedUri: Uri? = null
    private var handle: SmbHandle? = null
    private var remoteFile: File? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var opened = false
    private var reconnectCount = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        openedUri = dataSpec.uri
        position = dataSpec.position
        reconnectCount = 0
        openRemote()
        val size = remoteFile!!.getFileInformation(FileStandardInformation::class.java).endOfFile
        if (position > size) throw DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE)
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - position else minOf(dataSpec.length, size - position)
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val requested = minOf(length.toLong(), bytesRemaining, MAX_READ_BYTES.toLong()).toInt()
        while (true) {
            try {
                val read = remoteFile!!.read(buffer, position, offset, requested)
                if (read < 0) return C.RESULT_END_OF_INPUT
                position += read.toLong()
                bytesRemaining -= read.toLong()
                bytesTransferred(read)
                return read
            } catch (error: IOException) {
                if (reconnectCount >= MAX_RECONNECTS) throw error
                reconnectCount += 1
                closeRemote()
                Thread.sleep(250L shl (reconnectCount - 1))
                openRemote()
            }
        }
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        openedUri = null
        dataSpec = null
        closeRemote()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun openRemote() {
        val uri = openedUri ?: throw IOException("Missing SMB URI")
        val context = registry.resolve(uri.toString()) ?: throw IOException("SMB credentials need to be entered again")
        val location = context.location ?: throw IOException("SMB location is unavailable")
        val childPath = uri.pathSegments.joinToString("/")
        handle = protocolClient.connect(location, context.credential)
        remoteFile = handle!!.share.openFile(
            protocolClient.filePath(location, childPath),
            EnumSet.of(AccessMask.GENERIC_READ),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
        )
    }

    private fun closeRemote() {
        runCatching { remoteFile?.close() }
        remoteFile = null
        runCatching { handle?.close() }
        handle = null
    }

    private companion object {
        const val MAX_READ_BYTES = 1024 * 1024
        const val MAX_RECONNECTS = 3
    }
}

package com.zubaer.maxvideoplayer.feature.network.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.zubaer.maxvideoplayer.feature.network.protocol.ftp.FtpProtocolClient
import org.apache.commons.net.ftp.FTPClient
import java.io.IOException
import java.io.InputStream

@UnstableApi
class FtpDataSource(
    private val registry: NetworkRequestRegistry,
    private val protocolClient: FtpProtocolClient,
) : BaseDataSource(true) {
    private var dataSpec: DataSpec? = null
    private var openedUri: Uri? = null
    private var client: FTPClient? = null
    private var input: InputStream? = null
    private var remoteSize: Long? = null
    private var position = 0L
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var reconnectCount = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        openedUri = dataSpec.uri
        position = dataSpec.position
        reconnectCount = 0
        try {
            openRemote()
            val size = remoteSize
            if (size != null && position > size) throw DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE)
            bytesRemaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                size != null -> size - position
                else -> C.LENGTH_UNSET.toLong()
            }
        } catch (error: Throwable) {
            closeRemote()
            remoteSize = null
            throw error
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val requested = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), bytesRemaining).toInt()
        while (true) {
            try {
                val read = input!!.read(buffer, offset, requested)
                if (read < 0) return C.RESULT_END_OF_INPUT
                position += read.toLong()
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read.toLong()
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
        remoteSize = null
    }

    private fun openRemote() {
        val uri = openedUri ?: throw IOException("Missing FTP URI")
        val context = registry.resolve(uri.toString()) ?: throw IOException("FTP credentials need to be entered again")
        val location = context.location ?: throw IOException("FTP location is unavailable")
        val connected = protocolClient.createConnectedClient(location, context.credential)
        try {
            val observedSize = connected.mlistFile(uri.path.orEmpty())?.size?.takeIf { it >= 0L }
            if (remoteSize != null && observedSize != null && remoteSize != observedSize) {
                throw IOException("The remote FTP file changed during playback")
            }
            remoteSize = observedSize ?: remoteSize
            connected.restartOffset = position
            val stream = connected.retrieveFileStream(uri.path.orEmpty())
                ?: throw IOException("FTP server rejected the media range (${connected.replyCode})")
            client = connected
            input = stream
        } catch (error: Throwable) {
            protocolClient.closeClient(connected)
            throw error
        }
    }

    private fun closeRemote() {
        runCatching { input?.close() }
        input = null
        client?.let { connected ->
            runCatching { connected.completePendingCommand() }
            protocolClient.closeClient(connected)
        }
        client = null
    }

    private companion object {
        const val MAX_RECONNECTS = 3
    }
}

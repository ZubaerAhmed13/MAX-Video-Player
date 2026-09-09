package com.zubaer.maxvideoplayer.feature.network.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudDataSource
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudPlaybackRegistry
import com.zubaer.maxvideoplayer.feature.network.protocol.ftp.FtpProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.http.NetworkHttpClientFactory
import com.zubaer.maxvideoplayer.feature.network.protocol.smb.SmbProtocolClient
import com.zubaer.maxvideoplayer.feature.privatevault.datasource.EncryptedVaultDataSource
import com.zubaer.maxvideoplayer.feature.privatevault.datasource.PrivateVaultResolver

@UnstableApi
class NetworkDataSourceRouter private constructor(
    private val localFactory: DataSource.Factory,
    private val httpFactory: DataSource.Factory,
    private val registry: NetworkRequestRegistry,
    private val cloudRegistry: CloudPlaybackRegistry,
    private val smbClient: SmbProtocolClient,
    private val ftpClient: FtpProtocolClient,
    private val privateVaultResolver: PrivateVaultResolver?,
) : DataSource {
    class Factory(
        context: Context,
        private val registry: NetworkRequestRegistry,
        private val cloudRegistry: CloudPlaybackRegistry = CloudPlaybackRegistry(),
        private val privateVaultResolver: PrivateVaultResolver? = null,
    ) : DataSource.Factory {
        private val appContext = context.applicationContext
        private val http = OkHttpDataSource.Factory(NetworkHttpClientFactory.create(registry))
            .setUserAgent("MAXVideoPlayer/0.9 Android")
        private val local = DefaultDataSource.Factory(appContext, http)
        private val smb = SmbProtocolClient()
        private val ftp = FtpProtocolClient()

        override fun createDataSource(): DataSource = NetworkDataSourceRouter(
            local,
            http,
            registry,
            cloudRegistry,
            smb,
            ftp,
            privateVaultResolver,
        )
    }

    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(delegate == null) { "DataSource is already open" }
        val source = when (dataSpec.uri.scheme?.lowercase()) {
            "http", "https" -> httpFactory.createDataSource().let { http ->
                if (dataSpec.uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in SUBTITLE_EXTENSIONS) {
                    BoundedDataSource(http, MAX_NETWORK_SUBTITLE_BYTES)
                } else http
            }
            "maxsmb" -> SmbDataSource(registry, smbClient)
            "ftp", "ftps" -> FtpDataSource(registry, ftpClient)
            "maxcloud" -> CloudDataSource.Factory(cloudRegistry).createDataSource()
            "maxvault" -> EncryptedVaultDataSource.Factory(
                privateVaultResolver ?: throw java.io.IOException("Private Vault resolver is unavailable"),
            ).createDataSource()
            else -> localFactory.createDataSource()
        }
        listeners.forEach(source::addTransferListener)
        delegate = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(delegate) { "DataSource is not open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
        val source = delegate
        delegate = null
        source?.close()
    }

    private companion object {
        val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "ttml", "dfxp")
        const val MAX_NETWORK_SUBTITLE_BYTES = 16L * 1024L * 1024L
    }
}

@UnstableApi
private class BoundedDataSource(
    private val delegate: DataSource,
    private val maximumBytes: Long,
) : DataSource by delegate {
    private var bytesRead = 0L

    override fun open(dataSpec: DataSpec): Long {
        bytesRead = 0L
        val length = delegate.open(dataSpec)
        if (length > maximumBytes) {
            delegate.close()
            throw java.io.IOException("Remote subtitle exceeds the 16 MB safety limit")
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val remaining = maximumBytes - bytesRead
        if (remaining <= 0L) throw java.io.IOException("Remote subtitle exceeds the 16 MB safety limit")
        val read = delegate.read(buffer, offset, minOf(length.toLong(), remaining + 1L).toInt())
        if (read > 0) {
            bytesRead += read.toLong()
            if (bytesRead > maximumBytes) throw java.io.IOException("Remote subtitle exceeds the 16 MB safety limit")
        }
        return read
    }
}

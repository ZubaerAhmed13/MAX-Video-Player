package com.zubaer.maxvideoplayer.feature.cloud.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFailure
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudPlaybackResource
import com.zubaer.maxvideoplayer.feature.cloud.provider.CloudHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@UnstableApi
class CloudDataSource(
    private val registry: CloudPlaybackRegistry,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null
    private var resolvedUri: Uri? = null
    private var responseHeaders: Map<String, List<String>> = emptyMap()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(delegate == null) { "Cloud DataSource is already open." }
        val decoded = CloudUriCodec.decode(dataSpec.uri)
        val client = registry.client(decoded.identity)
            ?: throw CloudFailure.AuthenticationRequired("Connect this cloud account again to continue.")

        fun resolve(forceRefresh: Boolean): CloudPlaybackResource = runBlocking(Dispatchers.IO) {
            client.resolvePlayback(decoded.identity, forceRefresh)
        }.also { resource ->
            if (!decoded.expectedRevision.isNullOrBlank() && !resource.revision.isNullOrBlank() && decoded.expectedRevision != resource.revision) {
                throw CloudFailure.FileChanged()
            }
        }

        var resource = resolve(false)
        try {
            return openResolved(dataSpec, resource)
        } catch (error: HttpDataSource.InvalidResponseCodeException) {
            closeDelegate()
            if (error.responseCode != 401 && error.responseCode != 403) throw error
            resource = resolve(true)
            return openResolved(dataSpec, resource)
        }
    }

    private fun openResolved(original: DataSpec, resource: CloudPlaybackResource): Long {
        val factory = OkHttpDataSource.Factory(CloudHttp.client)
            .setUserAgent("MAXVideoPlayer/0.8 Android")
        val source = factory.createDataSource()
        listeners.forEach(source::addTransferListener)
        val headers = buildMap {
            putAll(original.httpRequestHeaders)
            putAll(resource.requestHeaders)
        }
        val spec = DataSpec.Builder()
            .setUri(resource.uri)
            .setPosition(original.position)
            .setLength(original.length)
            .setKey(original.key)
            .setFlags(original.flags)
            .setHttpRequestHeaders(headers)
            .build()
        delegate = source
        resolvedUri = resource.uri
        return source.open(spec).also {
            responseHeaders = source.responseHeaders
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(delegate) { "Cloud DataSource is not open." }.read(buffer, offset, length)

    override fun getUri(): Uri? = resolvedUri

    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders

    override fun close() {
        closeDelegate()
        resolvedUri = null
        responseHeaders = emptyMap()
    }

    private fun closeDelegate() {
        val source = delegate
        delegate = null
        runCatching { source?.close() }
    }

    class Factory(private val registry: CloudPlaybackRegistry) : DataSource.Factory {
        override fun createDataSource(): DataSource = CloudDataSource(registry)
    }
}

package com.zubaer.maxvideoplayer.feature.cloud.playback

import android.net.Uri
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFileIdentity
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudProvider
import com.zubaer.maxvideoplayer.feature.cloud.provider.CloudProviderClient
import java.util.concurrent.ConcurrentHashMap

class CloudPlaybackRegistry {
    private val clients = ConcurrentHashMap<Key, CloudProviderClient>()

    fun register(provider: CloudProvider, accountId: String, client: CloudProviderClient) {
        require(client.accountId == accountId)
        clients[Key(provider, accountId)] = client
    }

    fun unregister(provider: CloudProvider, accountId: String) {
        clients.remove(Key(provider, accountId))
    }

    fun client(identity: CloudFileIdentity): CloudProviderClient? = clients[Key(identity.provider, identity.accountId)]

    private data class Key(val provider: CloudProvider, val accountId: String)
}

object CloudUriCodec {
    private const val SCHEME = "maxcloud"

    fun encode(identity: CloudFileIdentity, expectedRevision: String? = null): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(identity.provider.name.lowercase())
        .appendQueryParameter("account", identity.accountId)
        .appendQueryParameter("file", identity.providerFileId)
        .apply {
            if (!identity.driveId.isNullOrBlank()) appendQueryParameter("drive", identity.driveId)
            if (!expectedRevision.isNullOrBlank()) appendQueryParameter("revision", expectedRevision)
        }
        .build()

    fun decode(uri: Uri): Decoded {
        require(uri.scheme.equals(SCHEME, ignoreCase = true)) { "Not a cloud playback URI." }
        val provider = CloudProvider.valueOf(requireNotNull(uri.authority).uppercase())
        val account = requireNotNull(uri.getQueryParameter("account")) { "Cloud URI missing account." }
        val file = requireNotNull(uri.getQueryParameter("file")) { "Cloud URI missing file." }
        return Decoded(
            identity = CloudFileIdentity(provider, account, file, uri.getQueryParameter("drive")),
            expectedRevision = uri.getQueryParameter("revision"),
        )
    }

    data class Decoded(val identity: CloudFileIdentity, val expectedRevision: String?)
}

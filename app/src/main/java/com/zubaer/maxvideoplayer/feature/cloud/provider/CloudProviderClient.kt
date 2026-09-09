package com.zubaer.maxvideoplayer.feature.cloud.provider

import com.zubaer.maxvideoplayer.feature.cloud.model.CloudEntry
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFileIdentity
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudPage
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudPlaybackResource

interface CloudAccessTokenProvider {
    /** Returns a currently usable bearer token, refreshing through the provider auth layer if needed. */
    suspend fun accessToken(forceRefresh: Boolean = false): String
}

interface CloudProviderClient {
    val accountId: String

    suspend fun browse(parentId: String? = null, pageToken: String? = null, pageSize: Int = 100): CloudPage

    suspend fun search(query: String, pageToken: String? = null, pageSize: Int = 100): CloudPage

    suspend fun get(identity: CloudFileIdentity): CloudEntry

    /**
     * Resolves a seekable streaming resource. The returned URL may be short-lived; callers must
     * retain [CloudFileIdentity] as authoritative history/queue identity and re-resolve on expiry.
     */
    suspend fun resolvePlayback(identity: CloudFileIdentity, forceRefresh: Boolean = false): CloudPlaybackResource
}

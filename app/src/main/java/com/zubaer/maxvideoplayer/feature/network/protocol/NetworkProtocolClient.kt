package com.zubaer.maxvideoplayer.feature.network.protocol

import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntry
import com.zubaer.maxvideoplayer.feature.network.model.NetworkFailure
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation

data class ConnectionTestResult(
    val connected: Boolean,
    val message: String,
    val failure: NetworkFailure? = null,
)

interface NetworkProtocolClient {
    suspend fun testConnection(location: NetworkLocation, credential: NetworkCredential?): ConnectionTestResult
    suspend fun list(location: NetworkLocation, credential: NetworkCredential?, remotePath: String): List<NetworkEntry>
    suspend fun stat(location: NetworkLocation, credential: NetworkCredential?, remotePath: String): NetworkEntry?
    fun playbackUri(location: NetworkLocation, remotePath: String): String
}

class NetworkProtocolException(
    val failure: NetworkFailure,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

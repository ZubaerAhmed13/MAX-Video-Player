package com.zubaer.maxvideoplayer.feature.network.protocol.smb

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntry
import com.zubaer.maxvideoplayer.feature.network.model.NetworkFailure
import com.zubaer.maxvideoplayer.feature.network.model.NetworkFilePolicy
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
import com.zubaer.maxvideoplayer.feature.network.protocol.ConnectionTestResult
import com.zubaer.maxvideoplayer.feature.network.protocol.NetworkProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.NetworkProtocolException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SmbProtocolClient : NetworkProtocolClient {
    override suspend fun testConnection(location: NetworkLocation, credential: NetworkCredential?): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            runCatching {
                connect(location, credential).use { handle ->
                    if (!handle.share.folderExists(directoryPath(location, ""))) {
                        ConnectionTestResult(false, "Share path not found", NetworkFailure.PathNotFound)
                    } else ConnectionTestResult(true, "Connected")
                }
            }.getOrElse { error ->
                val protocol = error as? NetworkProtocolException
                ConnectionTestResult(false, protocol?.message ?: "SMB server unavailable", protocol?.failure ?: mapFailure(error))
            }
        }

    override suspend fun list(location: NetworkLocation, credential: NetworkCredential?, remotePath: String): List<NetworkEntry> =
        withContext(Dispatchers.IO) {
            connect(location, credential).use { handle ->
                handle.share.list(directoryPath(location, remotePath)).asSequence()
                    .filter { it.fileName != "." && it.fileName != ".." }
                    .map { item ->
                        val directory = item.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                        val child = NetworkUriPolicy.safeRemotePath(listOf(remotePath, item.fileName).filter { it.isNotBlank() }.joinToString("/"))
                        NetworkEntry(
                            name = item.fileName,
                            uri = playbackUri(location, child),
                            type = NetworkFilePolicy.type(item.fileName, directory),
                            sizeBytes = item.endOfFile.takeIf { !directory && it >= 0L },
                            modifiedAtMs = item.lastWriteTime?.toEpochMillis(),
                            mimeType = NetworkFilePolicy.mimeType(item.fileName),
                            sourceId = location.id,
                            remotePath = child,
                        )
                    }
                    .sortedWith(compareBy<NetworkEntry> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    .toList()
            }
        }

    override suspend fun stat(location: NetworkLocation, credential: NetworkCredential?, remotePath: String): NetworkEntry? =
        withContext(Dispatchers.IO) {
            val parent = remotePath.substringBeforeLast('/', "")
            val name = remotePath.substringAfterLast('/')
            list(location, credential, parent).firstOrNull { it.name == name }
        }

    override fun playbackUri(location: NetworkLocation, remotePath: String): String {
        val encoded = NetworkUriPolicy.safeRemotePath(remotePath).split('/').filter { it.isNotEmpty() }
            .joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }
        return "maxsmb://${location.id}/$encoded"
    }

    internal fun connect(location: NetworkLocation, credential: NetworkCredential?): SmbHandle {
        val shareName = shareName(location)
        if (shareName.isBlank()) throw NetworkProtocolException(NetworkFailure.PathNotFound, "An SMB share name is required")
        val config = SmbConfig.builder()
            .withDialects(
                SMB2Dialect.SMB_3_1_1,
                SMB2Dialect.SMB_3_0_2,
                SMB2Dialect.SMB_3_0,
                SMB2Dialect.SMB_2_1,
                SMB2Dialect.SMB_2_0_2,
            )
            .withSigningEnabled(true)
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build()
        val client = SMBClient(config)
        try {
            val connection = client.connect(location.host, location.port)
            val authentication = when {
                location.useGuest -> AuthenticationContext.guest()
                credential == null || credential.username.isBlank() -> AuthenticationContext.anonymous()
                else -> AuthenticationContext(
                    credential.username,
                    credential.password.toCharArray(),
                    credential.domain.takeIf(String::isNotBlank),
                )
            }
            val session = connection.authenticate(authentication)
            val share = session.connectShare(shareName) as? DiskShare
                ?: throw NetworkProtocolException(NetworkFailure.ProtocolUnsupported, "The selected SMB share is not a disk share")
            return SmbHandle(client, connection, session, share)
        } catch (error: Throwable) {
            runCatching { client.close() }
            if (error is NetworkProtocolException) throw error
            throw NetworkProtocolException(mapFailure(error), safeMessage(error), error)
        }
    }

    internal fun shareName(location: NetworkLocation): String =
        NetworkUriPolicy.safeRemotePath(location.basePath).substringBefore('/')

    internal fun filePath(location: NetworkLocation, remotePath: String): String =
        NetworkUriPolicy.safeRemotePath(
            listOf(NetworkUriPolicy.safeRemotePath(location.basePath).substringAfter('/', ""), remotePath)
                .filter { it.isNotBlank() }.joinToString("/"),
        ).replace('/', '\\')

    internal fun directoryPath(location: NetworkLocation, remotePath: String): String = filePath(location, remotePath)

    private fun mapFailure(error: Throwable): NetworkFailure {
        val message = error.message.orEmpty().lowercase()
        return when {
            "logon" in message || "authentication" in message || "status_logon_failure" in message -> NetworkFailure.AuthenticationFailed
            "access_denied" in message || "access denied" in message -> NetworkFailure.PermissionDenied
            "object_name_not_found" in message || "path_not_found" in message -> NetworkFailure.PathNotFound
            error is java.net.SocketTimeoutException -> NetworkFailure.ConnectionTimeout
            error is IOException -> NetworkFailure.ServerNotFound
            else -> NetworkFailure.Other(error.javaClass.simpleName)
        }
    }

    private fun safeMessage(error: Throwable): String = when (mapFailure(error)) {
        NetworkFailure.AuthenticationFailed -> "Authentication failed"
        NetworkFailure.PermissionDenied -> "Access denied"
        NetworkFailure.PathNotFound -> "Share or path not found"
        NetworkFailure.ConnectionTimeout -> "Connection timed out"
        else -> "SMB server unavailable"
    }
}

internal class SmbHandle(
    private val client: SMBClient,
    private val connection: Connection,
    private val session: Session,
    val share: DiskShare,
) : Closeable {
    override fun close() {
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }
}

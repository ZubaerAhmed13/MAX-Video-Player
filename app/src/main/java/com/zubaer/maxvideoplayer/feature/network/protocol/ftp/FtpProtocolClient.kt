package com.zubaer.maxvideoplayer.feature.network.protocol.ftp

import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntry
import com.zubaer.maxvideoplayer.feature.network.model.NetworkFailure
import com.zubaer.maxvideoplayer.feature.network.model.NetworkFilePolicy
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
import com.zubaer.maxvideoplayer.feature.network.protocol.ConnectionTestResult
import com.zubaer.maxvideoplayer.feature.network.protocol.NetworkProtocolClient
import com.zubaer.maxvideoplayer.feature.network.protocol.NetworkProtocolException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder

class FtpProtocolClient : NetworkProtocolClient {
    override suspend fun testConnection(location: NetworkLocation, credential: NetworkCredential?): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            runCatching {
                withClient(location, credential) { client ->
                    if (!client.changeWorkingDirectory(remotePath(location, ""))) {
                        throw NetworkProtocolException(NetworkFailure.PathNotFound, "Server path not found")
                    }
                    ConnectionTestResult(true, "Connected")
                }
            }.getOrElse { error ->
                val protocol = error as? NetworkProtocolException
                when (error) {
                    is SocketTimeoutException -> ConnectionTestResult(false, "Connection timed out", NetworkFailure.ConnectionTimeout)
                    else -> ConnectionTestResult(false, protocol?.message ?: "FTP server unavailable", protocol?.failure ?: NetworkFailure.ServerNotFound)
                }
            }
        }

    override suspend fun list(location: NetworkLocation, credential: NetworkCredential?, remotePath: String): List<NetworkEntry> =
        withContext(Dispatchers.IO) {
            withClient(location, credential) { client ->
                client.listFiles(remotePath(location, remotePath)).asSequence()
                    .filter { it.name != "." && it.name != ".." }
                    .map { file ->
                        val child = NetworkUriPolicy.safeRemotePath(listOf(remotePath, file.name).filter { it.isNotBlank() }.joinToString("/"))
                        NetworkEntry(
                            name = file.name,
                            uri = playbackUri(location, child),
                            type = NetworkFilePolicy.type(file.name, file.isDirectory),
                            sizeBytes = file.size.takeIf { it >= 0L },
                            modifiedAtMs = runCatching { file.timestamp.timeInMillis }.getOrNull(),
                            mimeType = NetworkFilePolicy.mimeType(file.name),
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
            withClient(location, credential) { client ->
                val file = client.mlistFile(remotePath(location, remotePath)) ?: return@withClient null
                NetworkEntry(
                    name = file.name.ifBlank { remotePath.substringAfterLast('/') },
                    uri = playbackUri(location, remotePath),
                    type = NetworkFilePolicy.type(file.name, file.isDirectory),
                    sizeBytes = file.size.takeIf { it >= 0L },
                    modifiedAtMs = runCatching { file.timestamp.timeInMillis }.getOrNull(),
                    mimeType = NetworkFilePolicy.mimeType(file.name),
                    sourceId = location.id,
                    remotePath = remotePath,
                )
            }
        }

    override fun playbackUri(location: NetworkLocation, remotePath: String): String {
        val scheme = if (location.protocol == NetworkProtocol.FTPS) "ftps" else "ftp"
        val defaultPort = if (scheme == "ftp") 21 else 21
        val authority = if (location.port == defaultPort) location.host else "${location.host}:${location.port}"
        val encodedPath = remotePath(location, remotePath).trimStart('/').split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
        }
        return "$scheme://$authority/$encodedPath"
    }

    internal fun createConnectedClient(location: NetworkLocation, credential: NetworkCredential?): FTPClient {
        val client: FTPClient = if (location.protocol == NetworkProtocol.FTPS) {
            FTPSClient(false).apply { setEndpointCheckingEnabled(true) }
        } else FTPClient()
        // FTP's historical default control encoding is ISO-8859-1. Modern servers advertise and
        // use UTF-8, but relying on a library default corrupts non-ASCII directory names before
        // path confinement and file classification run. Select UTF-8 before opening the control
        // connection so LIST/MLSD names and commands use the same encoding end to end.
        client.controlEncoding = Charsets.UTF_8.name()
        client.connectTimeout = 15_000
        client.defaultTimeout = 15_000
        @Suppress("DEPRECATION")
        client.setDataTimeout(30_000)
        client.connect(location.host, location.port)
        if (!FTPReply.isPositiveCompletion(client.replyCode)) {
            client.disconnect()
            throw NetworkProtocolException(NetworkFailure.ServerRejected, "Server rejected the connection (${client.replyCode})")
        }
        val username = credential?.username?.takeIf { it.isNotBlank() } ?: "anonymous"
        val password = credential?.password.orEmpty().ifBlank { "max-video-player@localhost" }
        if (!client.login(username, password)) {
            client.disconnect()
            throw NetworkProtocolException(NetworkFailure.AuthenticationFailed, "Authentication failed")
        }
        if (client is FTPSClient) {
            client.execPBSZ(0)
            client.execPROT("P")
        }
        if (location.ftpPassiveMode) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
        if (!client.setFileType(FTP.BINARY_FILE_TYPE)) {
            closeClient(client)
            throw NetworkProtocolException(NetworkFailure.ProtocolUnsupported, "Server did not accept binary transfer mode")
        }
        return client
    }

    internal fun remotePath(location: NetworkLocation, child: String): String =
        "/" + NetworkUriPolicy.safeRemotePath(listOf(location.basePath, child).filter { it.isNotBlank() }.joinToString("/"))

    internal fun closeClient(client: FTPClient) {
        runCatching { if (client.isConnected) client.logout() }
        runCatching { if (client.isConnected) client.disconnect() }
    }

    private inline fun <T> withClient(location: NetworkLocation, credential: NetworkCredential?, block: (FTPClient) -> T): T {
        val client = try {
            createConnectedClient(location, credential)
        } catch (error: NetworkProtocolException) {
            throw error
        } catch (error: IOException) {
            throw NetworkProtocolException(NetworkFailure.ServerNotFound, "FTP server unavailable", error)
        }
        return try { block(client) } finally { closeClient(client) }
    }
}

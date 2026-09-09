package com.zubaer.maxvideoplayer.feature.privatevault.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultIdentity
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultSession
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultContainerFormat
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import com.zubaer.maxvideoplayer.feature.privatevault.storage.PrivateVaultStorage
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

class PrivateVaultResolver(
    private val session: PrivateVaultSession,
    private val storage: PrivateVaultStorage,
) {
    data class Resolved(val vaultId: UUID, val file: File, val masterSecret: ByteArray) : AutoCloseable {
        override fun close() = PrivateVaultCrypto.zero(masterSecret)
    }

    fun resolve(uri: Uri): Resolved {
        val raw = uri.toString()
        val id = PrivateVaultIdentity.vaultId(raw) ?: throw IOException("Invalid private-vault URI")
        val uuid = runCatching { UUID.fromString(id) }.getOrElse { throw IOException("Invalid private-vault identity") }
        // Authenticate the vault session before probing whether an opaque container exists. This
        // keeps locked callers from learning private-vault item existence through error differences.
        val masterSecret = session.masterSecretCopy()
        return try {
            val file = storage.containerFile(id)
            if (!file.isFile) throw IOException("Private media is unavailable")
            Resolved(uuid, file, masterSecret)
        } catch (error: Throwable) {
            PrivateVaultCrypto.zero(masterSecret)
            throw error
        }
    }
}

@UnstableApi
class EncryptedVaultDataSource(
    private val resolver: PrivateVaultResolver,
) : BaseDataSource(false) {
    class Factory(private val resolver: PrivateVaultResolver) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedVaultDataSource(resolver)
    }

    private var randomAccessFile: RandomAccessFile? = null
    private var openContainer: PrivateVaultContainerFormat.OpenContainer? = null
    private var resolved: PrivateVaultResolver.Resolved? = null
    private var openedDataSpec: DataSpec? = null
    private var currentUri: Uri? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var transferStartedFlag = false

    override fun open(dataSpec: DataSpec): Long {
        check(randomAccessFile == null) { "EncryptedVaultDataSource is already open" }
        transferInitializing(dataSpec)
        val localResolved = resolver.resolve(dataSpec.uri)
        try {
            val raf = RandomAccessFile(localResolved.file, "r")
            val container = try {
                PrivateVaultContainerFormat.open(rafToFile(localResolved.file), localResolved.vaultId, localResolved.masterSecret)
            } catch (error: Throwable) {
                raf.close()
                throw error
            }
            val logicalLength = container.header.plaintextLength
            if (dataSpec.position < 0L || dataSpec.position > logicalLength) {
                container.close()
                raf.close()
                throw IOException("Private media position is outside the file")
            }
            val available = logicalLength - dataSpec.position
            val requested = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                available
            } else {
                if (dataSpec.length < 0L) throw IOException("Invalid private media length")
                minOf(available, dataSpec.length)
            }
            resolved = localResolved
            randomAccessFile = raf
            openContainer = container
            openedDataSpec = dataSpec
            currentUri = dataSpec.uri
            position = dataSpec.position
            bytesRemaining = requested
            transferStarted(dataSpec)
            transferStartedFlag = true
            return requested
        } catch (error: Throwable) {
            localResolved.close()
            throw if (error is IOException) error else IOException("Private media could not be opened", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val raf = randomAccessFile ?: throw IOException("Private media source is closed")
        val container = openContainer ?: throw IOException("Private media source is closed")
        val count = minOf(length.toLong(), bytesRemaining).toInt()
        val read = PrivateVaultContainerFormat.readPlainRange(raf, container, position, buffer, offset, count)
        if (read <= 0) return C.RESULT_END_OF_INPUT
        position = Math.addExact(position, read.toLong())
        bytesRemaining -= read.toLong()
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        val wasStarted = transferStartedFlag
        transferStartedFlag = false
        runCatching { openContainer?.close() }
        openContainer = null
        runCatching { randomAccessFile?.close() }
        randomAccessFile = null
        resolved?.close()
        resolved = null
        openedDataSpec = null
        currentUri = null
        position = 0L
        bytesRemaining = 0L
        if (wasStarted) transferEnded()
    }

    private fun rafToFile(file: File): File = file
}

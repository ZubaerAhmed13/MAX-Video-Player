package com.zubaer.maxvideoplayer.feature.privatevault.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.core.model.SourceAvailability
import com.zubaer.maxvideoplayer.feature.privatevault.PRIVATE_VAULT_FORMAT_VERSION
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateImportMode
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateImportResult
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateMediaMetadata
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateMediaStatus
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultItem
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultSession
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultContainerFormat
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import com.zubaer.maxvideoplayer.feature.privatevault.persistence.PrivateMediaDao
import com.zubaer.maxvideoplayer.feature.privatevault.persistence.PrivateMediaEntity
import com.zubaer.maxvideoplayer.feature.privatevault.storage.PrivateVaultStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

class PrivateVaultRepository(
    context: Context,
    private val dao: PrivateMediaDao,
    private val storage: PrivateVaultStorage,
    private val session: PrivateVaultSession,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    suspend fun import(uri: Uri, mode: PrivateImportMode): PrivateImportResult = withContext(Dispatchers.IO) {
        if (session.state.value != com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultState.UNLOCKED) {
            return@withContext PrivateImportResult.Failure("Unlock the Private Vault first.")
        }
        val source = sourceInfo(uri) ?: return@withContext PrivateImportResult.Failure("The selected source is unavailable or its size cannot be determined safely.")
        if (source.sizeBytes < 0L) return@withContext PrivateImportResult.Failure("The selected source size is unavailable.")
        val chunkOverhead = try {
            Math.multiplyExact(
                com.zubaer.maxvideoplayer.feature.privatevault.crypto.VaultLongMath.ceilDiv(
                    source.sizeBytes,
                    com.zubaer.maxvideoplayer.feature.privatevault.PRIVATE_VAULT_CHUNK_SIZE.toLong(),
                ),
                PrivateVaultCrypto.GCM_TAG_BYTES.toLong(),
            )
        } catch (_: ArithmeticException) {
            return@withContext PrivateImportResult.Failure("The selected source is too large to address safely.")
        }
        val required = runCatching { Math.addExact(source.sizeBytes, chunkOverhead + STORAGE_RESERVE_BYTES) }.getOrElse {
            return@withContext PrivateImportResult.Failure("The selected source is too large to address safely.")
        }
        if (storage.availableBytes() < required) {
            return@withContext PrivateImportResult.Failure("Not enough device storage to create the encrypted private copy.")
        }

        val vaultUuid = UUID.randomUUID()
        val vaultId = vaultUuid.toString()
        val partial = storage.partialFile(vaultId)
        val master = runCatching { session.masterSecretCopy() }.getOrElse {
            return@withContext PrivateImportResult.Failure("Private Vault locked before the import started.")
        }
        try {
            partial.delete()
            val metadata = PrivateMediaMetadata(
                originalDisplayName = source.displayName,
                title = source.displayName.substringBeforeLast('.', source.displayName),
                mimeType = source.mimeType,
            )
            val write = openSource(uri).use { input ->
                PrivateVaultContainerFormat.write(
                    input = input,
                    sourceLength = source.sizeBytes,
                    destination = partial,
                    vaultId = vaultUuid,
                    metadata = metadata,
                    masterSecret = master,
                )
            }
            if (write.plaintextLength != source.sizeBytes) {
                partial.delete()
                return@withContext PrivateImportResult.Failure("Source changed during Private Vault import.")
            }

            if (mode == PrivateImportMode.MOVE) {
                val verifiedDigest = digestDecrypted(partial, vaultUuid, master)
                val verified = try {
                    PrivateVaultCrypto.constantTimeEquals(write.plaintextSha256, verifiedDigest)
                } finally {
                    verifiedDigest.fill(0)
                }
                if (!verified) {
                    partial.delete()
                    return@withContext PrivateImportResult.Failure("Encrypted copy verification failed; the original was not deleted.")
                }
            }

            val committed = storage.commitPartial(vaultId)
            val now = System.currentTimeMillis()
            val entity = PrivateMediaEntity(
                vaultId = vaultId,
                containerLocation = committed.name,
                encryptedSizeBytes = committed.length(),
                originalSizeBytes = source.sizeBytes,
                createdAtMs = now,
                importedAtMs = now,
                formatVersion = PRIVATE_VAULT_FORMAT_VERSION,
                status = PrivateMediaStatus.AVAILABLE.name,
            )
            try {
                dao.upsert(entity)
            } catch (error: Throwable) {
                committed.delete()
                return@withContext PrivateImportResult.Failure("Private Vault index could not be committed; no completed item was created.")
            }

            val item = PrivateVaultItem(
                vaultId,
                metadata,
                source.sizeBytes,
                committed.length(),
                now,
                PrivateMediaStatus.AVAILABLE,
            )
            if (mode == PrivateImportMode.COPY) {
                PrivateImportResult.Success(item, originalDeleted = false)
            } else if (deleteOriginal(uri)) {
                PrivateImportResult.Success(item, originalDeleted = true)
            } else {
                PrivateImportResult.EncryptedCopyCreatedOriginalRemains(
                    item,
                    "Encrypted copy created, but the original file still exists.",
                )
            }
        } catch (error: Throwable) {
            partial.delete()
            PrivateImportResult.Failure(error.message ?: "Private Vault import failed safely.")
        } finally {
            PrivateVaultCrypto.zero(master)
        }
    }

    suspend fun loadUnlockedItems(): List<PrivateVaultItem> = withContext(Dispatchers.IO) {
        val master = session.masterSecretCopy()
        try {
            dao.all().mapNotNull { entity ->
                val file = storage.containerFile(entity.vaultId)
                if (!file.isFile) {
                    runCatching { dao.upsert(entity.copy(status = PrivateMediaStatus.MISSING.name)) }
                    return@mapNotNull null
                }
                val uuid = runCatching { UUID.fromString(entity.vaultId) }.getOrNull() ?: return@mapNotNull null
                try {
                    PrivateVaultContainerFormat.open(file, uuid, master).use { open ->
                        PrivateVaultItem(
                            vaultId = entity.vaultId,
                            metadata = open.metadata,
                            originalSizeBytes = entity.originalSizeBytes,
                            encryptedSizeBytes = entity.encryptedSizeBytes,
                            importedAtMs = entity.importedAtMs,
                            status = PrivateMediaStatus.AVAILABLE,
                        )
                    }
                } catch (_: Throwable) {
                    runCatching { dao.upsert(entity.copy(status = PrivateMediaStatus.CORRUPTED.name)) }
                    null
                }
            }
        } finally {
            PrivateVaultCrypto.zero(master)
        }
    }

    fun toAppMedia(item: PrivateVaultItem): AppMedia = AppMedia(
        stableId = item.stableUri,
        uri = item.stableUri,
        title = item.metadata.title,
        mimeType = item.metadata.mimeType,
        durationMs = item.metadata.durationMs,
        sizeBytes = item.originalSizeBytes,
        width = item.metadata.width,
        height = item.metadata.height,
        rotationDegrees = item.metadata.rotationDegrees,
        fileName = null,
        relativePath = null,
        folderKey = null,
        folderName = null,
        sourceId = "private-vault",
        availability = SourceAvailability.AVAILABLE,
        sourceType = MediaSourceType.PRIVATE,
    )

    suspend fun restore(vaultId: String, output: java.io.OutputStream): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.get(vaultId) ?: return@withContext false
        val file = storage.containerFile(vaultId)
        if (!file.isFile) return@withContext false
        val uuid = runCatching { UUID.fromString(vaultId) }.getOrElse { return@withContext false }
        val master = session.masterSecretCopy()
        try {
            RandomAccessFile(file, "r").use { raf ->
                PrivateVaultContainerFormat.open(file, uuid, master).use { open ->
                    val buffer = ByteArray(RESTORE_BUFFER_BYTES)
                    var position = 0L
                    while (position < entity.originalSizeBytes) {
                        val count = minOf(buffer.size.toLong(), entity.originalSizeBytes - position).toInt()
                        val read = PrivateVaultContainerFormat.readPlainRange(raf, open, position, buffer, 0, count)
                        if (read <= 0) throw java.io.EOFException("Private media ended unexpectedly")
                        output.write(buffer, 0, read)
                        buffer.fill(0, 0, read)
                        position = Math.addExact(position, read.toLong())
                    }
                    output.flush()
                }
            }
            true
        } finally {
            PrivateVaultCrypto.zero(master)
        }
    }

    suspend fun delete(vaultId: String): Boolean = withContext(Dispatchers.IO) {
        if (session.state.value != com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultState.UNLOCKED) return@withContext false
        val deletedBytes = storage.delete(vaultId)
        if (deletedBytes) dao.delete(vaultId)
        deletedBytes
    }

    suspend fun eraseAll(): Boolean = withContext(Dispatchers.IO) {
        if (session.state.value != com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultState.UNLOCKED) return@withContext false
        var ok = true
        dao.all().forEach { if (!storage.delete(it.vaultId)) ok = false }
        if (ok) dao.clear()
        ok
    }

    suspend fun recoverAbandonedTransactions() = withContext(Dispatchers.IO) {
        storage.cleanupPartials()
        val indexed = dao.all().mapTo(mutableSetOf()) { it.vaultId }
        storage.opaqueContainerIds().filterNot(indexed::contains).forEach { orphan ->
            runCatching { storage.containerFile(orphan).delete() }
        }
    }

    private fun digestDecrypted(file: File, vaultId: UUID, master: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { raf ->
            PrivateVaultContainerFormat.open(file, vaultId, master).use { open ->
                val buffer = ByteArray(VERIFY_BUFFER_BYTES)
                var position = 0L
                while (position < open.header.plaintextLength) {
                    val count = minOf(buffer.size.toLong(), open.header.plaintextLength - position).toInt()
                    val read = PrivateVaultContainerFormat.readPlainRange(raf, open, position, buffer, 0, count)
                    if (read <= 0) throw java.io.EOFException("Verification reached EOF early")
                    digest.update(buffer, 0, read)
                    buffer.fill(0, 0, read)
                    position = Math.addExact(position, read.toLong())
                }
            }
        }
        return digest.digest()
    }

    private fun openSource(uri: Uri): InputStream = when (uri.scheme?.lowercase()) {
        "file" -> FileInputStream(File(requireNotNull(uri.path)))
        else -> resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException("Source cannot be opened")
    }

    private fun deleteOriginal(uri: Uri): Boolean = runCatching {
        when (uri.scheme?.lowercase()) {
            "file" -> File(requireNotNull(uri.path)).delete()
            else -> if (DocumentsContract.isDocumentUri(appContext, uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            } else {
                resolver.delete(uri, null, null) > 0
            }
        }
    }.getOrDefault(false)

    private fun sourceInfo(uri: Uri): SourceInfo? {
        if (uri.scheme.equals("file", true)) {
            val file = uri.path?.let(::File) ?: return null
            if (!file.isFile) return null
            return SourceInfo(file.name.ifBlank { "Private media" }, resolver.getType(uri), file.length())
        }
        var displayName: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        val resolvedSize = size ?: runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()
        if (resolvedSize == null || resolvedSize < 0L) return null
        return SourceInfo(displayName?.takeIf { it.isNotBlank() } ?: "Private media", resolver.getType(uri), resolvedSize)
    }

    private data class SourceInfo(val displayName: String, val mimeType: String?, val sizeBytes: Long)

    private companion object {
        const val STORAGE_RESERVE_BYTES = 512L * 1024L
        const val VERIFY_BUFFER_BYTES = 1024 * 1024
        const val RESTORE_BUFFER_BYTES = 1024 * 1024
    }
}

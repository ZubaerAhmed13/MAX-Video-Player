package com.zubaer.maxvideoplayer.feature.privatevault.crypto

import com.zubaer.maxvideoplayer.feature.privatevault.PRIVATE_VAULT_CHUNK_SIZE
import com.zubaer.maxvideoplayer.feature.privatevault.PRIVATE_VAULT_FORMAT_VERSION
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateMediaMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID

/**
 * MAXVAULT_CONTAINER_V1.
 *
 * The public header contains only opaque identity and random-access geometry. Original filenames,
 * titles and media metadata are encrypted. Each media chunk is independently AES-256-GCM
 * authenticated so Media3 can seek without a plaintext staging file.
 */
object PrivateVaultContainerFormat {
    val MAGIC: ByteArray = "MAXVLT01".toByteArray(Charsets.US_ASCII)
    const val FIXED_HEADER_BYTES = 80
    const val MAX_ENCRYPTED_METADATA_BYTES = 128 * 1024
    const val MAX_WRAPPED_KEY_BYTES = 256

    data class PublicHeader(
        val version: Int,
        val chunkSize: Int,
        val plaintextLength: Long,
        val vaultId: UUID,
        val chunkNoncePrefix: ByteArray,
        val metadataNonce: ByteArray,
        val metadataCipherLength: Int,
        val wrappedKeyNonce: ByteArray,
        val wrappedKeyCipherLength: Int,
        val dataStartOffset: Long,
    )

    data class OpenContainer(
        val header: PublicHeader,
        val metadata: PrivateMediaMetadata,
        val fileKey: ByteArray,
    ) : AutoCloseable {
        override fun close() = PrivateVaultCrypto.zero(fileKey)
    }

    data class WriteResult(
        val plaintextSha256: ByteArray,
        val plaintextLength: Long,
        val encryptedLength: Long,
    )

    fun write(
        input: InputStream,
        sourceLength: Long,
        destination: File,
        vaultId: UUID,
        metadata: PrivateMediaMetadata,
        masterSecret: ByteArray,
    ): WriteResult {
        require(sourceLength >= 0L) { "Source length is required for the random-access container" }
        require(masterSecret.size == PrivateVaultCrypto.KEY_BYTES) { "Invalid vault master secret" }
        val chunkCount = VaultLongMath.ceilDiv(sourceLength, PRIVATE_VAULT_CHUNK_SIZE.toLong())
        require(chunkCount <= PrivateVaultCrypto.MAX_CHUNK_INDEX + 1L) { "Source exceeds nonce-safe container capacity" }

        destination.parentFile?.mkdirs()
        val fileKey = PrivateVaultCrypto.randomKey()
        val noncePrefix = PrivateVaultCrypto.randomNoncePrefix()
        val descriptor = descriptorAad(vaultId, sourceLength, PRIVATE_VAULT_CHUNK_SIZE, noncePrefix)
        val metadataPlain = encodeMetadata(metadata)
        val metadataBlob = PrivateVaultCrypto.encrypt(fileKey, metadataPlain, aad(descriptor, "metadata"))
        metadataPlain.fill(0)
        val wrappedKeyBlob = PrivateVaultCrypto.encrypt(masterSecret, fileKey, aad(descriptor, "file-key"))
        require(metadataBlob.ciphertext.size <= MAX_ENCRYPTED_METADATA_BYTES)
        require(wrappedKeyBlob.ciphertext.size <= MAX_WRAPPED_KEY_BYTES)

        val digest = MessageDigest.getInstance("SHA-256")
        var totalPlain = 0L
        try {
            RandomAccessFile(destination, "rw").use { raf ->
                raf.setLength(0L)
                writeFixedHeader(
                    raf = raf,
                    sourceLength = sourceLength,
                    vaultId = vaultId,
                    noncePrefix = noncePrefix,
                    metadataNonce = metadataBlob.nonce,
                    metadataLength = metadataBlob.ciphertext.size,
                    wrappedKeyNonce = wrappedKeyBlob.nonce,
                    wrappedKeyLength = wrappedKeyBlob.ciphertext.size,
                )
                raf.write(metadataBlob.ciphertext)
                raf.write(wrappedKeyBlob.ciphertext)

                val buffer = ByteArray(PRIVATE_VAULT_CHUNK_SIZE)
                var chunkIndex = 0L
                while (totalPlain < sourceLength) {
                    val expected = minOf(PRIVATE_VAULT_CHUNK_SIZE.toLong(), sourceLength - totalPlain).toInt()
                    readFullyExact(input, buffer, expected)
                    digest.update(buffer, 0, expected)
                    val plainChunk = if (expected == buffer.size) buffer else buffer.copyOf(expected)
                    val encrypted = PrivateVaultCrypto.encrypt(
                        key = fileKey,
                        plaintext = plainChunk,
                        aad = chunkAad(descriptor, chunkIndex, expected),
                        nonce = PrivateVaultCrypto.chunkNonce(noncePrefix, chunkIndex),
                    ).ciphertext
                    raf.write(encrypted)
                    if (plainChunk !== buffer) plainChunk.fill(0)
                    totalPlain = VaultLongMath.checkedAdd(totalPlain, expected.toLong())
                    chunkIndex++
                }
                if (input.read() != -1) throw VaultFormatException("Source grew while it was being secured")
                buffer.fill(0)
                raf.fd.sync()
                return WriteResult(digest.digest(), totalPlain, raf.length())
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            PrivateVaultCrypto.zero(fileKey)
        }
    }

    fun open(file: File, expectedVaultId: UUID, masterSecret: ByteArray): OpenContainer {
        require(masterSecret.size == PrivateVaultCrypto.KEY_BYTES) { "Invalid vault master secret" }
        RandomAccessFile(file, "r").use { raf ->
            val header = readHeader(raf)
            if (header.vaultId != expectedVaultId) throw VaultFormatException("Vault identity mismatch")
            validateFileGeometry(raf.length(), header)
            val descriptor = descriptorAad(header.vaultId, header.plaintextLength, header.chunkSize, header.chunkNoncePrefix)

            raf.seek(FIXED_HEADER_BYTES.toLong())
            val metadataCipher = ByteArray(header.metadataCipherLength)
            raf.readFully(metadataCipher)
            val wrappedKeyCipher = ByteArray(header.wrappedKeyCipherLength)
            raf.readFully(wrappedKeyCipher)

            val fileKey = PrivateVaultCrypto.decrypt(
                masterSecret,
                wrappedKeyCipher,
                aad(descriptor, "file-key"),
                header.wrappedKeyNonce,
            )
            try {
                if (fileKey.size != PrivateVaultCrypto.KEY_BYTES) throw VaultFormatException("Invalid wrapped content key")
                val metadataPlain = PrivateVaultCrypto.decrypt(
                    fileKey,
                    metadataCipher,
                    aad(descriptor, "metadata"),
                    header.metadataNonce,
                )
                val metadata = try {
                    decodeMetadata(metadataPlain)
                } finally {
                    metadataPlain.fill(0)
                }
                return OpenContainer(header, metadata, fileKey.copyOf())
            } finally {
                fileKey.fill(0)
            }
        }
    }

    fun readPlainRange(
        file: RandomAccessFile,
        open: OpenContainer,
        position: Long,
        target: ByteArray,
        targetOffset: Int,
        requestedLength: Int,
    ): Int {
        require(position >= 0L) { "Negative vault position" }
        require(targetOffset >= 0 && requestedLength >= 0 && targetOffset + requestedLength <= target.size)
        val logicalLength = open.header.plaintextLength
        if (position >= logicalLength) return -1
        var remaining = minOf(requestedLength.toLong(), logicalLength - position).toInt()
        if (remaining == 0) return 0
        var logicalPosition = position
        var outputOffset = targetOffset
        val descriptor = descriptorAad(
            open.header.vaultId,
            open.header.plaintextLength,
            open.header.chunkSize,
            open.header.chunkNoncePrefix,
        )

        while (remaining > 0) {
            val chunkIndex = logicalPosition / open.header.chunkSize.toLong()
            if (chunkIndex > PrivateVaultCrypto.MAX_CHUNK_INDEX) throw VaultFormatException("Chunk index overflow")
            val withinChunk = (logicalPosition % open.header.chunkSize.toLong()).toInt()
            val chunkPlainLength = chunkPlainLength(open.header, chunkIndex)
            val cipherLength = Math.addExact(chunkPlainLength, PrivateVaultCrypto.GCM_TAG_BYTES)
            val encrypted = ByteArray(cipherLength)
            file.seek(chunkCipherOffset(open.header, chunkIndex))
            file.readFully(encrypted)
            val plain = PrivateVaultCrypto.decrypt(
                open.fileKey,
                encrypted,
                chunkAad(descriptor, chunkIndex, chunkPlainLength),
                PrivateVaultCrypto.chunkNonce(open.header.chunkNoncePrefix, chunkIndex),
            )
            try {
                val count = minOf(remaining, chunkPlainLength - withinChunk)
                if (count <= 0) throw VaultFormatException("Invalid random-access chunk geometry")
                plain.copyInto(target, outputOffset, withinChunk, withinChunk + count)
                outputOffset += count
                remaining -= count
                logicalPosition = VaultLongMath.checkedAdd(logicalPosition, count.toLong())
            } finally {
                plain.fill(0)
                encrypted.fill(0)
            }
        }
        return outputOffset - targetOffset
    }

    fun readHeader(raf: RandomAccessFile): PublicHeader {
        if (raf.length() < FIXED_HEADER_BYTES) throw VaultFormatException("Truncated vault header")
        raf.seek(0L)
        val magic = ByteArray(MAGIC.size)
        raf.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw VaultFormatException("Not a MAX private-vault container")
        val version = raf.readInt()
        if (version != PRIVATE_VAULT_FORMAT_VERSION) throw VaultFormatException("Unsupported vault format version: $version")
        val chunkSize = raf.readInt()
        if (chunkSize != PRIVATE_VAULT_CHUNK_SIZE) throw VaultFormatException("Unsupported vault chunk size")
        val plaintextLength = raf.readLong()
        if (plaintextLength < 0L) throw VaultFormatException("Invalid plaintext length")
        val vaultId = UUID(raf.readLong(), raf.readLong())
        val noncePrefix = ByteArray(PrivateVaultCrypto.NONCE_PREFIX_BYTES).also(raf::readFully)
        val metadataNonce = ByteArray(PrivateVaultCrypto.GCM_NONCE_BYTES).also(raf::readFully)
        val metadataLength = raf.readInt()
        val wrappedNonce = ByteArray(PrivateVaultCrypto.GCM_NONCE_BYTES).also(raf::readFully)
        val wrappedLength = raf.readInt()
        if (metadataLength !in PrivateVaultCrypto.GCM_TAG_BYTES..MAX_ENCRYPTED_METADATA_BYTES) {
            throw VaultFormatException("Invalid encrypted metadata length")
        }
        if (wrappedLength !in PrivateVaultCrypto.GCM_TAG_BYTES..MAX_WRAPPED_KEY_BYTES) {
            throw VaultFormatException("Invalid wrapped-key length")
        }
        val dataStart = VaultLongMath.checkedAdd(FIXED_HEADER_BYTES.toLong(), metadataLength.toLong() + wrappedLength.toLong())
        return PublicHeader(
            version,
            chunkSize,
            plaintextLength,
            vaultId,
            noncePrefix,
            metadataNonce,
            metadataLength,
            wrappedNonce,
            wrappedLength,
            dataStart,
        )
    }

    fun expectedEncryptedLength(header: PublicHeader): Long {
        val chunks = VaultLongMath.ceilDiv(header.plaintextLength, header.chunkSize.toLong())
        if (chunks == 0L) return header.dataStartOffset
        val fullChunksBeforeLast = chunks - 1L
        val fullCipherBytes = VaultLongMath.checkedMultiply(
            fullChunksBeforeLast,
            header.chunkSize.toLong() + PrivateVaultCrypto.GCM_TAG_BYTES,
        )
        val lastPlain = header.plaintextLength - VaultLongMath.checkedMultiply(fullChunksBeforeLast, header.chunkSize.toLong())
        return VaultLongMath.checkedAdd(
            VaultLongMath.checkedAdd(header.dataStartOffset, fullCipherBytes),
            lastPlain + PrivateVaultCrypto.GCM_TAG_BYTES,
        )
    }

    private fun validateFileGeometry(actualLength: Long, header: PublicHeader) {
        val chunks = VaultLongMath.ceilDiv(header.plaintextLength, header.chunkSize.toLong())
        if (chunks > PrivateVaultCrypto.MAX_CHUNK_INDEX + 1L) throw VaultFormatException("Vault file exceeds nonce-safe chunk range")
        val expected = try {
            expectedEncryptedLength(header)
        } catch (overflow: ArithmeticException) {
            throw VaultFormatException("Vault geometry overflow", overflow)
        }
        if (actualLength != expected) throw VaultFormatException("Vault container is truncated or malformed")
    }

    private fun chunkCipherOffset(header: PublicHeader, chunkIndex: Long): Long {
        require(chunkIndex >= 0L)
        return try {
            VaultLongMath.checkedAdd(
                header.dataStartOffset,
                VaultLongMath.checkedMultiply(chunkIndex, header.chunkSize.toLong() + PrivateVaultCrypto.GCM_TAG_BYTES),
            )
        } catch (overflow: ArithmeticException) {
            throw VaultFormatException("Encrypted offset overflow", overflow)
        }
    }

    private fun chunkPlainLength(header: PublicHeader, chunkIndex: Long): Int {
        val start = VaultLongMath.checkedMultiply(chunkIndex, header.chunkSize.toLong())
        if (start >= header.plaintextLength) throw EOFException("Chunk is beyond logical EOF")
        return minOf(header.chunkSize.toLong(), header.plaintextLength - start).toInt()
    }

    private fun writeFixedHeader(
        raf: RandomAccessFile,
        sourceLength: Long,
        vaultId: UUID,
        noncePrefix: ByteArray,
        metadataNonce: ByteArray,
        metadataLength: Int,
        wrappedKeyNonce: ByteArray,
        wrappedKeyLength: Int,
    ) {
        raf.write(MAGIC)
        raf.writeInt(PRIVATE_VAULT_FORMAT_VERSION)
        raf.writeInt(PRIVATE_VAULT_CHUNK_SIZE)
        raf.writeLong(sourceLength)
        raf.writeLong(vaultId.mostSignificantBits)
        raf.writeLong(vaultId.leastSignificantBits)
        raf.write(noncePrefix)
        raf.write(metadataNonce)
        raf.writeInt(metadataLength)
        raf.write(wrappedKeyNonce)
        raf.writeInt(wrappedKeyLength)
        check(raf.filePointer == FIXED_HEADER_BYTES.toLong())
    }

    private fun descriptorAad(vaultId: UUID, length: Long, chunkSize: Int, noncePrefix: ByteArray): ByteArray =
        ByteBuffer.allocate(MAGIC.size + 4 + 4 + 8 + 16 + noncePrefix.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .putInt(PRIVATE_VAULT_FORMAT_VERSION)
            .putInt(chunkSize)
            .putLong(length)
            .putLong(vaultId.mostSignificantBits)
            .putLong(vaultId.leastSignificantBits)
            .put(noncePrefix)
            .array()

    private fun aad(descriptor: ByteArray, domain: String): ByteArray =
        descriptor + domain.toByteArray(Charsets.US_ASCII)

    private fun chunkAad(descriptor: ByteArray, chunkIndex: Long, plaintextLength: Int): ByteArray =
        ByteBuffer.allocate(descriptor.size + 5 + 8 + 4)
            .order(ByteOrder.BIG_ENDIAN)
            .put(descriptor)
            .put("chunk".toByteArray(Charsets.US_ASCII))
            .putLong(chunkIndex)
            .putInt(plaintextLength)
            .array()

    private fun encodeMetadata(metadata: PrivateMediaMetadata): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            writeSafeString(out, metadata.originalDisplayName)
            writeSafeString(out, metadata.title)
            writeNullableString(out, metadata.mimeType)
            writeNullableLong(out, metadata.durationMs)
            writeNullableInt(out, metadata.width)
            writeNullableInt(out, metadata.height)
            writeNullableInt(out, metadata.rotationDegrees)
        }
        bytes.toByteArray()
    }

    private fun decodeMetadata(bytes: ByteArray): PrivateMediaMetadata = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val metadata = PrivateMediaMetadata(
            originalDisplayName = readSafeString(input),
            title = readSafeString(input),
            mimeType = readNullableString(input),
            durationMs = readNullableLong(input),
            width = readNullableInt(input),
            height = readNullableInt(input),
            rotationDegrees = readNullableInt(input),
        )
        if (input.read() != -1) throw VaultFormatException("Unexpected metadata payload")
        metadata
    }

    private fun writeSafeString(out: DataOutputStream, value: String) {
        require(value.length <= 4096) { "Metadata field is too long" }
        out.writeUTF(value)
    }

    private fun readSafeString(input: DataInputStream): String = input.readUTF().also {
        if (it.length > 4096) throw VaultFormatException("Metadata field is too long")
    }

    private fun writeNullableString(out: DataOutputStream, value: String?) {
        out.writeBoolean(value != null)
        if (value != null) writeSafeString(out, value)
    }

    private fun readNullableString(input: DataInputStream): String? = if (input.readBoolean()) readSafeString(input) else null
    private fun writeNullableLong(out: DataOutputStream, value: Long?) { out.writeBoolean(value != null); if (value != null) out.writeLong(value) }
    private fun readNullableLong(input: DataInputStream): Long? = if (input.readBoolean()) input.readLong() else null
    private fun writeNullableInt(out: DataOutputStream, value: Int?) { out.writeBoolean(value != null); if (value != null) out.writeInt(value) }
    private fun readNullableInt(input: DataInputStream): Int? = if (input.readBoolean()) input.readInt() else null

    private fun readFullyExact(input: InputStream, buffer: ByteArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) throw EOFException("Source ended before its declared length")
            if (read == 0) continue
            offset += read
        }
    }
}

class VaultFormatException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)

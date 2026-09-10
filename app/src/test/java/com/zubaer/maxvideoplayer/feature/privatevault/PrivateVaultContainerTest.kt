package com.zubaer.maxvideoplayer.feature.privatevault

import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultContainerFormat
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.VaultAuthenticationException
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.VaultFormatException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

class PrivateVaultContainerTest {
    @Test
    fun productionWriterAndReader_roundTripEmptyOneByteAndMultiChunkRanges() {
        listOf(
            ByteArray(0),
            byteArrayOf(42),
            ByteArray(PRIVATE_VAULT_CHUNK_SIZE) { (it * 13).toByte() },
            ByteArray(PRIVATE_VAULT_CHUNK_SIZE * 2 + 777) { (it * 17 + 3).toByte() },
        ).forEach { plain ->
            withContainer(plain) { file, vaultId, master ->
                PrivateVaultContainerFormat.open(file, vaultId, master).use { open ->
                    assertEquals(plain.size.toLong(), open.header.plaintextLength)
                    RandomAccessFile(file, "r").use { raf ->
                        if (plain.isEmpty()) {
                            assertEquals(-1, PrivateVaultContainerFormat.readPlainRange(raf, open, 0L, ByteArray(1), 0, 1))
                        } else {
                            val full = ByteArray(plain.size)
                            assertEquals(plain.size, PrivateVaultContainerFormat.readPlainRange(raf, open, 0L, full, 0, full.size))
                            assertArrayEquals(plain, full)

                            val start = (plain.size / 2).coerceAtMost(PRIVATE_VAULT_CHUNK_SIZE - 3).toLong()
                            val requested = minOf(1024, plain.size - start.toInt())
                            val range = ByteArray(requested)
                            assertEquals(requested, PrivateVaultContainerFormat.readPlainRange(raf, open, start, range, 0, requested))
                            assertArrayEquals(plain.copyOfRange(start.toInt(), start.toInt() + requested), range)

                            if (plain.size > PRIVATE_VAULT_CHUNK_SIZE + 10) {
                                val crossStart = PRIVATE_VAULT_CHUNK_SIZE - 5L
                                val cross = ByteArray(20)
                                assertEquals(20, PrivateVaultContainerFormat.readPlainRange(raf, open, crossStart, cross, 0, 20))
                                assertArrayEquals(plain.copyOfRange(crossStart.toInt(), crossStart.toInt() + 20), cross)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun wrongMasterSecret_modifiedCiphertext_andTruncationFailClosed() {
        val plain = ByteArray(PRIVATE_VAULT_CHUNK_SIZE + 333) { (it * 7).toByte() }
        withContainer(plain) { file, vaultId, master ->
            assertThrows(VaultAuthenticationException::class.java) {
                PrivateVaultContainerFormat.open(file, vaultId, ByteArray(32) { 99 }).close()
            }

            val header = RandomAccessFile(file, "r").use(PrivateVaultContainerFormat::readHeader)
            val modified = file.readBytes()
            val firstCipher = header.dataStartOffset.toInt()
            modified[firstCipher + 10] = (modified[firstCipher + 10].toInt() xor 1).toByte()
            val tampered = tempFile().apply { writeBytes(modified) }
            try {
                PrivateVaultContainerFormat.open(tampered, vaultId, master).use { open ->
                    assertThrows(VaultAuthenticationException::class.java) {
                        RandomAccessFile(tampered, "r").use { raf ->
                            PrivateVaultContainerFormat.readPlainRange(raf, open, 0L, ByteArray(32), 0, 32)
                        }
                    }
                }
            } finally {
                tampered.delete()
            }

            val truncated = tempFile().apply { writeBytes(file.readBytes().copyOf((file.length() - 1).toInt())) }
            try {
                assertThrows(VaultFormatException::class.java) {
                    PrivateVaultContainerFormat.open(truncated, vaultId, master).close()
                }
            } finally {
                truncated.delete()
            }
        }
    }

    @Test
    fun swappingAuthenticatedChunksFailsBecauseChunkIndexIsInAadAndNonce() {
        val plain = ByteArray(PRIVATE_VAULT_CHUNK_SIZE * 2) { (it * 29).toByte() }
        withContainer(plain) { file, vaultId, master ->
            val bytes = file.readBytes()
            val header = RandomAccessFile(file, "r").use(PrivateVaultContainerFormat::readHeader)
            val block = PRIVATE_VAULT_CHUNK_SIZE + PrivateVaultCrypto.GCM_TAG_BYTES
            val first = header.dataStartOffset.toInt()
            val a = bytes.copyOfRange(first, first + block)
            val b = bytes.copyOfRange(first + block, first + block * 2)
            b.copyInto(bytes, first)
            a.copyInto(bytes, first + block)
            val swapped = tempFile().apply { writeBytes(bytes) }
            try {
                PrivateVaultContainerFormat.open(swapped, vaultId, master).use { open ->
                    assertThrows(VaultAuthenticationException::class.java) {
                        RandomAccessFile(swapped, "r").use { raf ->
                            PrivateVaultContainerFormat.readPlainRange(raf, open, 0L, ByteArray(32), 0, 32)
                        }
                    }
                }
            } finally {
                swapped.delete()
            }
        }
    }

    @Test
    fun synthetic3Point2GiBGeometry_isLongSafeWithoutAllocatingGigabytes() {
        val logical = 3_435_973_837L
        val header = PrivateVaultContainerFormat.PublicHeader(
            version = PRIVATE_VAULT_FORMAT_VERSION,
            chunkSize = PRIVATE_VAULT_CHUNK_SIZE,
            plaintextLength = logical,
            vaultId = UUID(1L, 2L),
            chunkNoncePrefix = ByteArray(8),
            metadataNonce = ByteArray(12),
            metadataCipherLength = 32,
            wrappedKeyNonce = ByteArray(12),
            wrappedKeyCipherLength = 48,
            dataStartOffset = PrivateVaultContainerFormat.FIXED_HEADER_BYTES + 80L,
        )
        val encryptedLength = PrivateVaultContainerFormat.expectedEncryptedLength(header)
        assertTrue(encryptedLength > logical)
        assertTrue(encryptedLength > Int.MAX_VALUE.toLong())
    }

    private fun withContainer(
        plain: ByteArray,
        block: (File, UUID, ByteArray) -> Unit,
    ) {
        val file = tempFile()
        val master = ByteArray(32) { (it * 5 + 1).toByte() }
        val id = UUID.randomUUID()
        try {
            PrivateVaultContainerFormat.write(
                input = ByteArrayInputStream(plain),
                sourceLength = plain.size.toLong(),
                destination = file,
                vaultId = id,
                metadata = PrivateMediaMetadata(
                    originalDisplayName = "TOP_SECRET_PRIVATE_MOVIE_839247.mp4",
                    title = "TOP_SECRET_PRIVATE_MOVIE_839247",
                    mimeType = "video/mp4",
                ),
                masterSecret = master,
            )
            block(file, id, master)
        } finally {
            master.fill(0)
            file.delete()
        }
    }

    private fun tempFile(): File = File.createTempFile("max-vault-test-", ".maxvault")
}

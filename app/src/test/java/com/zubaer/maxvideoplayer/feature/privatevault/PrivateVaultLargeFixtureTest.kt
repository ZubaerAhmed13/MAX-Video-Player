package com.zubaer.maxvideoplayer.feature.privatevault

import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultContainerFormat
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

class PrivateVaultLargeFixtureTest {
    @Test
    fun productionContainerStreams64MiBWithoutWholeFilePlaintextAllocationAndSupportsRandomRanges() {
        val logicalLength = 64L * 1024L * 1024L + 12_345L
        val file = File.createTempFile("max-vault-64m-", ".maxvault")
        val master = PrivateVaultCrypto.randomKey()
        val vaultId = UUID.randomUUID()
        try {
            PrivateVaultContainerFormat.write(
                input = PatternInputStream(logicalLength),
                sourceLength = logicalLength,
                destination = file,
                vaultId = vaultId,
                metadata = PrivateMediaMetadata(
                    originalDisplayName = "large-private-fixture.mp4",
                    title = "large-private-fixture",
                    mimeType = "video/mp4",
                ),
                masterSecret = master,
            )
            PrivateVaultContainerFormat.open(file, vaultId, master).use { open ->
                assertEquals(logicalLength, open.header.plaintextLength)
                RandomAccessFile(file, "r").use { raf ->
                    listOf(
                        0L,
                        PRIVATE_VAULT_CHUNK_SIZE.toLong() - 1_024L,
                        logicalLength / 2L + 333L,
                        logicalLength - 4_096L,
                    ).forEach { start ->
                        val length = minOf(4_096L, logicalLength - start).toInt()
                        val actual = ByteArray(length)
                        assertEquals(length, PrivateVaultContainerFormat.readPlainRange(raf, open, start, actual, 0, length))
                        assertArrayEquals(expected(start, length), actual)
                    }
                }
            }
        } finally {
            PrivateVaultCrypto.zero(master)
            file.delete()
        }
    }

    private fun expected(start: Long, length: Int): ByteArray =
        ByteArray(length) { index -> pattern(start + index.toLong()) }

    private fun pattern(position: Long): Byte = ((position * 31L + 7L) and 0xffL).toByte()

    private inner class PatternInputStream(private val length: Long) : InputStream() {
        private var position = 0L

        override fun read(): Int {
            if (position >= length) return -1
            return pattern(position++).toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, requested: Int): Int {
            if (position >= length) return -1
            val count = minOf(requested.toLong(), length - position).toInt()
            for (index in 0 until count) buffer[offset + index] = pattern(position + index.toLong())
            position += count.toLong()
            return count
        }
    }
}

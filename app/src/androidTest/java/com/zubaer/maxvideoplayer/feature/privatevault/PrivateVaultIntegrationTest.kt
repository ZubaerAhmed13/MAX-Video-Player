package com.zubaer.maxvideoplayer.feature.privatevault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultSession
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultContainerFormat
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.VaultAuthenticationException
import com.zubaer.maxvideoplayer.feature.privatevault.datasource.EncryptedVaultDataSource
import com.zubaer.maxvideoplayer.feature.privatevault.datasource.PrivateVaultResolver
import com.zubaer.maxvideoplayer.feature.privatevault.storage.PrivateVaultStorage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@UnstableApi
class PrivateVaultIntegrationTest {
    @Test
    fun encryptedVaultDataSourceCoversStartBoundariesCrossChunkFinalEofAndLengthContracts() {
        withVault(PRIVATE_VAULT_CHUNK_SIZE * 2 + 257) { fixture ->
            val plain = fixture.plain
            val positions = listOf(
                0L to 256,
                127L to 333,
                PRIVATE_VAULT_CHUNK_SIZE.toLong() to 512,
                PRIVATE_VAULT_CHUNK_SIZE.toLong() - 16L to 128,
                plain.size.toLong() - 1L to 1,
            )
            positions.forEach { (position, requested) ->
                val expectedLength = minOf(requested.toLong(), plain.size.toLong() - position).toInt()
                val source = fixture.factory.createDataSource()
                val opened = source.open(
                    DataSpec.Builder()
                        .setUri(fixture.uri)
                        .setPosition(position)
                        .setLength(requested.toLong())
                        .build(),
                )
                assertEquals(expectedLength.toLong(), opened)
                val output = readToEnd(source, expectedLength + 32)
                assertArrayEquals(plain.copyOfRange(position.toInt(), position.toInt() + expectedLength), output)
                assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
                source.close()
            }

            val unset = fixture.factory.createDataSource()
            val unsetPosition = PRIVATE_VAULT_CHUNK_SIZE.toLong() + 17L
            assertEquals(
                plain.size.toLong() - unsetPosition,
                unset.open(DataSpec.Builder().setUri(fixture.uri).setPosition(unsetPosition).build()),
            )
            assertArrayEquals(
                plain.copyOfRange(unsetPosition.toInt(), plain.size),
                readToEnd(unset, plain.size - unsetPosition.toInt()),
            )
            unset.close()

            val exactEof = fixture.factory.createDataSource()
            assertEquals(0L, exactEof.open(DataSpec.Builder().setUri(fixture.uri).setPosition(plain.size.toLong()).build()))
            assertEquals(C.RESULT_END_OF_INPUT, exactEof.read(ByteArray(1), 0, 1))
            exactEof.close()

            val beyond = fixture.factory.createDataSource()
            assertThrows(IOException::class.java) {
                beyond.open(DataSpec.Builder().setUri(fixture.uri).setPosition(plain.size.toLong() + 1L).build())
            }
            runCatching { beyond.close() }

            assertThrows(IllegalArgumentException::class.java) {
                DataSpec.Builder().setUri(fixture.uri).setPosition(-1L).build()
            }

            val closed = fixture.factory.createDataSource()
            closed.open(DataSpec.Builder().setUri(fixture.uri).setLength(1L).build())
            closed.close()
            assertThrows(IOException::class.java) { closed.read(ByteArray(1), 0, 1) }
        }
    }

    @Test
    fun corruptRequestedChunkFailsAuthenticationWithoutDecryptingWholeFile() {
        withVault(PRIVATE_VAULT_CHUNK_SIZE * 2 + 64) { fixture ->
            val container = fixture.storage.containerFile(fixture.id.toString())
            val header = RandomAccessFile(container, "r").use(PrivateVaultContainerFormat::readHeader)
            val secondChunkOffset = header.dataStartOffset + PRIVATE_VAULT_CHUNK_SIZE.toLong() +
                com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto.GCM_TAG_BYTES.toLong()
            RandomAccessFile(container, "rw").use { raf ->
                raf.seek(secondChunkOffset + 31L)
                val original = raf.readByte()
                raf.seek(secondChunkOffset + 31L)
                raf.writeByte(original.toInt() xor 0x01)
            }

            val source = fixture.factory.createDataSource()
            source.open(
                DataSpec.Builder()
                    .setUri(fixture.uri)
                    .setPosition(PRIVATE_VAULT_CHUNK_SIZE.toLong() + 8L)
                    .setLength(64L)
                    .build(),
            )
            assertThrows(VaultAuthenticationException::class.java) {
                source.read(ByteArray(64), 0, 64)
            }
            source.close()
        }
    }

    @Test
    fun lockedVaultRejectsNewDataSourceOpenBeforeRevealingItemExistence() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = PrivateVaultStorage(context)
        val session = PrivateVaultSession().apply { setConfigured(true) }
        val source = EncryptedVaultDataSource.Factory(PrivateVaultResolver(session, storage)).createDataSource()
        assertThrows(IllegalStateException::class.java) {
            source.open(DataSpec(Uri.parse("maxvault://${UUID.randomUUID()}")))
        }
    }

    private fun readToEnd(source: DataSource, maxExpected: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(maxExpected.coerceAtLeast(32))
        val buffer = ByteArray(4_097)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            assertTrue("DataSource returned a non-progress read", read > 0)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun withVault(size: Int, block: (Fixture) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = PrivateVaultStorage(context)
        storage.cleanupPartials()
        val session = PrivateVaultSession().apply { setConfigured(true) }
        val master = ByteArray(32) { (it * 7 + 11).toByte() }
        session.unlock(master)
        val id = UUID.randomUUID()
        val plain = ByteArray(size) { (it * 19 + 5).toByte() }
        val partial = storage.partialFile(id.toString())
        try {
            PrivateVaultContainerFormat.write(
                ByteArrayInputStream(plain),
                plain.size.toLong(),
                partial,
                id,
                PrivateMediaMetadata("fixture.mp4", "fixture", "video/mp4"),
                master,
            )
            storage.commitPartial(id.toString())
            block(
                Fixture(
                    id = id,
                    plain = plain,
                    storage = storage,
                    uri = Uri.parse("maxvault://$id"),
                    factory = EncryptedVaultDataSource.Factory(PrivateVaultResolver(session, storage)),
                ),
            )
        } finally {
            storage.delete(id.toString())
            partial.delete()
            master.fill(0)
            session.lock()
        }
    }

    private data class Fixture(
        val id: UUID,
        val plain: ByteArray,
        val storage: PrivateVaultStorage,
        val uri: Uri,
        val factory: DataSource.Factory,
    )
}

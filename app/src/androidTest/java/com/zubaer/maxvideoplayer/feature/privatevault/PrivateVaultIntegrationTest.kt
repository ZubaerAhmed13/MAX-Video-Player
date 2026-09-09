package com.zubaer.maxvideoplayer.feature.privatevault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultSession
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultContainerFormat
import com.zubaer.maxvideoplayer.feature.privatevault.datasource.EncryptedVaultDataSource
import com.zubaer.maxvideoplayer.feature.privatevault.datasource.PrivateVaultResolver
import com.zubaer.maxvideoplayer.feature.privatevault.storage.PrivateVaultStorage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@UnstableApi
class PrivateVaultIntegrationTest {
    @Test
    fun encryptedVaultDataSourceReadsNonZeroAndCrossChunkRangesThroughProductionPath() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = PrivateVaultStorage(context)
        storage.cleanupPartials()
        val session = PrivateVaultSession()
        session.setConfigured(true)
        val master = ByteArray(32) { (it * 7 + 11).toByte() }
        session.unlock(master)
        val id = UUID.randomUUID()
        val plain = ByteArray(PRIVATE_VAULT_CHUNK_SIZE + 4096) { (it * 19 + 5).toByte() }
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
            val dataSource = EncryptedVaultDataSource.Factory(PrivateVaultResolver(session, storage)).createDataSource()
            val position = PRIVATE_VAULT_CHUNK_SIZE - 16L
            val length = 128L
            val opened = dataSource.open(
                DataSpec.Builder()
                    .setUri(Uri.parse("maxvault://$id"))
                    .setPosition(position)
                    .setLength(length)
                    .build(),
            )
            assertEquals(length, opened)
            val output = ByteArray(length.toInt())
            var offset = 0
            while (offset < output.size) {
                val read = dataSource.read(output, offset, output.size - offset)
                if (read == C.RESULT_END_OF_INPUT) break
                offset += read
            }
            assertEquals(output.size, offset)
            assertArrayEquals(plain.copyOfRange(position.toInt(), position.toInt() + output.size), output)
            assertEquals(C.RESULT_END_OF_INPUT, dataSource.read(ByteArray(1), 0, 1))
            dataSource.close()
        } finally {
            storage.delete(id.toString())
            master.fill(0)
            session.lock()
        }
    }

    @Test
    fun lockedVaultRejectsNewDataSourceOpen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = PrivateVaultStorage(context)
        val session = PrivateVaultSession().apply { setConfigured(true) }
        val source = EncryptedVaultDataSource.Factory(PrivateVaultResolver(session, storage)).createDataSource()
        assertThrows(IllegalStateException::class.java) {
            source.open(DataSpec(Uri.parse("maxvault://${UUID.randomUUID()}")))
        }
    }
}

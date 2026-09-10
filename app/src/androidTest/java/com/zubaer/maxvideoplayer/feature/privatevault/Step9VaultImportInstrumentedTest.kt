package com.zubaer.maxvideoplayer.feature.privatevault

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultSession
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import com.zubaer.maxvideoplayer.feature.privatevault.persistence.PrivateMediaDao
import com.zubaer.maxvideoplayer.feature.privatevault.persistence.PrivateMediaEntity
import com.zubaer.maxvideoplayer.feature.privatevault.repository.PrivateVaultRepository
import com.zubaer.maxvideoplayer.feature.privatevault.storage.PrivateVaultStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class Step9VaultImportInstrumentedTest {
    @Test
    fun copyAndMoveUseProductionTransactionOrdering() = withVault { context, storage, session, master, beforeIds ->
        val dao = FakePrivateMediaDao()
        val repository = PrivateVaultRepository(context, dao, storage, session)

        val copySource = sourceFile(context, "step9-copy-source.mp4", 2 * 1024 * 1024 + 37)
        val copy = runBlocking { repository.import(Uri.fromFile(copySource), PrivateImportMode.COPY) }
        assertTrue(copy is PrivateImportResult.Success)
        copy as PrivateImportResult.Success
        assertFalse(copy.originalDeleted)
        assertTrue(copySource.isFile)
        assertTrue(storage.containerFile(copy.item.vaultId).isFile)
        assertTrue(dao.rows.containsKey(copy.item.vaultId))

        val moveSource = sourceFile(context, "step9-move-source.mp4", 1024 * 1024 + 19)
        val move = runBlocking { repository.import(Uri.fromFile(moveSource), PrivateImportMode.MOVE) }
        assertTrue(move is PrivateImportResult.Success)
        move as PrivateImportResult.Success
        assertTrue(move.originalDeleted)
        assertFalse(moveSource.exists())
        assertTrue(storage.containerFile(move.item.vaultId).isFile)
        assertTrue(dao.rows.containsKey(move.item.vaultId))

        cleanupNewContainers(storage, beforeIds)
        copySource.delete()
    }

    @Test
    fun failedSourceDeletionReportsEncryptedCopyAndOriginalRemains() = withVault { context, storage, session, _, beforeIds ->
        val dao = FakePrivateMediaDao()
        val repository = PrivateVaultRepository(context, dao, storage, session)
        val parent = File(context.cacheDir, "step9-delete-denied-${System.nanoTime()}").apply { mkdirs() }
        val source = File(parent, "original.mp4").apply { writeBytes(ByteArray(1024 * 1024 + 11) { (it * 3).toByte() }) }
        assertTrue(parent.setWritable(false, false))
        try {
            val result = runBlocking { repository.import(Uri.fromFile(source), PrivateImportMode.MOVE) }
            assertTrue(
                "Move must not claim success when source deletion is denied: $result",
                result is PrivateImportResult.EncryptedCopyCreatedOriginalRemains,
            )
            assertTrue("Original must still exist after failed delete", source.exists())
            val item = (result as PrivateImportResult.EncryptedCopyCreatedOriginalRemains).item
            assertTrue(storage.containerFile(item.vaultId).isFile)
            assertTrue(dao.rows.containsKey(item.vaultId))
        } finally {
            parent.setWritable(true, false)
            source.delete()
            parent.delete()
            cleanupNewContainers(storage, beforeIds)
        }
    }

    @Test
    fun storagePreflightRejectsSparseSourceLargerThanAvailableSpaceWithoutCreatingIndex() = withVault { context, storage, session, _, beforeIds ->
        val dao = FakePrivateMediaDao()
        val repository = PrivateVaultRepository(context, dao, storage, session)
        val source = File(context.cacheDir, "step9-sparse-too-large-${System.nanoTime()}.mp4")
        try {
            val logicalLength = Math.addExact(storage.availableBytes(), 1024L * 1024L)
            RandomAccessFile(source, "rw").use { it.setLength(logicalLength) }
            val result = runBlocking { repository.import(Uri.fromFile(source), PrivateImportMode.COPY) }
            assertTrue(result is PrivateImportResult.Failure)
            assertTrue((result as PrivateImportResult.Failure).message.contains("Not enough device storage"))
            assertTrue(dao.rows.isEmpty())
            assertEquals(beforeIds, storage.opaqueContainerIds())
        } finally {
            source.delete()
            cleanupNewContainers(storage, beforeIds)
        }
    }

    @Test
    fun databaseCommitFailureDeletesCommittedEncryptedArtifactAndKeepsSource() = withVault { context, storage, session, _, beforeIds ->
        val dao = FakePrivateMediaDao(failOnUpsert = true)
        val repository = PrivateVaultRepository(context, dao, storage, session)
        val source = sourceFile(context, "step9-db-failure-source.mp4", 1024 * 1024 + 7)
        try {
            val result = runBlocking { repository.import(Uri.fromFile(source), PrivateImportMode.MOVE) }
            assertTrue(result is PrivateImportResult.Failure)
            assertTrue(source.isFile)
            assertTrue(dao.rows.isEmpty())
            assertEquals(beforeIds, storage.opaqueContainerIds())
        } finally {
            source.delete()
            cleanupNewContainers(storage, beforeIds)
        }
    }

    @Test
    fun unreadableSourceFailsWithoutValidContainerOrDatabaseRow() = withVault { context, storage, session, _, beforeIds ->
        val dao = FakePrivateMediaDao()
        val repository = PrivateVaultRepository(context, dao, storage, session)
        val source = sourceFile(context, "step9-unreadable-source.mp4", 4096)
        assertTrue(source.setReadable(false, false))
        try {
            val result = runBlocking { repository.import(Uri.fromFile(source), PrivateImportMode.COPY) }
            assertTrue("Unreadable source unexpectedly imported: $result", result is PrivateImportResult.Failure)
            assertTrue(dao.rows.isEmpty())
            assertEquals(beforeIds, storage.opaqueContainerIds())
        } finally {
            source.setReadable(true, false)
            source.delete()
            cleanupNewContainers(storage, beforeIds)
        }
    }

    @Test
    fun unwritableVaultDirectoryFailsWithoutDeletingSourceOrLeavingCompletedItem() = withVault { context, storage, session, _, beforeIds ->
        val dao = FakePrivateMediaDao()
        val repository = PrivateVaultRepository(context, dao, storage, session)
        val source = sourceFile(context, "step9-write-failure-source.mp4", 4096)
        assertTrue(storage.directory.setWritable(false, false))
        try {
            val result = runBlocking { repository.import(Uri.fromFile(source), PrivateImportMode.MOVE) }
            assertTrue("Unwritable vault unexpectedly imported: $result", result is PrivateImportResult.Failure)
            assertTrue(source.isFile)
            assertTrue(dao.rows.isEmpty())
            assertEquals(beforeIds, storage.opaqueContainerIds())
        } finally {
            storage.directory.setWritable(true, false)
            source.delete()
            cleanupNewContainers(storage, beforeIds)
        }
    }

    @Test
    fun recoveryRemovesNewPartialAndOrphanButPreservesIndexedContainers() = withVault { context, storage, session, _, beforeIds ->
        val rows = LinkedHashMap<String, PrivateMediaEntity>()
        beforeIds.forEach { id -> rows[id] = placeholderEntity(id) }
        val dao = FakePrivateMediaDao(initialRows = rows)
        val repository = PrivateVaultRepository(context, dao, storage, session)
        val partialId = java.util.UUID.randomUUID().toString()
        val orphanId = java.util.UUID.randomUUID().toString()
        storage.partialFile(partialId).writeBytes(byteArrayOf(1, 2, 3))
        storage.containerFile(orphanId).writeBytes(byteArrayOf(4, 5, 6))

        runBlocking { repository.recoverAbandonedTransactions() }

        assertFalse(storage.partialFile(partialId).exists())
        assertFalse(storage.containerFile(orphanId).exists())
        assertTrue(beforeIds.all { storage.containerFile(it).exists() })
    }

    private fun withVault(
        block: (Context, PrivateVaultStorage, PrivateVaultSession, ByteArray, Set<String>) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = PrivateVaultStorage(context)
        val beforeIds = storage.opaqueContainerIds()
        val session = PrivateVaultSession().apply { setConfigured(true) }
        val master = PrivateVaultCrypto.randomKey()
        session.unlock(master)
        try {
            block(context, storage, session, master, beforeIds)
        } finally {
            storage.directory.setWritable(true, false)
            cleanupNewContainers(storage, beforeIds)
            session.lock()
            PrivateVaultCrypto.zero(master)
        }
    }

    private fun sourceFile(context: Context, name: String, size: Int): File =
        File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            outputStream().buffered().use { output ->
                val chunk = ByteArray(16 * 1024) { index -> (index * 17 + 5).toByte() }
                var remaining = size
                while (remaining > 0) {
                    val count = minOf(chunk.size, remaining)
                    output.write(chunk, 0, count)
                    remaining -= count
                }
            }
        }

    private fun cleanupNewContainers(storage: PrivateVaultStorage, beforeIds: Set<String>) {
        (storage.opaqueContainerIds() - beforeIds).forEach(storage::delete)
        storage.directory.listFiles()?.filter { it.name.endsWith(".partial") }?.forEach { it.delete() }
    }

    private fun placeholderEntity(id: String) = PrivateMediaEntity(
        vaultId = id,
        containerLocation = "$id.maxvault",
        encryptedSizeBytes = 1L,
        originalSizeBytes = 1L,
        createdAtMs = 1L,
        importedAtMs = 1L,
        formatVersion = PRIVATE_VAULT_FORMAT_VERSION,
        status = PrivateMediaStatus.AVAILABLE.name,
    )

    private class FakePrivateMediaDao(
        failOnUpsert: Boolean = false,
        initialRows: Map<String, PrivateMediaEntity> = emptyMap(),
    ) : PrivateMediaDao {
        val rows = LinkedHashMap(initialRows)
        private val failUpsert = failOnUpsert

        override fun observeAll(): Flow<List<PrivateMediaEntity>> = flowOf(rows.values.toList())
        override suspend fun all(): List<PrivateMediaEntity> = rows.values.sortedByDescending { it.importedAtMs }
        override suspend fun get(vaultId: String): PrivateMediaEntity? = rows[vaultId]
        override fun getBlockingForMigrationTest(vaultId: String): PrivateMediaEntity? = rows[vaultId]
        override suspend fun upsert(entity: PrivateMediaEntity) {
            if (failUpsert) throw IllegalStateException("Injected database failure")
            rows[entity.vaultId] = entity
        }
        override suspend fun delete(vaultId: String) { rows.remove(vaultId) }
        override suspend fun clear() { rows.clear() }
    }
}

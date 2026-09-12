package com.zubaer.maxvideoplayer.feature.subtitle

import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.core.database.MaxDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SubtitleRecoveryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun missingAssociationDoesNotBecomeVideoFailureAndCanRelinkWithDelayAndEncodingPreserved() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, MaxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = SubtitleRepository(context, database)
        val runId = "${System.currentTimeMillis()}-${System.nanoTime()}"
        val mediaId = "media-recovery-$runId"
        val original = File(context.cacheDir, "recovery-$runId.en.srt").apply {
            writeText("1\n00:00:00,100 --> 00:00:01,000\nOriginal\n")
        }
        val replacement = File(context.cacheDir, "recovery-$runId.en.relinked.srt").apply {
            writeText("1\n00:00:00,100 --> 00:00:01,000\nReplacement\n")
        }

        try {
            val attached = repository.saveExternalAttachment(
                mediaId,
                SubtitleFileDescriptor(
                    uri = Uri.fromFile(original).toString(),
                    displayName = original.name,
                    mimeType = MimeTypes.APPLICATION_SUBRIP,
                    format = SubtitleFormat.SRT,
                    encoding = SubtitleEncoding.UTF_8,
                ),
                preferred = true,
            )
            repository.setSubtitleDelay(mediaId, -1_250L)
            assertEquals(SubtitleAvailability.AVAILABLE, attached.availability)

            assertTrue(original.delete())
            assertTrue("Original subtitle fixture still exists after delete", !original.exists())
            repository.refreshAvailability(mediaId)
            assertEquals(
                SubtitleAvailability.MISSING,
                repository.externalAttachmentById(mediaId, attached.id)?.availability,
            )
            assertTrue(repository.recoverableErrorFor(mediaId)?.contains("Video playback can continue") == true)

            val descriptor = repository.describeAsync(Uri.fromFile(replacement))
            requireNotNull(descriptor)
            val relinked = repository.relinkExternalAttachment(mediaId, attached.id, descriptor)
            requireNotNull(relinked)
            assertNotEquals(attached.id, relinked.id)
            assertEquals(-1_250L, relinked.delayMs)
            assertEquals(relinked.id, repository.selectedExternalAttachmentId(mediaId))
            assertEquals(SubtitleAvailability.AVAILABLE, relinked.availability)

            assertTrue(repository.setExternalEncoding(mediaId, relinked.id, SubtitleEncoding.WINDOWS_1252))
            withTimeout(5_000L) {
                while (database.subtitleDao().association(relinked.id)?.encoding != SubtitleEncoding.WINDOWS_1252.name) {
                    delay(25L)
                }
            }
            assertEquals(
                SubtitleEncoding.WINDOWS_1252.name,
                database.subtitleDao().association(relinked.id)?.encoding,
            )
        } finally {
            // This test owns an injected in-memory Room instance while SubtitleRepository owns a
            // long-lived SupervisorJob in production. A row becoming visible does not prove that
            // older reconciliation coroutines have all left the repository mutex. Drain those
            // children before closing the test DB so no orphaned persistence work can crash the
            // following instrumentation test with "connection is closed".
            withTimeout(5_000L) {
                repository.awaitPersistenceChildrenForTest()
            }
            original.delete()
            replacement.delete()
            database.close()
        }
    }

    private suspend fun SubtitleRepository.awaitPersistenceChildrenForTest() {
        val field = SubtitleRepository::class.java.getDeclaredField("ioScope").apply { isAccessible = true }
        val scope = field.get(this) as CoroutineScope
        val parent = scope.coroutineContext[Job] ?: return
        while (true) {
            val children = parent.children.toList()
            if (children.isEmpty()) return
            children.forEach { it.join() }
        }
    }
}

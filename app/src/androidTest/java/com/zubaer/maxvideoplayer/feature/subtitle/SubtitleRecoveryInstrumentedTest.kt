package com.zubaer.maxvideoplayer.feature.subtitle

import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zubaer.maxvideoplayer.core.database.MaxDatabase
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
        val original = File(context.cacheDir, "recovery.en.srt").apply {
            writeText("1\n00:00:00,100 --> 00:00:01,000\nOriginal\n")
        }
        val replacement = File(context.cacheDir, "recovery.en.relinked.srt").apply {
            writeText("1\n00:00:00,100 --> 00:00:01,000\nReplacement\n")
        }

        try {
            val repository = SubtitleRepository(context, database)
            val attached = repository.saveExternalAttachment(
                "media-recovery",
                SubtitleFileDescriptor(
                    uri = Uri.fromFile(original).toString(),
                    displayName = original.name,
                    mimeType = MimeTypes.APPLICATION_SUBRIP,
                    format = SubtitleFormat.SRT,
                    encoding = SubtitleEncoding.UTF_8,
                ),
                preferred = true,
            )
            repository.setSubtitleDelay("media-recovery", -1_250L)
            assertEquals(SubtitleAvailability.AVAILABLE, attached.availability)

            assertTrue(original.delete())
            repository.refreshAvailability("media-recovery")
            assertEquals(
                SubtitleAvailability.MISSING,
                repository.externalAttachmentById("media-recovery", attached.id)?.availability,
            )
            assertTrue(repository.recoverableErrorFor("media-recovery")?.contains("Video playback can continue") == true)

            val descriptor = repository.describeAsync(Uri.fromFile(replacement))
            requireNotNull(descriptor)
            val relinked = repository.relinkExternalAttachment("media-recovery", attached.id, descriptor)
            requireNotNull(relinked)
            assertNotEquals(attached.id, relinked.id)
            assertEquals(-1_250L, relinked.delayMs)
            assertEquals(relinked.id, repository.selectedExternalAttachmentId("media-recovery"))
            assertEquals(SubtitleAvailability.AVAILABLE, relinked.availability)

            assertTrue(repository.setExternalEncoding("media-recovery", relinked.id, SubtitleEncoding.WINDOWS_1252))
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
            original.delete()
            replacement.delete()
            database.close()
        }
    }
}

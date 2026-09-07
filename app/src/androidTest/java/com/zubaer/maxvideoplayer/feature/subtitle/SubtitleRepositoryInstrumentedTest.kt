package com.zubaer.maxvideoplayer.feature.subtitle

import android.graphics.Color
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleRepositoryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetPreferences() {
        context.getSharedPreferences("subtitle_preferences_v1", 0).edit().clear().commit()
    }

    @Test
    fun stylePersistsAcrossRepositoryRecreationAndClampsUnsafeValues() {
        val first = SubtitleRepository(context)
        first.setTextScale(9f)
        first.setBottomPaddingFraction(-2f)
        first.setForegroundColor(Color.YELLOW)
        first.setBackgroundColor(0x99000000.toInt())
        first.setEdgeStyle(SubtitleEdgeStyle.DROP_SHADOW)
        first.setApplyEmbeddedStyles(false)
        first.setApplyEmbeddedFontSizes(false)

        val recreated = SubtitleRepository(context)
        val state = recreated.style.value
        assertEquals(2f, state.textScale, 0.0001f)
        assertEquals(0f, state.bottomPaddingFraction, 0.0001f)
        assertEquals(Color.YELLOW, state.foregroundColor)
        assertEquals(0x99000000.toInt(), state.backgroundColor)
        assertEquals(SubtitleEdgeStyle.DROP_SHADOW, state.edgeStyle)
        assertTrue(!state.applyEmbeddedStyles)
        assertTrue(!state.applyEmbeddedFontSizes)
    }

    @Test
    fun legacyCompatibilityAttachmentPersistsPerStableMediaId() {
        val first = SubtitleRepository(context)
        val descriptor = SubtitleFileDescriptor(
            uri = "content://subtitle-provider/document/movie.en.srt",
            displayName = "movie.en.srt",
            mimeType = MimeTypes.APPLICATION_SUBRIP,
            format = SubtitleFormat.SRT,
        )
        first.saveExternalAttachment("media-A", descriptor)

        val recreated = SubtitleRepository(context)
        val attachment = recreated.externalAttachmentFor("media-A")
        assertEquals(descriptor.uri, attachment?.uri)
        assertEquals(descriptor.displayName, attachment?.label)
        assertEquals(MimeTypes.APPLICATION_SUBRIP, attachment?.mimeType)
        assertEquals("en", attachment?.language)
        assertNull(recreated.externalAttachmentFor("media-B"))

        recreated.clearExternalAttachment("media-A")
        assertNull(SubtitleRepository(context).externalAttachmentFor("media-A"))
    }

    @Test
    fun roomBackedRepositoryPersistsMultipleAssociationsSelectionAndDelay() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MaxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val first = SubtitleRepository(context, db)
            val english = first.saveExternalAttachment(
                "media-A",
                SubtitleFileDescriptor(
                    uri = "content://subtitle/movie.en.srt",
                    displayName = "movie.en.srt",
                    mimeType = MimeTypes.APPLICATION_SUBRIP,
                    format = SubtitleFormat.SRT,
                ),
                preferred = true,
            )
            val bangla = first.saveExternalAttachment(
                "media-A",
                SubtitleFileDescriptor(
                    uri = "content://subtitle/movie.bn.ass",
                    displayName = "movie.bn.ass",
                    mimeType = MimeTypes.TEXT_SSA,
                    format = SubtitleFormat.ASS,
                ),
                preferred = false,
            )
            first.selectExternalAttachment("media-A", bangla.id)
            first.setSubtitleDelay("media-A", 750L)

            withTimeout(5_000L) {
                while (true) {
                    val associations = db.subtitleDao().associationsForMedia("media-A")
                    val state = db.subtitleDao().mediaState("media-A")
                    val selectedRow = db.subtitleDao().association(bangla.id)
                    if (
                        associations.size >= 2 &&
                        state?.selectedExternalId == bangla.id &&
                        state.delayMs == 750L &&
                        selectedRow?.isPreferred == true &&
                        selectedRow.delayMs == 750L
                    ) {
                        break
                    }
                    delay(25L)
                }
            }

            val recreated = SubtitleRepository(context, db)
            val restored = recreated.externalAttachmentsFor("media-A")
            assertEquals(2, restored.size)
            assertTrue(restored.any { it.id == english.id })
            assertEquals(bangla.id, recreated.selectedExternalAttachmentId("media-A"))
            assertEquals(750L, recreated.subtitleDelayFor("media-A"))

            // save/select/delay APIs intentionally enqueue idempotent Room reconciliation work.
            // Waiting until the DB happens to contain the expected row is not sufficient: older
            // reconciliation coroutines may still be queued behind the repository mutex. Drain the
            // repository-owned persistence children deterministically before closing this test-owned
            // in-memory DB so an unrelated later instrumentation test cannot inherit an exception.
            withTimeout(5_000L) {
                first.awaitPersistenceChildrenForTest()
            }
        } finally {
            db.close()
        }
    }

    /**
     * Test-only lifetime barrier. SubtitleRepository deliberately owns a long-lived SupervisorJob
     * in production, so there is no product shutdown API to call for an injected test DB. Reflecting
     * the private scope here keeps that lifecycle concern out of the production surface while making
     * the test deterministic: no repository persistence child may still reference DB before close.
     */
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

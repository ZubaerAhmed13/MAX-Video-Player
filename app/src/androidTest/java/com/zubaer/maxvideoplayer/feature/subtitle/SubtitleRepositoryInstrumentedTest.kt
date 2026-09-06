package com.zubaer.maxvideoplayer.feature.subtitle

import android.graphics.Color
import androidx.media3.common.MimeTypes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    fun externalAttachmentPersistsPerStableMediaIdWithoutDuplicatingMedia() {
        val first = SubtitleRepository(context)
        val descriptor = SubtitleFileDescriptor(
            uri = "content://subtitle-provider/document/movie.en.srt",
            displayName = "movie.en.srt",
            mimeType = MimeTypes.APPLICATION_SUBRIP,
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
}

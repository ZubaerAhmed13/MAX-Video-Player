package com.zubaer.maxvideoplayer.feature.subtitle

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFormatPolicyTest {
    @Test
    fun resolvesSupportedExtensionsWhenProviderMimeIsGeneric() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleFormatPolicy.resolveMimeType("movie.en.srt", "text/plain"))
        assertEquals(MimeTypes.TEXT_VTT, SubtitleFormatPolicy.resolveMimeType("movie.vtt", "application/octet-stream"))
        assertEquals(MimeTypes.TEXT_SSA, SubtitleFormatPolicy.resolveMimeType("movie.ass", null))
        assertEquals(MimeTypes.TEXT_SSA, SubtitleFormatPolicy.resolveMimeType("movie.ssa", "application/octet-stream"))
        assertEquals(MimeTypes.APPLICATION_TTML, SubtitleFormatPolicy.resolveMimeType("movie.dfxp", "application/xml"))
        assertEquals(MimeTypes.APPLICATION_TTML, SubtitleFormatPolicy.resolveMimeType("movie.ttml", null))
    }

    @Test
    fun canonicalizesCommonSrtAndAssMimeAliases() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleFormatPolicy.resolveMimeType("whatever.bin", "text/srt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleFormatPolicy.resolveMimeType("whatever.bin", "application/srt"))
        assertEquals(MimeTypes.TEXT_SSA, SubtitleFormatPolicy.resolveMimeType("whatever.bin", "application/x-ass"))
        assertEquals(MimeTypes.TEXT_SSA, SubtitleFormatPolicy.resolveMimeType("whatever.bin", "application/ssa"))
    }

    @Test
    fun rejectsUnknownFileWhenProviderDoesNotIdentifySubtitleFormat() {
        assertNull(SubtitleFormatPolicy.resolveMimeType("notes.txt", "text/plain"))
        assertNull(SubtitleFormatPolicy.resolveMimeType("captions.xyz", "application/octet-stream"))
        assertFalse(SubtitleFormatPolicy.isSupported("captions.xyz", null))
        assertTrue(SubtitleFormatPolicy.isSupported("captions.en.srt", "text/plain"))
    }
}

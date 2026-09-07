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
    fun arbitraryXmlIsNotSilentlyClassifiedAsTtml() {
        val arbitraryXml = "<notes><item>not captions</item></notes>".toByteArray()
        val ttml = "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body/></tt>".toByteArray()

        assertNull(SubtitleFormatPolicy.resolveMimeType("notes.xml", "application/xml", arbitraryXml))
        assertEquals(MimeTypes.APPLICATION_TTML, SubtitleFormatPolicy.resolveMimeType("captions.xml", "application/xml", ttml))
    }

    @Test
    fun detectsUnicodeBomAndUtf8WithoutGuessingLegacyEncoding() {
        assertEquals(SubtitleEncoding.UTF_8, SubtitleEncodingPolicy.detect(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0x31)))
        assertEquals(SubtitleEncoding.UTF_16LE, SubtitleEncodingPolicy.detect(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x31, 0x00)))
        assertEquals(SubtitleEncoding.UTF_16BE, SubtitleEncodingPolicy.detect(byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x00, 0x31)))
        assertEquals(SubtitleEncoding.UTF_8, SubtitleEncodingPolicy.detect("বাংলা English Deutsch العربية 日本語".toByteArray()))
    }

    @Test
    fun rejectsUnknownFileWhenProviderDoesNotIdentifySubtitleFormat() {
        assertNull(SubtitleFormatPolicy.resolveMimeType("notes.txt", "text/plain"))
        assertNull(SubtitleFormatPolicy.resolveMimeType("captions.xyz", "application/octet-stream"))
        assertFalse(SubtitleFormatPolicy.isSupported("captions.xyz", null))
        assertTrue(SubtitleFormatPolicy.isSupported("captions.en.srt", "text/plain"))
    }
}

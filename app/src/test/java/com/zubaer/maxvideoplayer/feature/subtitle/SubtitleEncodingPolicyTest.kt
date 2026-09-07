package com.zubaer.maxvideoplayer.feature.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class SubtitleEncodingPolicyTest {
    @Test
    fun detectsUtf8AndUtf16BomAndDecodesSafely() {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Hello বাংলা".toByteArray(StandardCharsets.UTF_8)
        val utf16Le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Hello".toByteArray(StandardCharsets.UTF_16LE)
        val utf16Be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "Hello".toByteArray(StandardCharsets.UTF_16BE)

        assertEquals(SubtitleEncoding.UTF_8, SubtitleEncodingPolicy.detect(utf8))
        assertEquals(SubtitleEncoding.UTF_16LE, SubtitleEncodingPolicy.detect(utf16Le))
        assertEquals(SubtitleEncoding.UTF_16BE, SubtitleEncodingPolicy.detect(utf16Be))
        assertTrue(SubtitleEncodingPolicy.decodePrefix(utf8).contains("Hello বাংলা"))
        assertEquals("Hello", SubtitleEncodingPolicy.decodePrefix(utf16Le))
        assertEquals("Hello", SubtitleEncodingPolicy.decodePrefix(utf16Be))
    }

    @Test
    fun invalidUtf8CanBeRecoveredWithWindows1252Override() {
        val windows1252 = byteArrayOf('C'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())
        assertEquals(SubtitleEncoding.AUTO, SubtitleEncodingPolicy.detect(windows1252))
        assertEquals("Café", SubtitleEncodingPolicy.decodePrefix(windows1252, SubtitleEncoding.WINDOWS_1252))
    }
}

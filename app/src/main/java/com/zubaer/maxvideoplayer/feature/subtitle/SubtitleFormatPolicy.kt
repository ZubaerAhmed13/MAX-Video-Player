package com.zubaer.maxvideoplayer.feature.subtitle

import androidx.media3.common.MimeTypes
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object SubtitleFormatPolicy {
    private val providerMimeAliases = mapOf(
        "application/x-subrip" to MimeTypes.APPLICATION_SUBRIP,
        "application/srt" to MimeTypes.APPLICATION_SUBRIP,
        "text/srt" to MimeTypes.APPLICATION_SUBRIP,
        "text/vtt" to MimeTypes.TEXT_VTT,
        "text/webvtt" to MimeTypes.TEXT_VTT,
        "text/x-ssa" to MimeTypes.TEXT_SSA,
        "text/x-ass" to MimeTypes.TEXT_SSA,
        "application/x-ssa" to MimeTypes.TEXT_SSA,
        "application/x-ass" to MimeTypes.TEXT_SSA,
        "application/ssa" to MimeTypes.TEXT_SSA,
        "application/ass" to MimeTypes.TEXT_SSA,
        "application/ttml+xml" to MimeTypes.APPLICATION_TTML,
    )

    fun resolveMimeType(displayName: String, providerMime: String?, prefix: ByteArray? = null): String? {
        val canonicalProvider = providerMime?.lowercase()?.let(providerMimeAliases::get)
        if (canonicalProvider != null) return canonicalProvider

        return when (displayName.substringAfterLast('.', "").lowercase()) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "vtt" -> MimeTypes.TEXT_VTT
            "ssa", "ass" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp" -> MimeTypes.APPLICATION_TTML
            "xml" -> if (looksLikeTtml(prefix)) MimeTypes.APPLICATION_TTML else null
            else -> null
        }
    }

    fun resolveFormat(displayName: String, providerMime: String?, prefix: ByteArray? = null): SubtitleFormat? {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val mime = resolveMimeType(displayName, providerMime, prefix) ?: return null
        return when {
            extension == "ass" -> SubtitleFormat.ASS
            extension == "ssa" -> SubtitleFormat.SSA
            mime == MimeTypes.APPLICATION_SUBRIP -> SubtitleFormat.SRT
            mime == MimeTypes.TEXT_VTT -> SubtitleFormat.WEBVTT
            mime == MimeTypes.TEXT_SSA -> SubtitleFormat.SSA
            mime == MimeTypes.APPLICATION_TTML -> SubtitleFormat.TTML
            else -> null
        }
    }

    fun isSupported(displayName: String, providerMime: String?, prefix: ByteArray? = null): Boolean =
        resolveMimeType(displayName, providerMime, prefix) != null

    fun supportedPickerMimeTypes(): Array<String> = arrayOf(
        MimeTypes.APPLICATION_SUBRIP,
        MimeTypes.TEXT_VTT,
        MimeTypes.TEXT_SSA,
        MimeTypes.APPLICATION_TTML,
        "text/srt",
        "application/srt",
        "text/x-ssa",
        "text/x-ass",
        "application/x-ass",
        "text/plain",
        "application/xml",
        "text/xml",
        "application/octet-stream",
    )

    private fun looksLikeTtml(prefix: ByteArray?): Boolean {
        if (prefix == null || prefix.isEmpty()) return false
        val text = SubtitleEncodingPolicy.decodePrefix(prefix).lowercase()
        if (text.isBlank()) return false
        return Regex("<\\s*(?:[a-z0-9_-]+:)?tt(?:\\s|>)").containsMatchIn(text) &&
            ("http://www.w3.org/ns/ttml" in text || "xmlns" in text || "<tt" in text)
    }
}

object SubtitleEncodingPolicy {
    fun detect(bytes: ByteArray): SubtitleEncoding {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return SubtitleEncoding.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return SubtitleEncoding.UTF_16LE
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return SubtitleEncoding.UTF_16BE
        return if (isValidUtf8(bytes)) SubtitleEncoding.UTF_8 else SubtitleEncoding.AUTO
    }

    fun decodePrefix(bytes: ByteArray, requested: SubtitleEncoding = SubtitleEncoding.AUTO): String {
        val encoding = if (requested == SubtitleEncoding.AUTO) detect(bytes) else requested
        return when (encoding) {
            SubtitleEncoding.UTF_16LE -> String(bytes, StandardCharsets.UTF_16LE).removePrefix("\uFEFF")
            SubtitleEncoding.UTF_16BE -> String(bytes, StandardCharsets.UTF_16BE).removePrefix("\uFEFF")
            SubtitleEncoding.WINDOWS_1252 -> String(bytes, charset("windows-1252"))
            SubtitleEncoding.UTF_8, SubtitleEncoding.AUTO -> String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        }
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }
}

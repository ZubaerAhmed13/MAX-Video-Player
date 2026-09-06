package com.zubaer.maxvideoplayer.feature.subtitle

import androidx.media3.common.MimeTypes

/**
 * Deterministic mapping for the text subtitle formats intentionally supported by Step 4.
 * Generic provider MIME types are accepted only when the filename extension is known.
 */
object SubtitleFormatPolicy {
    private val extensionToMime = mapOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "vtt" to MimeTypes.TEXT_VTT,
        "ass" to MimeTypes.TEXT_SSA,
        "ssa" to MimeTypes.TEXT_SSA,
        "ttml" to MimeTypes.APPLICATION_TTML,
        "dfxp" to MimeTypes.APPLICATION_TTML,
        "xml" to MimeTypes.APPLICATION_TTML,
    )

    private val acceptedMimeTypes = setOf(
        MimeTypes.APPLICATION_SUBRIP,
        MimeTypes.TEXT_VTT,
        MimeTypes.TEXT_SSA,
        MimeTypes.APPLICATION_TTML,
        "text/srt",
        "application/srt",
        "application/ssa",
        "application/x-ssa",
        "application/ass",
        "application/x-ass",
    )

    fun resolveMimeType(displayName: String?, providerMimeType: String?): String? {
        val normalizedProvider = providerMimeType?.lowercase()?.substringBefore(';')?.trim()
        val extensionMime = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?.let(extensionToMime::get)

        return when {
            normalizedProvider in acceptedMimeTypes -> canonicalize(normalizedProvider!!)
            extensionMime != null -> extensionMime
            else -> null
        }
    }

    fun isSupported(displayName: String?, providerMimeType: String?): Boolean =
        resolveMimeType(displayName, providerMimeType) != null

    fun supportedPickerMimeTypes(): Array<String> = arrayOf(
        MimeTypes.APPLICATION_SUBRIP,
        MimeTypes.TEXT_VTT,
        MimeTypes.TEXT_SSA,
        MimeTypes.APPLICATION_TTML,
        "text/plain",
        "application/xml",
        "text/xml",
        "application/octet-stream",
    )

    private fun canonicalize(mimeType: String): String = when (mimeType) {
        "text/srt", "application/srt" -> MimeTypes.APPLICATION_SUBRIP
        "application/ssa", "application/x-ssa", "application/ass", "application/x-ass" -> MimeTypes.TEXT_SSA
        else -> mimeType
    }
}

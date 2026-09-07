package com.zubaer.maxvideoplayer.feature.subtitle

import android.graphics.Color

enum class SubtitleEdgeStyle {
    NONE,
    OUTLINE,
    DROP_SHADOW,
    RAISED,
    DEPRESSED,
}

enum class SubtitleSourceType {
    EMBEDDED,
    SIDELOADED_FILE,
    AUTO_DISCOVERED,
    NETWORK_URL,
    FUTURE_PROVIDER,
}

enum class SubtitleFormat {
    SRT,
    WEBVTT,
    SSA,
    ASS,
    TTML,
    UNKNOWN,
}

enum class SubtitleEncoding {
    AUTO,
    UTF_8,
    UTF_16LE,
    UTF_16BE,
    WINDOWS_1252,
}

enum class SubtitleAvailability {
    AVAILABLE,
    MISSING,
    PERMISSION_LOST,
    UNSUPPORTED,
    MALFORMED,
    UNKNOWN,
}

data class SubtitleStyleState(
    val textScale: Float = 1f,
    val foregroundColor: Int = Color.WHITE,
    val backgroundColor: Int = Color.TRANSPARENT,
    val windowColor: Int = Color.TRANSPARENT,
    val edgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.OUTLINE,
    val edgeColor: Int = Color.BLACK,
    val bottomPaddingFraction: Float = 0.08f,
    val applyEmbeddedStyles: Boolean = true,
    val applyEmbeddedFontSizes: Boolean = true,
    val useSystemCaptionStyle: Boolean = false,
)

data class SubtitlePreferenceState(
    val autoLoadMatching: Boolean = true,
    val preferredLanguages: List<String> = listOf("en"),
    val defaultEncoding: SubtitleEncoding = SubtitleEncoding.AUTO,
)

data class ExternalSubtitleAttachment(
    val id: String,
    val mediaId: String,
    val uri: String,
    val label: String,
    val language: String? = null,
    val mimeType: String,
    val format: SubtitleFormat = SubtitleFormat.UNKNOWN,
    val encoding: SubtitleEncoding = SubtitleEncoding.AUTO,
    val sourceType: SubtitleSourceType = SubtitleSourceType.SIDELOADED_FILE,
    val isPreferred: Boolean = false,
    val availability: SubtitleAvailability = SubtitleAvailability.AVAILABLE,
    val delayMs: Long = 0L,
)

data class SubtitleFileDescriptor(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val format: SubtitleFormat = SubtitleFormat.UNKNOWN,
    val encoding: SubtitleEncoding = SubtitleEncoding.AUTO,
    val sizeBytes: Long? = null,
    val sourceType: SubtitleSourceType = SubtitleSourceType.SIDELOADED_FILE,
)

sealed interface SubtitleAttachResult {
    data class Attached(val descriptor: SubtitleFileDescriptor) : SubtitleAttachResult
    data class Unsupported(val displayName: String) : SubtitleAttachResult
    data class TooLarge(val displayName: String, val sizeBytes: Long) : SubtitleAttachResult
    data class Failed(val reason: String) : SubtitleAttachResult
}

sealed interface SubtitleLoadError {
    data object FileUnavailable : SubtitleLoadError
    data object PermissionLost : SubtitleLoadError
    data object UnsupportedFormat : SubtitleLoadError
    data object Malformed : SubtitleLoadError
    data object EncodingError : SubtitleLoadError
    data object Network : SubtitleLoadError
    data class Failure(val message: String) : SubtitleLoadError
}

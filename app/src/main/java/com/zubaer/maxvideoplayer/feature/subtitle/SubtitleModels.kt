package com.zubaer.maxvideoplayer.feature.subtitle

import android.graphics.Color

enum class SubtitleEdgeStyle {
    NONE,
    OUTLINE,
    DROP_SHADOW,
    RAISED,
    DEPRESSED,
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
)

data class ExternalSubtitleAttachment(
    val mediaId: String,
    val uri: String,
    val label: String,
    val language: String? = null,
    val mimeType: String,
)

data class SubtitleFileDescriptor(
    val uri: String,
    val displayName: String,
    val mimeType: String,
)

sealed interface SubtitleAttachResult {
    data class Attached(val descriptor: SubtitleFileDescriptor) : SubtitleAttachResult
    data class Unsupported(val displayName: String) : SubtitleAttachResult
    data class Failed(val reason: String) : SubtitleAttachResult
}

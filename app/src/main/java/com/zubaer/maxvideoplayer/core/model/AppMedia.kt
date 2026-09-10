package com.zubaer.maxvideoplayer.core.model

data class AppMedia(
    val stableId: String,
    val uri: String,
    val title: String,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val rotationDegrees: Int? = null,
    val frameRate: Float? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioSampleRate: Int? = null,
    val audioChannelCount: Int? = null,
    val colorStandard: Int? = null,
    val colorRange: Int? = null,
    val colorTransfer: Int? = null,
    val fileName: String? = null,
    val dateAddedMs: Long? = null,
    val dateModifiedMs: Long? = null,
    val relativePath: String? = null,
    val folderKey: String? = null,
    val folderName: String? = null,
    val sourceId: String? = null,
    val availability: SourceAvailability = SourceAvailability.AVAILABLE,
    val sourceType: MediaSourceType,
)

enum class MediaSourceType {
    MEDIA_STORE,
    SAF,
    NETWORK,
    PRIVATE,
}

enum class SourceAvailability {
    AVAILABLE,
    MISSING,
    PERMISSION_LOST,
    REMOVED_STORAGE,
    UNAVAILABLE,
    CHANGED,
    UNSUPPORTED,
    CORRUPTED,
    UNKNOWN,
}

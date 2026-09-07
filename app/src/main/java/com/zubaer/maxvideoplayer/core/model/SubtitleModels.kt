package com.zubaer.maxvideoplayer.core.model

/** Lightweight, UI-safe description of a Media3 text track. */
data class SubtitleTrackInfo(
    val key: String,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
    val selected: Boolean = false,
    val supported: Boolean = true,
    val external: Boolean = false,
    val forced: Boolean = false,
    val default: Boolean = false,
    val externalAssociationId: String? = null,
)

data class ExternalSubtitleInfo(
    val id: String,
    val label: String,
    val language: String? = null,
    val mimeType: String,
    val format: String,
    val preferred: Boolean,
    val availability: String,
    val delayMs: Long,
)

/** Snapshot of text-track state published by the service-owned playback connection. */
data class SubtitlePlaybackState(
    val enabled: Boolean = true,
    val tracks: List<SubtitleTrackInfo> = emptyList(),
    val selectedTrackKey: String? = null,
    val externalAttached: Boolean = false,
    val externalLabel: String? = null,
    val externalAssociations: List<ExternalSubtitleInfo> = emptyList(),
    val selectedExternalAssociationId: String? = null,
    val delayMs: Long = 0L,
    val recoverableError: String? = null,
)

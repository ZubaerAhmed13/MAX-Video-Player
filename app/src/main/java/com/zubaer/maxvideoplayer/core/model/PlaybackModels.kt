package com.zubaer.maxvideoplayer.core.model

import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderSessionState

enum class DecoderMode {
    AUTO,
    HARDWARE,
    ENHANCED_HARDWARE,
    SOFTWARE,
}

enum class RepeatMode {
    OFF,
    ONE,
    ALL,
}

enum class ImplementationStatus {
    IMPLEMENTED,
    SHARED_IMPLEMENTATION,
    PLANNED,
}

data class DecoderModeAvailability(
    val mode: DecoderMode,
    val status: ImplementationStatus,
    val note: String,
)

object DecoderModeCatalog {
    // Historical Step-1 truth retained for migration/documentation tests.
    val step1: List<DecoderModeAvailability> = listOf(
        DecoderModeAvailability(DecoderMode.AUTO, ImplementationStatus.IMPLEMENTED, "Media3 selects the supported decoder path."),
        DecoderModeAvailability(DecoderMode.HARDWARE, ImplementationStatus.IMPLEMENTED, "Media3/MediaCodec hardware path where supported by the device."),
        DecoderModeAvailability(DecoderMode.ENHANCED_HARDWARE, ImplementationStatus.SHARED_IMPLEMENTATION, "Step 1 shares the MediaCodec path; independent routing belongs to Step 6."),
        DecoderModeAvailability(DecoderMode.SOFTWARE, ImplementationStatus.PLANNED, "Architecture reserved; no fake software decoder is exposed before Step 6."),
    )

    val step6: List<DecoderModeAvailability> = listOf(
        DecoderModeAvailability(DecoderMode.AUTO, ImplementationStatus.IMPLEMENTED, "Ranks compatible decoder candidates with hardware preference and controlled cross-backend fallback."),
        DecoderModeAvailability(DecoderMode.HARDWARE, ImplementationStatus.IMPLEMENTED, "Uses only the single preferred hardware-accelerated video decoder candidate."),
        DecoderModeAvailability(DecoderMode.ENHANCED_HARDWARE, ImplementationStatus.IMPLEMENTED, "Uses a bounded hardware-only candidate chain with fallback among hardware decoders."),
        DecoderModeAvailability(DecoderMode.SOFTWARE, ImplementationStatus.IMPLEMENTED, "Uses only decoders classified by Media3/platform capability as software-only."),
    )
}

sealed interface PlaybackError {
    data object SourceUnavailable : PlaybackError
    data object PermissionLost : PlaybackError
    data object UnsupportedFormat : PlaybackError
    data object UnsupportedDecoder : PlaybackError
    data object MalformedMedia : PlaybackError
    data object Network : PlaybackError
    data object DecoderInitialization : PlaybackError
    data class Failure(val diagnosticCode: Int? = null) : PlaybackError
    data object Unknown : PlaybackError
}

data class PlaybackUiState(
    val connected: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackEnded: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercentage: Int = 0,
    val playbackSpeed: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val currentMediaItemIndex: Int = 0,
    val mediaItemCount: Int = 0,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val subtitles: SubtitlePlaybackState = SubtitlePlaybackState(),
    val decoder: DecoderSessionState = DecoderSessionState(),
    val error: PlaybackError? = null,
)

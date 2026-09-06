package com.zubaer.maxvideoplayer.feature.player

/**
 * Step-3 bounded seek-preview contract. Implementations must never decode an unbounded
 * frame sequence or interrupt the active playback session. A concrete thumbnail/frame
 * extractor may be supplied by a later optimization without changing seeking semantics.
 */
interface SeekPreviewProvider {
    suspend fun preview(mediaStableId: String, mediaUri: String, positionMs: Long, maxWidthPx: Int, maxHeightPx: Int): SeekPreviewFrame?
}

data class SeekPreviewFrame(
    val positionMs: Long,
    val width: Int,
    val height: Int,
    val payload: Any,
)

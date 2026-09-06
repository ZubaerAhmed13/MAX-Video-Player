package com.zubaer.maxvideoplayer.feature.library

import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.SourceAvailability

/**
 * Converts the currently visible library ordering into the lightweight queue contract consumed by
 * the existing service-owned playback architecture. Unavailable rows remain visible in library/
 * playlist recovery flows but are not handed to Media3 as playable queue entries.
 */
object LibraryQueuePlanner {
    fun create(visibleMedia: List<AppMedia>, selected: AppMedia): LibraryPlaybackRequest {
        val playable = visibleMedia.filter { it.availability == SourceAvailability.AVAILABLE }
        val queue = if (playable.any { it.stableId == selected.stableId }) playable else listOf(selected)
        val index = queue.indexOfFirst { it.stableId == selected.stableId }.coerceAtLeast(0)
        return LibraryPlaybackRequest(queue = queue, startIndex = index)
    }
}

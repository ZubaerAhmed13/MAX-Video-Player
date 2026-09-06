package com.zubaer.maxvideoplayer

import androidx.lifecycle.ViewModel
import com.zubaer.maxvideoplayer.core.model.AppMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackLaunch(
    val queue: List<AppMedia>,
    val startIndex: Int,
) {
    val media: AppMedia get() = queue[startIndex.coerceIn(queue.indices)]
}

/** Activity-scoped destination state; playback itself remains service-owned. */
class AppNavigationViewModel : ViewModel() {
    private val _selectedMedia = MutableStateFlow<AppMedia?>(null)
    val selectedMedia: StateFlow<AppMedia?> = _selectedMedia.asStateFlow()

    private val _playbackLaunch = MutableStateFlow<PlaybackLaunch?>(null)
    val playbackLaunch: StateFlow<PlaybackLaunch?> = _playbackLaunch.asStateFlow()

    fun select(media: AppMedia) = selectQueue(listOf(media), 0)

    fun selectQueue(queue: List<AppMedia>, startIndex: Int) {
        if (queue.isEmpty()) return
        val safeIndex = startIndex.coerceIn(queue.indices)
        _playbackLaunch.value = PlaybackLaunch(queue, safeIndex)
        _selectedMedia.value = queue[safeIndex]
    }

    fun clearSelection() {
        _playbackLaunch.value = null
        _selectedMedia.value = null
    }
}

package com.zubaer.maxvideoplayer.core.model

data class PlaybackQueueState(
    val items: List<AppMedia> = emptyList(),
    val currentIndex: Int = -1,
) {
    val current: AppMedia? get() = items.getOrNull(currentIndex)
    val next: AppMedia? get() = items.getOrNull(currentIndex + 1)
    val previous: AppMedia? get() = items.getOrNull(currentIndex - 1)

    fun moveNext(): PlaybackQueueState = if (next != null) copy(currentIndex = currentIndex + 1) else this
    fun movePrevious(): PlaybackQueueState = if (previous != null) copy(currentIndex = currentIndex - 1) else this

    companion object {
        fun of(items: List<AppMedia>, startIndex: Int): PlaybackQueueState {
            if (items.isEmpty()) return PlaybackQueueState()
            return PlaybackQueueState(items, startIndex.coerceIn(items.indices))
        }
    }
}

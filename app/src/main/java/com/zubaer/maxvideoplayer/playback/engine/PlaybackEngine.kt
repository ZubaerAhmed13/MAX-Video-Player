package com.zubaer.maxvideoplayer.playback.engine

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.zubaer.maxvideoplayer.core.model.RepeatMode

interface PlaybackEngine {
    val player: Player

    fun setMedia(item: MediaItem, startPositionMs: Long = 0L, playWhenReady: Boolean = true)
    fun setQueue(items: List<MediaItem>, startIndex: Int = 0, startPositionMs: Long = 0L, playWhenReady: Boolean = true)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setRepeatMode(mode: RepeatMode)
    fun release()
}

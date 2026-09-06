package com.zubaer.maxvideoplayer.playback.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class Media3PlaybackEngine(context: Context) : PlaybackEngine {
    private val exoPlayer = ExoPlayer.Builder(context.applicationContext)
        .build()
        .apply {
            val attributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(attributes, true)
            setHandleAudioBecomingNoisy(true)
        }

    override val player: Player get() = exoPlayer

    override fun setMedia(item: MediaItem, startPositionMs: Long, playWhenReady: Boolean) {
        exoPlayer.setMediaItem(item, startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
    }

    override fun setQueue(items: List<MediaItem>, startIndex: Int, startPositionMs: Long, playWhenReady: Boolean) {
        if (items.isEmpty()) {
            exoPlayer.clearMediaItems()
            return
        }
        exoPlayer.setMediaItems(items, startIndex.coerceIn(items.indices), startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
    }

    override fun play() = exoPlayer.play()
    override fun pause() = exoPlayer.pause()
    override fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
    override fun setPlaybackSpeed(speed: Float) = exoPlayer.setPlaybackSpeed(speed.coerceIn(0.25f, 4f))
    override fun release() = exoPlayer.release()
}

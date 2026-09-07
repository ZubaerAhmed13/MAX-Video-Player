package com.zubaer.maxvideoplayer.playback.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleAwareMediaSourceFactory
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository

@UnstableApi
class Media3PlaybackEngine(
    context: Context,
    subtitleRepository: SubtitleRepository,
) : PlaybackEngine {
    private val exoPlayer = ExoPlayer.Builder(
        context.applicationContext,
        SubtitleAwareMediaSourceFactory(context.applicationContext, subtitleRepository),
    )
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

    override fun setRepeatMode(mode: RepeatMode) {
        exoPlayer.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    override fun release() = exoPlayer.release()
}

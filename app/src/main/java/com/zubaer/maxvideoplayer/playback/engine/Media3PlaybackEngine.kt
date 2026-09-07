package com.zubaer.maxvideoplayer.playback.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import com.zubaer.maxvideoplayer.feature.audio.AudioRepository
import com.zubaer.maxvideoplayer.feature.audio.MaxAudioProcessor
import com.zubaer.maxvideoplayer.feature.audio.ProfessionalMediaSourceFactory
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class Media3PlaybackEngine(
    context: Context,
    subtitleRepository: SubtitleRepository,
    private val audioRepository: AudioRepository,
) : PlaybackEngine {
    val audioProcessor = MaxAudioProcessor(audioRepository)
    private val appContext = context.applicationContext
    private val renderersFactory = object : DefaultRenderersFactory(appContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            // App-owned DSP requires decoded PCM. Device-dependent float/offload paths that can
            // bypass custom processors are not silently advertised as DSP-active.
            .setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(false)
            .setAudioProcessors(arrayOf(audioProcessor))
            .build()
    }

    private val exoPlayer = ExoPlayer.Builder(
        appContext,
        renderersFactory,
        ProfessionalMediaSourceFactory(appContext, subtitleRepository, audioRepository),
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

    init {
        audioRepository.setDspPipelineInstalled(true)
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

    override fun release() {
        exoPlayer.release()
        audioRepository.setDspPipelineInstalled(false)
    }
}

package com.zubaer.maxvideoplayer.playback.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import com.zubaer.maxvideoplayer.feature.audio.AudioRepository
import com.zubaer.maxvideoplayer.feature.audio.MaxAudioProcessor
import com.zubaer.maxvideoplayer.feature.audio.ProfessionalMediaSourceFactory
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderFormatSnapshot
import com.zubaer.maxvideoplayer.feature.decoder.runtime.DecoderRepository
import com.zubaer.maxvideoplayer.feature.decoder.runtime.ProfessionalRenderersFactory
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkRequestRegistry
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class Media3PlaybackEngine(
    context: Context,
    subtitleRepository: SubtitleRepository,
    private val audioRepository: AudioRepository,
    private val decoderRepository: DecoderRepository,
    networkRequestRegistry: NetworkRequestRegistry,
) : PlaybackEngine {
    val audioProcessor = MaxAudioProcessor(audioRepository)
    private val appContext = context.applicationContext
    private val renderersFactory = ProfessionalRenderersFactory(appContext, decoderRepository, audioProcessor)

    private val decoderAnalyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            decoderRepository.recordDecoderInitialized(decoderName, initializationDurationMs)
        }

        override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
            decoderRepository.recordDecoderReleased(decoderName)
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderRepository.recordInputFormat(
                DecoderFormatSnapshot(
                    mimeType = format.sampleMimeType,
                    codecs = format.codecs,
                    width = format.width.takeIf { it > 0 },
                    height = format.height.takeIf { it > 0 },
                    frameRate = format.frameRate.takeIf { it > 0f },
                    colorInfoPresent = format.colorInfo != null,
                ),
            )
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            decoderRepository.addDroppedFrames(droppedFrames)
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            if (C.TRACK_TYPE_VIDEO in exoPlayer.trackSelectionParameters.disabledTrackTypes) {
                decoderRepository.markVideoDecoderInactive("Video decoder inactive — audio-only mode")
            }
        }
    }

    private val decoderFailureListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode !in DECODER_ERROR_CODES) return
            val runtimeFailure = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED
            val hasAlternative = decoderRepository.recordCodecFailure(
                message = "${error.errorCodeName}: ${error.message ?: "video decoder failure"}",
                runtime = runtimeFailure,
            )
            val mode = decoderRepository.requestedMode()
            if (hasAlternative && mode != DecoderMode.HARDWARE) {
                reconfigureVideoDecoder(mode)
            }
        }
    }

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(
        appContext,
        renderersFactory,
        ProfessionalMediaSourceFactory(appContext, subtitleRepository, audioRepository, networkRequestRegistry),
    )
        .build()
        .apply {
            val attributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(attributes, true)
            setHandleAudioBecomingNoisy(true)
            addAnalyticsListener(decoderAnalyticsListener)
            addListener(decoderFailureListener)
        }

    init {
        audioRepository.setDspPipelineInstalled(true)
    }

    override val player: Player get() = exoPlayer

    fun reconfigureVideoDecoder(mode: DecoderMode = decoderRepository.requestedMode()) {
        if (exoPlayer.isReleased || exoPlayer.mediaItemCount == 0) return
        if (C.TRACK_TYPE_VIDEO in exoPlayer.trackSelectionParameters.disabledTrackTypes) {
            decoderRepository.markVideoDecoderInactive("Video decoder inactive — audio-only mode")
            return
        }

        val items = List(exoPlayer.mediaItemCount) { exoPlayer.getMediaItemAt(it) }
        val index = exoPlayer.currentMediaItemIndex.coerceIn(items.indices)
        val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val playWhenReady = exoPlayer.playWhenReady
        val repeatMode = exoPlayer.repeatMode
        val shuffleEnabled = exoPlayer.shuffleModeEnabled
        val playbackParameters = exoPlayer.playbackParameters
        val trackSelectionParameters = exoPlayer.trackSelectionParameters

        decoderRepository.markSwitching()
        exoPlayer.stop()
        exoPlayer.setMediaItems(items, index, positionMs)
        exoPlayer.repeatMode = repeatMode
        exoPlayer.shuffleModeEnabled = shuffleEnabled
        exoPlayer.playbackParameters = playbackParameters
        exoPlayer.trackSelectionParameters = trackSelectionParameters
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
    }

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
        // CastPlayer.release() releases whichever delegate is active. If local playback was active,
        // ExoPlayer may already have been released by the service-owned CastPlayer. Avoid performing
        // a second renderer release while still ensuring the inactive local delegate is released
        // when the service was casting at shutdown.
        if (!exoPlayer.isReleased) {
            exoPlayer.removeAnalyticsListener(decoderAnalyticsListener)
            exoPlayer.removeListener(decoderFailureListener)
            exoPlayer.release()
        }
        decoderRepository.markVideoDecoderInactive("Playback released")
        audioRepository.setDspPipelineInstalled(false)
    }

    private companion object {
        val DECODER_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
        )
    }
}

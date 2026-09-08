package com.zubaer.maxvideoplayer.feature.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkDataSourceRouter
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkRequestRegistry
import com.zubaer.maxvideoplayer.feature.subtitle.OffsetSubtitleParserFactory
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository

/**
 * One authoritative Media3 timeline: primary video/audio/subtitles plus at most the selected
 * external audio source. External subtitles remain configured on the primary MediaItem.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class ProfessionalMediaSourceFactory(
    context: Context,
    private val subtitleRepository: SubtitleRepository,
    private val audioRepository: AudioRepository,
    private val networkRequestRegistry: NetworkRequestRegistry,
) : ForwardingMediaSourceFactory(baseFactory(context, networkRequestRegistry)) {
    private val appContext = context.applicationContext
    private val dataSourceFactory = NetworkDataSourceRouter.Factory(appContext, networkRequestRegistry)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val primary = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(NETWORK_RETRY_COUNT))
            .setSubtitleParserFactory(
                OffsetSubtitleParserFactory(
                    mediaId = mediaItem.mediaId,
                    delayMs = subtitleRepository.subtitleDelayFor(mediaItem.mediaId),
                    subtitleRepository = subtitleRepository,
                ),
            )
            .createMediaSource(mediaItem)

        val external = audioRepository.selectedExternalFor(mediaItem.mediaId) ?: return primary
        val externalItem = MediaItem.Builder()
            .setMediaId("max.external.audio.${external.id}")
            .setUri(external.uri)
            .setMimeType(external.mimeType)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(external.displayName).build())
            .build()
        val externalSource = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(NETWORK_RETRY_COUNT))
            .createMediaSource(externalItem)
        return MergingMediaSource(true, false, primary, externalSource)
    }

    private companion object {
        const val NETWORK_RETRY_COUNT = 5

        fun baseFactory(context: Context, registry: NetworkRequestRegistry): DefaultMediaSourceFactory =
            DefaultMediaSourceFactory(NetworkDataSourceRouter.Factory(context.applicationContext, registry))
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(NETWORK_RETRY_COUNT))
    }
}

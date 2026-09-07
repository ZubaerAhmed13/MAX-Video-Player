package com.zubaer.maxvideoplayer.feature.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.zubaer.maxvideoplayer.feature.subtitle.OffsetSubtitleParserFactory
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository

/**
 * One authoritative Media3 timeline: primary video/audio/subtitles plus at most the selected
 * external audio source. External subtitles remain configured on the primary MediaItem.
 */
@UnstableApi
class ProfessionalMediaSourceFactory(
    context: Context,
    private val subtitleRepository: SubtitleRepository,
    private val audioRepository: AudioRepository,
) : ForwardingMediaSourceFactory(DefaultMediaSourceFactory(context.applicationContext)) {
    private val appContext = context.applicationContext

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val primary = DefaultMediaSourceFactory(appContext)
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
        val externalSource = DefaultMediaSourceFactory(appContext).createMediaSource(externalItem)
        return MergingMediaSource(true, false, primary, externalSource)
    }
}

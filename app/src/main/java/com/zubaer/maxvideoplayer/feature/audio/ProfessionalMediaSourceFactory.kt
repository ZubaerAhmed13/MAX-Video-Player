package com.zubaer.maxvideoplayer.feature.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudPlaybackRegistry
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
    private val cloudPlaybackRegistry: CloudPlaybackRegistry,
) : ForwardingMediaSourceFactory(baseFactory(context, networkRequestRegistry, cloudPlaybackRegistry)) {
    private val appContext = context.applicationContext
    private val dataSourceFactory = NetworkDataSourceRouter.Factory(appContext, networkRequestRegistry, cloudPlaybackRegistry)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val primary = if (mediaItem.localConfiguration?.uri?.scheme.equals("rtsp", ignoreCase = true)) {
            val publicUri = requireNotNull(mediaItem.localConfiguration).uri
            val authenticatedUri = networkRequestRegistry.authenticatedRtspUri(publicUri.toString())
            val privateItem = if (authenticatedUri == publicUri) mediaItem else mediaItem.buildUpon().setUri(authenticatedUri).build()
            val rtspSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(RTSP_TIMEOUT_MS)
                .setDebugLoggingEnabled(false)
                .createMediaSource(privateItem)
            if (privateItem === mediaItem) rtspSource else CredentialHidingMediaSource(rtspSource, mediaItem)
        } else {
            DefaultMediaSourceFactory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(NETWORK_RETRY_COUNT))
                .setSubtitleParserFactory(
                    OffsetSubtitleParserFactory(
                        mediaId = mediaItem.mediaId,
                        delayMs = subtitleRepository.subtitleDelayFor(mediaItem.mediaId),
                        subtitleRepository = subtitleRepository,
                    ),
                )
                .createMediaSource(mediaItem)
        }

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
        const val RTSP_TIMEOUT_MS = 15_000L

        fun baseFactory(
            context: Context,
            registry: NetworkRequestRegistry,
            cloudRegistry: CloudPlaybackRegistry,
        ): DefaultMediaSourceFactory =
            DefaultMediaSourceFactory(NetworkDataSourceRouter.Factory(context.applicationContext, registry, cloudRegistry))
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(NETWORK_RETRY_COUNT))
    }
}

/** Keeps RTSP user-info out of the player timeline and every MediaSession controller surface. */
@UnstableApi
internal class CredentialHidingMediaSource(
    child: MediaSource,
    private val publicMediaItem: MediaItem,
) : WrappingMediaSource(child) {
    override fun getMediaItem(): MediaItem = publicMediaItem

    override fun getInitialTimeline(): Timeline? = super.getInitialTimeline()?.credentialFree()

    override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
        refreshSourceInfo(newTimeline.credentialFree())
    }

    private fun Timeline.credentialFree(): Timeline = object : ForwardingTimeline(this) {
        override fun getWindow(
            windowIndex: Int,
            window: Timeline.Window,
            defaultPositionProjectionUs: Long,
        ): Timeline.Window = super.getWindow(windowIndex, window, defaultPositionProjectionUs).also {
            it.mediaItem = publicMediaItem
        }
    }
}

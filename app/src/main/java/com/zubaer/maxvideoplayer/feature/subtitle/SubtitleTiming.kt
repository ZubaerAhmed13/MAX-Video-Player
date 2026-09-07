package com.zubaer.maxvideoplayer.feature.subtitle

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import kotlin.math.max

object SubtitleTimingPolicy {
    const val MIN_DELAY_MS: Long = -600_000L
    const val MAX_DELAY_MS: Long = 600_000L

    fun clamp(delayMs: Long): Long = delayMs.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
}

@UnstableApi
class OffsetSubtitleParserFactory(
    delayMs: Long,
    private val delegate: SubtitleParser.Factory = DefaultSubtitleParserFactory(),
) : SubtitleParser.Factory {
    private val delayUs = safeMultiplyMicros(SubtitleTimingPolicy.clamp(delayMs))

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int = delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser = OffsetSubtitleParser(delegate.create(format), delayUs)

    private class OffsetSubtitleParser(
        private val delegate: SubtitleParser,
        private val delayUs: Long,
    ) : SubtitleParser {
        override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior

        override fun parse(
            data: ByteArray,
            offset: Int,
            length: Int,
            outputOptions: SubtitleParser.OutputOptions,
            output: Consumer<CuesWithTiming>,
        ) {
            delegate.parse(data, offset, length, outputOptions) { cues ->
                output.accept(shift(cues, delayUs))
            }
        }

        override fun reset() = delegate.reset()
    }

    companion object {
        internal fun shift(cues: CuesWithTiming, delayUs: Long): CuesWithTiming {
            if (cues.startTimeUs == C.TIME_UNSET || delayUs == 0L) return cues
            val rawStart = safeAdd(cues.startTimeUs, delayUs)
            if (rawStart >= 0L) return CuesWithTiming(cues.cues, rawStart, cues.durationUs)

            val clippedDuration = when (cues.durationUs) {
                C.TIME_UNSET -> C.TIME_UNSET
                else -> max(0L, safeAdd(cues.durationUs, rawStart))
            }
            return CuesWithTiming(cues.cues, 0L, clippedDuration)
        }

        private fun safeMultiplyMicros(milliseconds: Long): Long = when {
            milliseconds > Long.MAX_VALUE / 1_000L -> Long.MAX_VALUE
            milliseconds < Long.MIN_VALUE / 1_000L -> Long.MIN_VALUE
            else -> milliseconds * 1_000L
        }

        private fun safeAdd(a: Long, b: Long): Long {
            if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE
            if (b < 0 && a < Long.MIN_VALUE - b) return Long.MIN_VALUE
            return a + b
        }
    }
}

/**
 * Creates a fresh DefaultMediaSourceFactory per MediaItem so the subtitle timing snapshot belongs
 * to that item. This prevents a prefetched queue item from inheriting the previous item's delay.
 */
@UnstableApi
class SubtitleAwareMediaSourceFactory(
    context: Context,
    private val subtitleRepository: SubtitleRepository,
) : ForwardingMediaSourceFactory(DefaultMediaSourceFactory(context.applicationContext)) {
    private val appContext = context.applicationContext

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val delayMs = subtitleRepository.subtitleDelayFor(mediaItem.mediaId)
        return DefaultMediaSourceFactory(appContext)
            .setSubtitleParserFactory(OffsetSubtitleParserFactory(delayMs))
            .createMediaSource(mediaItem)
    }
}

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
import java.nio.charset.StandardCharsets
import kotlin.math.max

object SubtitleTimingPolicy {
    const val MIN_DELAY_MS: Long = -600_000L
    const val MAX_DELAY_MS: Long = 600_000L

    fun clamp(delayMs: Long): Long = delayMs.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
}

/**
 * Wraps Media3's real subtitle parsers. Timing stays in the service-owned playback pipeline, and
 * only side-loaded subtitle byte streams are encoding-normalized. Embedded container tracks are
 * passed through untouched.
 */
@UnstableApi
class OffsetSubtitleParserFactory(
    private val mediaId: String,
    delayMs: Long,
    private val subtitleRepository: SubtitleRepository,
    private val delegate: SubtitleParser.Factory = DefaultSubtitleParserFactory(),
) : SubtitleParser.Factory {
    private val delayUs = safeMultiplyMicros(SubtitleTimingPolicy.clamp(delayMs))

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int = delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser {
        val attachment = resolveExternalAttachment(format)
        return OffsetSubtitleParser(
            delegate = delegate.create(format),
            delayUs = delayUs,
            mediaId = mediaId,
            attachment = attachment,
            subtitleRepository = subtitleRepository,
        )
    }

    private fun resolveExternalAttachment(format: Format): ExternalSubtitleAttachment? {
        val attachments = subtitleRepository.externalAttachmentsFor(mediaId)
        val id = format.id
            ?.takeIf { it.startsWith(EXTERNAL_SUBTITLE_ID_PREFIX) }
            ?.removePrefix(EXTERNAL_SUBTITLE_ID_PREFIX)
        if (id != null) attachments.firstOrNull { it.id == id }?.let { return it }

        return attachments.firstOrNull { candidate ->
            val labelMatches = format.label?.toString() == candidate.label
            val mimeMatches = format.sampleMimeType == candidate.mimeType || format.codecs == candidate.mimeType
            val languageMatches = candidate.language == null || format.language == candidate.language
            labelMatches && mimeMatches && languageMatches
        }
    }

    private class OffsetSubtitleParser(
        private val delegate: SubtitleParser,
        private val delayUs: Long,
        private val mediaId: String,
        private val attachment: ExternalSubtitleAttachment?,
        private val subtitleRepository: SubtitleRepository,
    ) : SubtitleParser {
        override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior

        override fun parse(
            data: ByteArray,
            offset: Int,
            length: Int,
            outputOptions: SubtitleParser.OutputOptions,
            output: Consumer<CuesWithTiming>,
        ) {
            val normalized = attachment?.let { normalizeExternalBytes(data, offset, length, it, subtitleRepository) }
            val parseData = normalized ?: data
            val parseOffset = if (normalized != null) 0 else offset
            val parseLength = if (normalized != null) normalized.size else length

            try {
                delegate.parse(parseData, parseOffset, parseLength, outputOptions) { cues ->
                    output.accept(shift(cues, delayUs))
                }
            } catch (error: RuntimeException) {
                if (attachment == null) throw error
                subtitleRepository.reportExternalParseFailure(mediaId, attachment.id, error.message)
                // A malformed optional subtitle must not convert a playable video into a player error.
            }
        }

        override fun reset() = delegate.reset()
    }

    companion object {
        private const val EXTERNAL_SUBTITLE_ID_PREFIX = "max.external."

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

        private fun normalizeExternalBytes(
            data: ByteArray,
            offset: Int,
            length: Int,
            attachment: ExternalSubtitleAttachment,
            repository: SubtitleRepository,
        ): ByteArray? {
            if (length <= 0 || offset < 0 || offset + length > data.size) return null
            val bytes = data.copyOfRange(offset, offset + length)
            val explicit = attachment.encoding
            val globalDefault = repository.subtitlePreferences.value.defaultEncoding
            val detected = SubtitleEncodingPolicy.detect(bytes)
            val effective = when {
                explicit != SubtitleEncoding.AUTO -> explicit
                detected != SubtitleEncoding.AUTO -> detected
                globalDefault != SubtitleEncoding.AUTO -> globalDefault
                else -> SubtitleEncoding.UTF_8
            }

            val hasUtf8Bom = bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            if (effective == SubtitleEncoding.UTF_8 && !hasUtf8Bom) return null

            val decoded = SubtitleEncodingPolicy.decodePrefix(bytes, effective)
            return decoded.toByteArray(StandardCharsets.UTF_8)
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
 * Creates a fresh DefaultMediaSourceFactory per MediaItem so subtitle timing and parser policy are
 * snapshotted for that exact queue item. A prefetched item cannot inherit the previous item's delay.
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
            .setSubtitleParserFactory(
                OffsetSubtitleParserFactory(
                    mediaId = mediaItem.mediaId,
                    delayMs = delayMs,
                    subtitleRepository = subtitleRepository,
                ),
            )
            .createMediaSource(mediaItem)
    }
}

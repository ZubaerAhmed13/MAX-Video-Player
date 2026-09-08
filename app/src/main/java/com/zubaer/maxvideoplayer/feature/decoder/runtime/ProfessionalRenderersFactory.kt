package com.zubaer.maxvideoplayer.feature.decoder.runtime

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.zubaer.maxvideoplayer.feature.audio.MaxAudioProcessor
import com.zubaer.maxvideoplayer.feature.decoder.selection.ProfessionalMediaCodecSelector

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class ProfessionalRenderersFactory(
    context: Context,
    repository: DecoderRepository,
    private val audioProcessor: MaxAudioProcessor,
) : DefaultRenderersFactory(context.applicationContext) {
    init {
        setMediaCodecSelector(ProfessionalMediaCodecSelector(repository))
        // The mode policy controls which candidates are visible. Decoder fallback is therefore
        // safe globally: Hardware exposes exactly one candidate, Enhanced Hardware exposes only
        // hardware candidates, Software exposes only software candidates, and Auto may cross
        // backend classes deliberately.
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        // Preserve the Step-5 production DSP path exactly: decoded PCM must continue through
        // MaxAudioProcessor regardless of which video decoder is active.
        .setEnableFloatOutput(false)
        .setEnableAudioOutputPlaybackParameters(false)
        .setAudioProcessors(arrayOf(audioProcessor))
        .build()
}

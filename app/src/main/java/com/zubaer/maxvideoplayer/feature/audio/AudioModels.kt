package com.zubaer.maxvideoplayer.feature.audio

import kotlin.math.pow

enum class AudioChannelMode { STEREO, MONO, LEFT, RIGHT }
enum class AudioAvailability { AVAILABLE, MISSING, PERMISSION_LOST, UNSUPPORTED, MALFORMED, UNKNOWN }
enum class AudioRouteType { SPEAKER, WIRED_HEADSET, WIRED_HEADPHONES, BLUETOOTH_A2DP, BLUETOOTH_LE, USB, HDMI, UNKNOWN }
enum class EqualizerPreset { FLAT, BASS, VOCAL, TREBLE, ROCK, CLASSICAL, ELECTRONIC, CUSTOM }
enum class AudioSelectionMode { AUTO, MANUAL }
enum class BackgroundPlaybackMode { PAUSE, CONTINUE_AUDIO, PIP_WHEN_POSSIBLE }

data class AudioTrackInfo(
    val key: String,
    val label: String,
    val language: String?,
    val mimeType: String?,
    val codec: String?,
    val channelCount: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val commentary: Boolean,
    val selected: Boolean,
    val supported: Boolean,
    val external: Boolean,
    /** True when Media3 marks this track as describing the video for blind/low-vision users. */
    val audioDescription: Boolean = false,
)

data class AudioTrackDescriptor(
    val language: String?,
    val label: String?,
    val mimeType: String?,
    val channelCount: Int?,
)

data class ExternalAudioInfo(
    val id: String,
    val stableMediaId: String,
    val uri: String,
    val displayName: String,
    val language: String?,
    val mimeType: String?,
    val addedAtMs: Long,
    val preferred: Boolean,
    val availability: AudioAvailability,
)

data class AudioRouteInfo(
    val type: AudioRouteType = AudioRouteType.UNKNOWN,
    val label: String = "Unknown output",
)

data class AudioDspParameters(
    val equalizerEnabled: Boolean = false,
    val equalizerBandsDb: List<Float> = List(EQ_FREQUENCIES_HZ.size) { 0f },
    val preampDb: Float = 0f,
    val boostDb: Float = 0f,
    val channelMode: AudioChannelMode = AudioChannelMode.STEREO,
    val balance: Float = 0f,
    val delayMs: Long = 0L,
    val revision: Long = 0L,
) {
    fun neutral(): Boolean = !equalizerEnabled && preampDb == 0f && boostDb == 0f &&
        channelMode == AudioChannelMode.STEREO && balance == 0f && delayMs == 0L

    fun linearGain(): Float = 10.0.pow(((preampDb + boostDb) / 20f).toDouble()).toFloat()
}

data class AudioEngineState(
    val selectedTrackKey: String? = null,
    val selectionMode: AudioSelectionMode = AudioSelectionMode.AUTO,
    val tracks: List<AudioTrackInfo> = emptyList(),
    val preferredLanguages: List<String> = listOf("en"),
    val externalAudio: List<ExternalAudioInfo> = emptyList(),
    val selectedExternalId: String? = null,
    /** Per-media offset only. Positive means audio plays later. */
    val audioDelayMs: Long = 0L,
    /** Global compensation profile for the currently detected output route. */
    val routeCompensationMs: Long = 0L,
    val equalizerEnabled: Boolean = false,
    val equalizerBandsDb: List<Float> = List(EQ_FREQUENCIES_HZ.size) { 0f },
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val preampDb: Float = 0f,
    val boostDb: Float = 0f,
    val channelMode: AudioChannelMode = AudioChannelMode.STEREO,
    val balance: Float = 0f,
    val pitch: Float = 1f,
    val audioOnlyMode: Boolean = false,
    val backgroundMode: BackgroundPlaybackMode = BackgroundPlaybackMode.CONTINUE_AUDIO,
    val disableVideoInBackground: Boolean = false,
    val currentRoute: AudioRouteInfo = AudioRouteInfo(),
    val dspPipelineInstalled: Boolean = false,
    val dspAvailable: Boolean = true,
    val dspBypassReason: String? = null,
    val recoverableError: String? = null,
) {
    val effectiveAudioDelayMs: Long
        get() = AudioPolicy.effectiveDelay(audioDelayMs, routeCompensationMs)

    /**
     * Channel-mode and left/right balance are deliberately stereo-only. The DSP preserves
     * multichannel layouts instead of pretending LEFT/RIGHT/MONO semantics are valid for 5.1/7.1.
     */
    val selectedChannelCount: Int?
        get() = tracks.firstOrNull { it.selected }?.channelCount

    val stereoChannelControlsAvailable: Boolean
        get() = selectedChannelCount == 2
}

val EQ_FREQUENCIES_HZ = intArrayOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

object AudioPolicy {
    const val MIN_DELAY_MS = -10_000L
    const val MAX_DELAY_MS = 10_000L
    const val MIN_EQ_DB = -12f
    const val MAX_EQ_DB = 12f
    const val MIN_PREAMP_DB = -12f
    const val MAX_PREAMP_DB = 12f
    const val MIN_BOOST_DB = 0f
    const val MAX_BOOST_DB = 12f
    const val MIN_PITCH = 0.5f
    const val MAX_PITCH = 2f

    fun clampDelay(value: Long): Long = value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    fun effectiveDelay(mediaDelayMs: Long, routeCompensationMs: Long): Long {
        val media = clampDelay(mediaDelayMs)
        val route = clampDelay(routeCompensationMs)
        val sum = when {
            route > 0L && media > Long.MAX_VALUE - route -> Long.MAX_VALUE
            route < 0L && media < Long.MIN_VALUE - route -> Long.MIN_VALUE
            else -> media + route
        }
        return clampDelay(sum)
    }
    fun clampBand(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN_EQ_DB, MAX_EQ_DB) else 0f
    fun clampPreamp(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB) else 0f
    fun clampBoost(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN_BOOST_DB, MAX_BOOST_DB) else 0f
    fun clampBalance(value: Float): Float = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
    fun clampPitch(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN_PITCH, MAX_PITCH) else 1f

    fun preset(preset: EqualizerPreset): List<Float> = when (preset) {
        EqualizerPreset.FLAT -> List(10) { 0f }
        EqualizerPreset.BASS -> listOf(5f, 4f, 3f, 1.5f, 0f, -0.5f, -1f, -1f, -1.5f, -2f)
        EqualizerPreset.VOCAL -> listOf(-2f, -1.5f, -1f, 0f, 1.5f, 3f, 3.5f, 2f, 0f, -1f)
        EqualizerPreset.TREBLE -> listOf(-2f, -1.5f, -1f, -0.5f, 0f, 1f, 2f, 3f, 4f, 4.5f)
        EqualizerPreset.ROCK -> listOf(3.5f, 2.5f, 1f, -1f, -1.5f, 0.5f, 2f, 3f, 3.5f, 3f)
        EqualizerPreset.CLASSICAL -> listOf(2f, 1.5f, 0.5f, 0f, -0.5f, -0.5f, 0f, 1.5f, 2.5f, 3f)
        EqualizerPreset.ELECTRONIC -> listOf(4f, 3f, 1.5f, 0f, -1f, 0f, 1.5f, 3f, 4f, 3f)
        EqualizerPreset.CUSTOM -> List(10) { 0f }
    }
}

package com.zubaer.maxvideoplayer.core.device

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

data class ResolutionCapability(
    val width: Int,
    val height: Int,
    val supportedAt30Fps: Boolean,
    val supportedAt60Fps: Boolean? = null,
)

data class CodecCapability(
    val name: String,
    val encoder: Boolean,
    val mimeTypes: List<String>,
    val hardwareAccelerated: Boolean?,
    val adaptivePlayback: Boolean,
    val securePlayback: Boolean,
    val profileLevels: List<String>,
    val resolutionTargets: Map<String, ResolutionCapability>,
    val softwareOnly: Boolean? = null,
    val vendor: Boolean? = null,
    val tunneledPlayback: Boolean = false,
    val lowLatency: Boolean? = null,
    val colorFormats: List<Int> = emptyList(),
)

enum class DeviceDecoderBackend {
    HARDWARE,
    SOFTWARE,
    UNKNOWN,
}

data class DecoderMimeCapability(
    val name: String,
    val mimeType: String,
    val backend: DeviceDecoderBackend,
    val hardwareAccelerated: Boolean?,
    val softwareOnly: Boolean?,
    val vendor: Boolean?,
    val adaptivePlayback: Boolean,
    val securePlayback: Boolean,
    val tunneledPlayback: Boolean,
    val lowLatency: Boolean?,
    val profileLevels: List<String>,
    val colorFormats: List<Int>,
    val resolutionTargets: Map<String, ResolutionCapability>,
)

data class DeviceDecoderProfile(
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val abis: List<String>,
    val availableVideoDecoders: List<DecoderMimeCapability>,
    val hardwareDecoderNames: List<String>,
    val softwareDecoderNames: List<String>,
    val unknownDecoderNames: List<String>,
    val supportedMimeTypes: List<String>,
    val collectedAtMs: Long,
)

data class DeviceCapabilityProfile(
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val abis: List<String>,
    val cpuCoreCount: Int,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val codecs: List<CodecCapability>,
)

class DeviceCapabilityProvider(private val context: Context) {
    @Volatile
    private var cachedDecoderProfile: DeviceDecoderProfile? = null
    private val decoderProfileLock = Any()

    fun collect(): DeviceCapabilityProfile {
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.map { mapCodec(it) }
        return DeviceCapabilityProfile(
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            abis = Build.SUPPORTED_ABIS.toList(),
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            totalMemoryBytes = memory.totalMem,
            availableMemoryBytes = memory.availMem,
            codecs = codecInfos,
        )
    }

    /**
     * Returns an immutable snapshot of video decoder capabilities. The snapshot is cached because
     * the device codec inventory is effectively static for the lifetime of an app process. Callers
     * can request an explicit refresh after an app/device update or from diagnostics UI.
     */
    fun collectDecoderProfile(forceRefresh: Boolean = false): DeviceDecoderProfile {
        if (!forceRefresh) cachedDecoderProfile?.let { return it }
        return synchronized(decoderProfileLock) {
            if (!forceRefresh) cachedDecoderProfile?.let { return@synchronized it }
            scanDecoderProfile().also { cachedDecoderProfile = it }
        }
    }

    private fun scanDecoderProfile(): DeviceDecoderProfile {
        val decoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .asSequence()
            .filterNot { it.isEncoder }
            .flatMap { info ->
                info.supportedTypes.asSequence()
                    .filter { it.startsWith("video/", ignoreCase = true) }
                    .map { mime -> mapDecoderMime(info, mime) }
            }
            .sortedWith(
                compareBy<DecoderMimeCapability> { it.mimeType.lowercase() }
                    .thenBy { it.backend.ordinal }
                    .thenBy { it.name.lowercase() },
            )
            .toList()

        return DeviceDecoderProfile(
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            abis = Build.SUPPORTED_ABIS.toList(),
            availableVideoDecoders = decoders,
            hardwareDecoderNames = decoders.filter { it.backend == DeviceDecoderBackend.HARDWARE }.map { it.name }.distinct(),
            softwareDecoderNames = decoders.filter { it.backend == DeviceDecoderBackend.SOFTWARE }.map { it.name }.distinct(),
            unknownDecoderNames = decoders.filter { it.backend == DeviceDecoderBackend.UNKNOWN }.map { it.name }.distinct(),
            supportedMimeTypes = decoders.map { it.mimeType }.distinct().sorted(),
            collectedAtMs = System.currentTimeMillis(),
        )
    }

    private fun mapCodec(info: MediaCodecInfo): CodecCapability {
        val mimes = info.supportedTypes.toList()
        var adaptive = false
        var secure = false
        var tunneled = false
        var lowLatency: Boolean? = null
        val profiles = mutableListOf<String>()
        val colors = linkedSetOf<Int>()
        val resolutions = linkedMapOf<String, ResolutionCapability>()
        val videoMimes = mimes.filter { it.startsWith("video/", ignoreCase = true) }
        videoMimes.forEachIndexed { index, videoMime ->
            runCatching {
                val caps = info.getCapabilitiesForType(videoMime)
                adaptive = adaptive || caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback)
                secure = secure || caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback)
                tunneled = tunneled || caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback)
                if (Build.VERSION.SDK_INT >= 30) {
                    lowLatency = (lowLatency == true) || caps.isFeatureSupported("low-latency")
                }
                caps.profileLevels.forEach { profiles += "$videoMime:${it.profile}:${it.level}" }
                caps.colorFormats.forEach(colors::add)
                if (index == 0) resolutions.putAll(resolutionTargets(caps.videoCapabilities))
            }
        }
        return CodecCapability(
            name = info.name,
            encoder = info.isEncoder,
            mimeTypes = mimes,
            hardwareAccelerated = hardwareAccelerated(info),
            adaptivePlayback = adaptive,
            securePlayback = secure,
            profileLevels = profiles,
            resolutionTargets = resolutions,
            softwareOnly = softwareOnly(info),
            vendor = vendor(info),
            tunneledPlayback = tunneled,
            lowLatency = lowLatency,
            colorFormats = colors.toList(),
        )
    }

    private fun mapDecoderMime(info: MediaCodecInfo, mimeType: String): DecoderMimeCapability {
        val capabilities = runCatching { info.getCapabilitiesForType(mimeType) }.getOrNull()
        val hardware = hardwareAccelerated(info)
        val software = softwareOnly(info)
        return DecoderMimeCapability(
            name = info.name,
            mimeType = mimeType,
            backend = classifyBackend(info.name, hardware, software),
            hardwareAccelerated = hardware,
            softwareOnly = software,
            vendor = vendor(info),
            adaptivePlayback = capabilities?.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback) == true,
            securePlayback = capabilities?.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback) == true,
            tunneledPlayback = capabilities?.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback) == true,
            lowLatency = if (Build.VERSION.SDK_INT >= 30 && capabilities != null) {
                capabilities.isFeatureSupported("low-latency")
            } else {
                null
            },
            profileLevels = capabilities?.profileLevels?.map { "${it.profile}:${it.level}" }.orEmpty(),
            colorFormats = capabilities?.colorFormats?.toList().orEmpty(),
            resolutionTargets = resolutionTargets(capabilities?.videoCapabilities),
        )
    }

    private fun resolutionTargets(videoCapabilities: MediaCodecInfo.VideoCapabilities?): Map<String, ResolutionCapability> {
        if (videoCapabilities == null) return emptyMap()
        return linkedMapOf<String, ResolutionCapability>().apply {
            listOf(
                "720p" to (1280 to 720),
                "1080p" to (1920 to 1080),
                "1440p" to (2560 to 1440),
                "2160p" to (3840 to 2160),
            ).forEach { (label, size) ->
                val at30 = runCatching {
                    videoCapabilities.areSizeAndRateSupported(size.first, size.second, 30.0)
                }.getOrDefault(false)
                val at60 = runCatching {
                    videoCapabilities.areSizeAndRateSupported(size.first, size.second, 60.0)
                }.getOrNull()
                put(label, ResolutionCapability(size.first, size.second, at30, at60))
            }
        }
    }

    private fun hardwareAccelerated(info: MediaCodecInfo): Boolean? =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else null

    private fun softwareOnly(info: MediaCodecInfo): Boolean? =
        if (Build.VERSION.SDK_INT >= 29) info.isSoftwareOnly else legacySoftwareClassification(info.name)

    private fun vendor(info: MediaCodecInfo): Boolean? =
        if (Build.VERSION.SDK_INT >= 29) info.isVendor else null

    private fun classifyBackend(name: String, hardware: Boolean?, software: Boolean?): DeviceDecoderBackend = when {
        software == true -> DeviceDecoderBackend.SOFTWARE
        hardware == true -> DeviceDecoderBackend.HARDWARE
        else -> if (legacySoftwareClassification(name) == true) DeviceDecoderBackend.SOFTWARE else DeviceDecoderBackend.UNKNOWN
    }

    private fun legacySoftwareClassification(name: String): Boolean? {
        val normalized = name.lowercase()
        return when {
            normalized.startsWith("omx.google.") -> true
            normalized.startsWith("c2.android.") -> true
            normalized.startsWith("omx.ffmpeg.") -> true
            normalized.startsWith("ffmpeg.") -> true
            else -> null
        }
    }
}

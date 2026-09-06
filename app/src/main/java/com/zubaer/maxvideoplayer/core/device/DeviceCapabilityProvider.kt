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

    private fun mapCodec(info: MediaCodecInfo): CodecCapability {
        val mimes = info.supportedTypes.toList()
        var adaptive = false
        var secure = false
        val profiles = mutableListOf<String>()
        val resolutions = linkedMapOf<String, ResolutionCapability>()
        val videoMime = mimes.firstOrNull { it.startsWith("video/") }
        if (videoMime != null) {
            runCatching {
                val caps = info.getCapabilitiesForType(videoMime)
                adaptive = caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback)
                secure = caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback)
                caps.profileLevels.forEach { profiles += "${it.profile}:${it.level}" }
                val vc = caps.videoCapabilities
                listOf("720p" to (1280 to 720), "1080p" to (1920 to 1080), "1440p" to (2560 to 1440), "2160p" to (3840 to 2160)).forEach { (label, size) ->
                    val supported = runCatching { vc.areSizeAndRateSupported(size.first, size.second, 30.0) }.getOrDefault(false)
                    resolutions[label] = ResolutionCapability(size.first, size.second, supported)
                }
            }
        }
        return CodecCapability(
            name = info.name,
            encoder = info.isEncoder,
            mimeTypes = mimes,
            hardwareAccelerated = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else null,
            adaptivePlayback = adaptive,
            securePlayback = secure,
            profileLevels = profiles,
            resolutionTargets = resolutions,
        )
    }
}

package com.zubaer.maxvideoplayer.feature.decoder.capability

import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderBackendType

object CodecClassifier {
    /**
     * Modern platform/Media3 flags are authoritative. Name heuristics are used only as a
     * conservative legacy fallback and deliberately return UNKNOWN for ambiguous vendor names.
     */
    fun classify(
        hardwareAccelerated: Boolean?,
        softwareOnly: Boolean?,
        codecName: String,
    ): DecoderBackendType {
        if (softwareOnly == true) return DecoderBackendType.SOFTWARE
        if (hardwareAccelerated == true) return DecoderBackendType.HARDWARE
        if (hardwareAccelerated == false && softwareOnly == false) return DecoderBackendType.UNKNOWN

        val normalized = codecName.lowercase()
        return when {
            normalized.startsWith("omx.google.") -> DecoderBackendType.SOFTWARE
            normalized.startsWith("c2.android.") -> DecoderBackendType.SOFTWARE
            normalized.startsWith("omx.ffmpeg.") -> DecoderBackendType.SOFTWARE
            normalized.startsWith("ffmpeg.") -> DecoderBackendType.SOFTWARE
            else -> DecoderBackendType.UNKNOWN
        }
    }
}

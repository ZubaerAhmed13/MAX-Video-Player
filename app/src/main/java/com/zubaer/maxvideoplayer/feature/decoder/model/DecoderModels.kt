package com.zubaer.maxvideoplayer.feature.decoder.model

import com.zubaer.maxvideoplayer.core.model.DecoderMode

enum class DecoderBackendType {
    HARDWARE,
    SOFTWARE,
    UNKNOWN,
}

enum class DecoderSupportStatus {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

data class CodecProfileLevel(
    val profile: Int,
    val level: Int,
)

data class DecoderCandidate(
    val name: String,
    val canonicalName: String? = null,
    val mimeType: String,
    val backend: DecoderBackendType,
    val hardwareAccelerated: Boolean,
    val softwareOnly: Boolean,
    val vendor: Boolean,
    val secure: Boolean,
    val tunneling: Boolean,
    val adaptive: Boolean = false,
    val profileLevels: List<CodecProfileLevel> = emptyList(),
    val sizeRateSupport: DecoderSupportStatus = DecoderSupportStatus.UNKNOWN,
    val platformPriority: Int = 0,
)

data class VideoDecoderRequirement(
    val mimeType: String,
    val profile: Int? = null,
    val level: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val secureRequired: Boolean = false,
)

data class DecoderDecision(
    val requestedMode: DecoderMode,
    val candidates: List<DecoderCandidate>,
    val rejectedCandidates: List<DecoderCandidate> = emptyList(),
    val reason: String,
)

enum class DecoderFailureCode {
    NO_COMPATIBLE_DECODER,
    DECODER_INIT_FAILED,
    DECODER_RUNTIME_FAILED,
    PROFILE_UNSUPPORTED,
    SIZE_RATE_UNSUPPORTED,
    SECURE_DECODER_REQUIRED,
    SOFTWARE_BACKEND_UNAVAILABLE,
    UNKNOWN,
}

data class DecoderFailure(
    val code: DecoderFailureCode,
    val decoderName: String? = null,
    val message: String,
    val recoverable: Boolean,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class DecoderFallbackEvent(
    val decoderName: String?,
    val backend: DecoderBackendType?,
    val failureCode: DecoderFailureCode,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class DecoderFormatSnapshot(
    val mimeType: String? = null,
    val codecs: String? = null,
    val profile: Int? = null,
    val level: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val colorInfoPresent: Boolean = false,
)

data class DecoderDiagnostics(
    val requestedMode: DecoderMode = DecoderMode.AUTO,
    val effectiveMode: DecoderMode? = null,
    val effectiveBackend: DecoderBackendType? = null,
    val activeDecoderName: String? = null,
    val activeDecoderCanonicalName: String? = null,
    val hardwareAccelerated: Boolean? = null,
    val softwareOnly: Boolean? = null,
    val vendor: Boolean? = null,
    val secure: Boolean? = null,
    val inputFormat: DecoderFormatSnapshot = DecoderFormatSnapshot(),
    val decoderInitializationDurationMs: Long? = null,
    val droppedFrames: Long = 0L,
    val fallbackCount: Int = 0,
    val fallbackHistory: List<DecoderFallbackEvent> = emptyList(),
    val lastFailure: DecoderFailure? = null,
    val candidateNames: List<String> = emptyList(),
    val videoDecoderActive: Boolean = false,
    val switching: Boolean = false,
    val statusMessage: String? = null,
)

data class DecoderSessionState(
    val mediaId: String? = null,
    val requestedMode: DecoderMode = DecoderMode.AUTO,
    val usingMediaOverride: Boolean = false,
    val diagnostics: DecoderDiagnostics = DecoderDiagnostics(),
)

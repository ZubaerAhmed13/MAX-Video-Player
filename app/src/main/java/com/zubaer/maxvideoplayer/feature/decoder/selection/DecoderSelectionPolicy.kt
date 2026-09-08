package com.zubaer.maxvideoplayer.feature.decoder.selection

import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderBackendType
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderCandidate
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderDecision
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderSupportStatus
import com.zubaer.maxvideoplayer.feature.decoder.model.VideoDecoderRequirement

object DecoderSelectionPolicy {
    fun decide(
        mode: DecoderMode,
        candidates: List<DecoderCandidate>,
        requirement: VideoDecoderRequirement? = null,
        sessionRejected: Set<String> = emptySet(),
    ): DecoderDecision {
        val compatible = candidates
            .filterNot { it.name in sessionRejected }
            .filter { candidate -> requirement == null || supports(candidate, requirement) != DecoderSupportStatus.UNSUPPORTED }

        val ordered = compatible.sortedWith(
            compareBy<DecoderCandidate> { compatibilityRank(it, requirement) }
                .thenBy { backendRank(mode, it.backend) }
                .thenBy { it.platformPriority }
                .thenBy { it.name.lowercase() },
        )

        val selected = when (mode) {
            DecoderMode.HARDWARE -> ordered.filter { it.backend == DecoderBackendType.HARDWARE }.take(1)
            DecoderMode.ENHANCED_HARDWARE -> ordered.filter { it.backend == DecoderBackendType.HARDWARE }
            DecoderMode.SOFTWARE -> ordered.filter { it.backend == DecoderBackendType.SOFTWARE }
            DecoderMode.AUTO -> ordered
        }
        val selectedNames = selected.mapTo(hashSetOf()) { it.name }
        return DecoderDecision(
            requestedMode = mode,
            candidates = selected,
            rejectedCandidates = candidates.filter { it.name !in selectedNames },
            reason = when (mode) {
                DecoderMode.AUTO -> "Auto ranks compatible hardware first, then conservatively classified unknown codecs, then software."
                DecoderMode.HARDWARE -> "Hardware uses the single preferred compatible hardware-accelerated decoder."
                DecoderMode.ENHANCED_HARDWARE -> "Enhanced Hardware exposes the compatible hardware candidate chain for bounded fallback."
                DecoderMode.SOFTWARE -> "Software exposes only decoders classified as software-only."
            },
        )
    }

    fun supports(candidate: DecoderCandidate, requirement: VideoDecoderRequirement): DecoderSupportStatus {
        if (!candidate.mimeType.equals(requirement.mimeType, ignoreCase = true)) return DecoderSupportStatus.UNSUPPORTED
        if (requirement.secureRequired && !candidate.secure) return DecoderSupportStatus.UNSUPPORTED
        if (candidate.sizeRateSupport == DecoderSupportStatus.UNSUPPORTED) return DecoderSupportStatus.UNSUPPORTED

        val profile = requirement.profile
        if (profile != null && candidate.profileLevels.isNotEmpty()) {
            val matchingProfile = candidate.profileLevels.filter { it.profile == profile }
            if (matchingProfile.isEmpty()) return DecoderSupportStatus.UNSUPPORTED
            val requiredLevel = requirement.level
            if (requiredLevel != null && matchingProfile.none { it.level >= requiredLevel }) {
                return DecoderSupportStatus.UNSUPPORTED
            }
        }

        val hasUnknownRequirement = requirement.profile == null ||
            requirement.width == null ||
            requirement.height == null ||
            candidate.profileLevels.isEmpty() ||
            candidate.sizeRateSupport == DecoderSupportStatus.UNKNOWN
        return if (hasUnknownRequirement) DecoderSupportStatus.UNKNOWN else DecoderSupportStatus.SUPPORTED
    }

    private fun compatibilityRank(candidate: DecoderCandidate, requirement: VideoDecoderRequirement?): Int = when {
        requirement == null -> 1
        supports(candidate, requirement) == DecoderSupportStatus.SUPPORTED -> 0
        else -> 1
    }

    private fun backendRank(mode: DecoderMode, backend: DecoderBackendType): Int = when (mode) {
        DecoderMode.AUTO -> when (backend) {
            DecoderBackendType.HARDWARE -> 0
            DecoderBackendType.UNKNOWN -> 1
            DecoderBackendType.SOFTWARE -> 2
        }
        DecoderMode.HARDWARE,
        DecoderMode.ENHANCED_HARDWARE,
        -> if (backend == DecoderBackendType.HARDWARE) 0 else 1
        DecoderMode.SOFTWARE -> if (backend == DecoderBackendType.SOFTWARE) 0 else 1
    }
}

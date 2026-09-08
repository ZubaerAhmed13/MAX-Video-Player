package com.zubaer.maxvideoplayer.feature.cast

import android.net.Uri

/**
 * Truthful Cast source classification. A Cast receiver cannot dereference Android-only content
 * URIs or app-private network schemes, so those sources are never passed through as if they were
 * receiver-reachable URLs.
 */
class CastSourceResolver {
    fun resolve(
        uriString: String,
        isAdaptiveManifest: Boolean = false,
        requiresPrivateHeaders: Boolean = false,
        receiverReachable: Boolean = true,
    ): CastSourceDecision {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return CastSourceDecision(CastSourceMode.UNSUPPORTED_CAST, "Invalid media URI")
        val scheme = uri.scheme?.lowercase().orEmpty()

        if (scheme == "rtsp") {
            return CastSourceDecision(
                CastSourceMode.UNSUPPORTED_CAST,
                "RTSP casting is not supported by this receiver path",
            )
        }

        if (isAdaptiveManifest && requiresPrivateHeaders) {
            return CastSourceDecision(
                CastSourceMode.MANIFEST_RELAY,
                "Authenticated adaptive stream requires credential-preserving manifest relay",
            )
        }

        if (scheme in DIRECT_SCHEMES && receiverReachable && !requiresPrivateHeaders) {
            return CastSourceDecision(
                CastSourceMode.DIRECT_CAST,
                "Receiver can fetch the resource directly",
            )
        }

        if (scheme in RELAY_SCHEMES || requiresPrivateHeaders || !receiverReachable) {
            return CastSourceDecision(
                CastSourceMode.LOCAL_RELAY,
                "Receiver cannot safely fetch the source directly",
            )
        }

        return CastSourceDecision(
            CastSourceMode.UNSUPPORTED_CAST,
            "Source scheme is not certified for Cast",
        )
    }

    private companion object {
        val DIRECT_SCHEMES = setOf("http", "https")
        val RELAY_SCHEMES = setOf("content", "file", "maxsmb", "ftp", "ftps", "maxcloud", "cloud")
    }
}

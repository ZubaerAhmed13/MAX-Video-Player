package com.zubaer.maxvideoplayer.feature.cast

/**
 * Truthful Cast source classification. A Cast receiver cannot dereference Android-only content
 * URIs or app-private network schemes, so those sources are never passed through as if they were
 * receiver-reachable URLs.
 *
 * This classifier intentionally avoids android.net.Uri because it is pure policy logic and must
 * remain deterministic in local JVM tests as well as Android instrumentation.
 */
class CastSourceResolver {
    fun resolve(
        uriString: String,
        isAdaptiveManifest: Boolean = false,
        requiresPrivateHeaders: Boolean = false,
        receiverReachable: Boolean = true,
    ): CastSourceDecision {
        val scheme = schemeOf(uriString)
            ?: return CastSourceDecision(CastSourceMode.UNSUPPORTED_CAST, "Invalid media URI")

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

    private fun schemeOf(value: String): String? {
        val trimmed = value.trim()
        val separator = trimmed.indexOf(':')
        if (separator <= 0) return null
        val candidate = trimmed.substring(0, separator)
        if (!SCHEME_PATTERN.matches(candidate)) return null
        return candidate.lowercase()
    }

    private companion object {
        val SCHEME_PATTERN = Regex("[A-Za-z][A-Za-z0-9+.-]*")
        val DIRECT_SCHEMES = setOf("http", "https")
        val RELAY_SCHEMES = setOf("content", "file", "maxsmb", "ftp", "ftps", "maxcloud", "cloud")
    }
}

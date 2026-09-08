package com.zubaer.maxvideoplayer.feature.cast

import java.security.SecureRandom

object CastRelaySecurity {
    private val secureRandom = SecureRandom()

    /** Generates a 256-bit session token. The token must never be logged or persisted. */
    fun newSessionToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

data class ByteRange(
    val startInclusive: Long,
    val endInclusive: Long,
) {
    init {
        require(startInclusive >= 0L)
        require(endInclusive >= startInclusive)
    }

    val length: Long get() = endInclusive - startInclusive + 1L
}

sealed interface RangeParseResult {
    data object NotRequested : RangeParseResult
    data class Valid(val range: ByteRange) : RangeParseResult
    data class Unsatisfiable(val totalLength: Long?) : RangeParseResult
}

/** Strict single-range parser for Cast relay GET/HEAD requests. */
object CastRangeParser {
    fun parse(header: String?, totalLength: Long?): RangeParseResult {
        if (header.isNullOrBlank()) return RangeParseResult.NotRequested
        if (!header.startsWith("bytes=", ignoreCase = true)) return RangeParseResult.Unsatisfiable(totalLength)
        if (header.contains(',')) return RangeParseResult.Unsatisfiable(totalLength)

        val spec = header.substringAfter('=').trim()
        val parts = spec.split('-', limit = 2)
        if (parts.size != 2) return RangeParseResult.Unsatisfiable(totalLength)
        val startText = parts[0].trim()
        val endText = parts[1].trim()

        if (startText.isEmpty()) {
            val length = totalLength ?: return RangeParseResult.Unsatisfiable(null)
            val suffix = endText.toLongOrNull()?.takeIf { it > 0L }
                ?: return RangeParseResult.Unsatisfiable(totalLength)
            if (length <= 0L) return RangeParseResult.Unsatisfiable(totalLength)
            val actual = suffix.coerceAtMost(length)
            return RangeParseResult.Valid(ByteRange(length - actual, length - 1L))
        }

        val start = startText.toLongOrNull()?.takeIf { it >= 0L }
            ?: return RangeParseResult.Unsatisfiable(totalLength)
        if (totalLength != null && start >= totalLength) return RangeParseResult.Unsatisfiable(totalLength)

        val requestedEnd = if (endText.isEmpty()) {
            totalLength?.minus(1L) ?: Long.MAX_VALUE
        } else {
            endText.toLongOrNull()?.takeIf { it >= start }
                ?: return RangeParseResult.Unsatisfiable(totalLength)
        }
        val end = if (totalLength != null) requestedEnd.coerceAtMost(totalLength - 1L) else requestedEnd
        if (end < start) return RangeParseResult.Unsatisfiable(totalLength)
        return RangeParseResult.Valid(ByteRange(start, end))
    }
}

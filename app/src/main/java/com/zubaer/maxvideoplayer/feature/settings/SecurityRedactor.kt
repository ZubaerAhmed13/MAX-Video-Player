package com.zubaer.maxvideoplayer.feature.settings

/** Central best-effort redaction layer for user-exportable diagnostics. */
object SecurityRedactor {
    private val bearer = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val namedSecret = Regex(
        "(?i)(access_token|refresh_token|password|credential|cookie|authorization)\\s*[:=]\\s*([^\\s,;]+)",
    )
    private val signedQuery = Regex("(?i)([?&](?:signature|sig|token|key|auth|x-amz-signature)=)[^&#\\s]+")
    private val userInfo = Regex("([A-Za-z][A-Za-z0-9+.-]*://)[^/@\\s:]+:[^/@\\s]+@")

    fun redact(value: String): String = value
        .replace(bearer, "Bearer <redacted>")
        .replace(namedSecret) { match -> "${match.groupValues[1]}=<redacted>" }
        .replace(signedQuery) { match -> "${match.groupValues[1]}<redacted>" }
        .replace(userInfo, "$1<redacted>@")

    fun diagnosticLine(label: String, value: Any?): String = "$label=${redact(value?.toString().orEmpty())}"
}

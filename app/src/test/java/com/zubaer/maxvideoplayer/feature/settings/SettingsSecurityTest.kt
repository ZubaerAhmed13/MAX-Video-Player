package com.zubaer.maxvideoplayer.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSecurityTest {
    @Test
    fun exportContainsOnlyTypedNonSensitiveSettingsAndNoSecretSentinels() {
        val json = SettingsJsonCodec.encode(
            AppSettings(
                appLockEnabled = true,
                autoLockTimeout = AutoLockTimeout.THIRTY_SECONDS,
                biometricUnlockEnabled = true,
                protectPrivateScreens = true,
                reduceMotion = true,
                contrastMode = AccessibilityContrastMode.HIGH_CONTRAST,
                useSystemCaptionStyle = true,
                sleepFadeDuration = SleepFadeDuration.THIRTY_SECONDS,
            ),
            exportedAt = "2026-09-10T00:00:00Z",
        )
        assertTrue(json.contains("max-video-player-settings"))
        listOf(
            "TOP_SECRET_PRIVATE_MOVIE_839247.mp4",
            "access_token",
            "refresh_token",
            "password",
            "Authorization",
            "Bearer ",
            "signedUrl",
        ).forEach { secret -> assertFalse("Export leaked $secret", json.contains(secret, ignoreCase = true)) }
    }

    @Test
    fun importIgnoresFutureFieldsFallsBackUnknownEnumsAndSummarizesChanges() {
        val current = AppSettings(autoLockTimeout = AutoLockTimeout.ONE_MINUTE)
        val json = """
            {
              "format":"max-video-player-settings",
              "version":1,
              "exportedAt":"future",
              "settings":{
                "appLockEnabled":true,
                "autoLockTimeout":"FUTURE_TIMEOUT",
                "contrastMode":"HIGH_CONTRAST",
                "futureSetting":{"nested":[1,true,"x"]}
              }
            }
        """.trimIndent()
        val ready = SettingsJsonCodec.decode(json, current) as SettingsImportResult.Ready
        assertTrue(ready.settings.appLockEnabled)
        assertEquals(AutoLockTimeout.ONE_MINUTE, ready.settings.autoLockTimeout)
        assertEquals(AccessibilityContrastMode.HIGH_CONTRAST, ready.settings.contrastMode)
        assertEquals(1, ready.summary.ignoredUnknownKeys)
    }

    @Test
    fun malformedWrongFormatUnsupportedVersionAndOversizeFailWithoutStateMutation() {
        val current = AppSettings()
        assertTrue(SettingsJsonCodec.decode("{broken", current) is SettingsImportResult.Failure)
        assertTrue(SettingsJsonCodec.decode("{\"format\":\"other\",\"version\":1,\"settings\":{}}", current) is SettingsImportResult.Failure)
        assertTrue(SettingsJsonCodec.decode("{\"format\":\"max-video-player-settings\",\"version\":2,\"settings\":{}}", current) is SettingsImportResult.Failure)
        val oversized = " ".repeat(SettingsJsonCodec.MAX_IMPORT_BYTES + 1)
        assertTrue(SettingsJsonCodec.decode(oversized, current) is SettingsImportResult.Failure)
    }

    @Test
    fun diagnosticRedactorRemovesKnownCredentialForms() {
        val raw = "Bearer abc.def access_token=secret refresh_token:refresh password=pw cookie=c1 https://user:pass@example.com/a?signature=xyz&ok=1"
        val redacted = SecurityRedactor.redact(raw)
        assertFalse(redacted.contains("abc.def"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("refresh"))
        assertFalse(redacted.contains("password=pw"))
        assertFalse(redacted.contains("cookie=c1"))
        assertFalse(redacted.contains("user:pass"))
        assertFalse(redacted.contains("signature=xyz"))
        assertTrue(redacted.contains("<redacted>"))
    }
}

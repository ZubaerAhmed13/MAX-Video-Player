package com.zubaer.maxvideoplayer.feature.settings

enum class AutoLockTimeout(val millis: Long) {
    IMMEDIATELY(0L),
    THIRTY_SECONDS(30_000L),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L),
}

enum class SleepFadeDuration(val millis: Long) {
    OFF(0L),
    FIFTEEN_SECONDS(15_000L),
    THIRTY_SECONDS(30_000L),
    SIXTY_SECONDS(60_000L),
}

enum class AccessibilityContrastMode {
    STANDARD,
    HIGH_CONTRAST,
}

data class AppSettings(
    val appLockEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
    val biometricUnlockEnabled: Boolean = false,
    val protectPrivateScreens: Boolean = true,
    val reduceMotion: Boolean = false,
    val contrastMode: AccessibilityContrastMode = AccessibilityContrastMode.STANDARD,
    val useSystemCaptionStyle: Boolean = false,
    val sleepFadeDuration: SleepFadeDuration = SleepFadeDuration.OFF,
)

data class SettingsImportSummary(
    val changedKeys: List<String>,
    val ignoredUnknownKeys: Int,
)

sealed interface SettingsImportResult {
    data class Ready(val settings: AppSettings, val summary: SettingsImportSummary) : SettingsImportResult
    data class Failure(val message: String) : SettingsImportResult
}

package com.zubaer.maxvideoplayer.feature.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Typed settings facade. It contains no credentials, vault keys, tokens, paths or signed URLs. */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun setAppLockEnabled(value: Boolean) = update { it.copy(appLockEnabled = value) }
    fun setAutoLockTimeout(value: AutoLockTimeout) = update { it.copy(autoLockTimeout = value) }
    fun setBiometricUnlockEnabled(value: Boolean) = update { it.copy(biometricUnlockEnabled = value) }
    fun setProtectPrivateScreens(value: Boolean) = update { it.copy(protectPrivateScreens = value) }
    fun setReduceMotion(value: Boolean) = update { it.copy(reduceMotion = value) }
    fun setContrastMode(value: AccessibilityContrastMode) = update { it.copy(contrastMode = value) }
    fun setUseSystemCaptionStyle(value: Boolean) = update { it.copy(useSystemCaptionStyle = value) }
    fun setSleepFadeDuration(value: SleepFadeDuration) = update { it.copy(sleepFadeDuration = value) }

    fun resetPrivacySection() = update {
        it.copy(
            appLockEnabled = false,
            autoLockTimeout = AutoLockTimeout.ONE_MINUTE,
            biometricUnlockEnabled = false,
            protectPrivateScreens = true,
        )
    }

    fun resetAccessibilitySection() = update {
        it.copy(
            reduceMotion = false,
            contrastMode = AccessibilityContrastMode.STANDARD,
            useSystemCaptionStyle = false,
        )
    }

    fun resetAllNonSensitive() {
        write(AppSettings())
    }

    fun exportTo(output: OutputStream) {
        val bytes = SettingsJsonCodec.encode(_state.value).toByteArray(Charsets.UTF_8)
        output.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
        bytes.fill(0)
    }

    fun parseImport(input: InputStream): SettingsImportResult {
        val text = try {
            readBounded(input, SettingsJsonCodec.MAX_IMPORT_BYTES)
        } catch (_: OversizedSettingsFileException) {
            return SettingsImportResult.Failure("Settings file exceeds the 256 KB safety limit.")
        } catch (_: Throwable) {
            return SettingsImportResult.Failure("Settings file could not be read.")
        }
        return SettingsJsonCodec.decode(text, _state.value)
    }

    fun applyImport(result: SettingsImportResult.Ready): Boolean = write(result.settings)

    private fun update(transform: (AppSettings) -> AppSettings) {
        write(transform(_state.value))
    }

    private fun write(settings: AppSettings): Boolean {
        val committed = prefs.edit()
            .putBoolean(KEY_APP_LOCK, settings.appLockEnabled)
            .putString(KEY_AUTO_LOCK, settings.autoLockTimeout.name)
            .putBoolean(KEY_BIOMETRIC, settings.biometricUnlockEnabled)
            .putBoolean(KEY_SCREEN_PROTECTION, settings.protectPrivateScreens)
            .putBoolean(KEY_REDUCE_MOTION, settings.reduceMotion)
            .putString(KEY_CONTRAST, settings.contrastMode.name)
            .putBoolean(KEY_SYSTEM_CAPTIONS, settings.useSystemCaptionStyle)
            .putString(KEY_SLEEP_FADE, settings.sleepFadeDuration.name)
            .commit()
        if (committed) _state.value = settings
        return committed
    }

    private fun read(): AppSettings = AppSettings(
        appLockEnabled = prefs.getBoolean(KEY_APP_LOCK, false),
        autoLockTimeout = enumValue(prefs.getString(KEY_AUTO_LOCK, null), AutoLockTimeout.ONE_MINUTE),
        biometricUnlockEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
        protectPrivateScreens = prefs.getBoolean(KEY_SCREEN_PROTECTION, true),
        reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false),
        contrastMode = enumValue(prefs.getString(KEY_CONTRAST, null), AccessibilityContrastMode.STANDARD),
        useSystemCaptionStyle = prefs.getBoolean(KEY_SYSTEM_CAPTIONS, false),
        sleepFadeDuration = enumValue(prefs.getString(KEY_SLEEP_FADE, null), SleepFadeDuration.OFF),
    )

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private fun readBounded(input: InputStream, maximumBytes: Int): String {
        input.use { stream ->
            val output = ByteArrayOutputStream(minOf(16 * 1024, maximumBytes))
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total = Math.addExact(total, read)
                if (total > maximumBytes) throw OversizedSettingsFileException()
                output.write(buffer, 0, read)
            }
            buffer.fill(0)
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private companion object {
        const val PREFS = "max_advanced_settings_v1"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_AUTO_LOCK = "auto_lock_timeout"
        const val KEY_BIOMETRIC = "biometric_unlock"
        const val KEY_SCREEN_PROTECTION = "protect_private_screens"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_CONTRAST = "contrast_mode"
        const val KEY_SYSTEM_CAPTIONS = "system_caption_style"
        const val KEY_SLEEP_FADE = "sleep_fade"
    }
}

private class OversizedSettingsFileException : java.io.IOException()

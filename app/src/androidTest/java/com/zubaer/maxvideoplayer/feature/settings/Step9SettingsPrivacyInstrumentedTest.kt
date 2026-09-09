package com.zubaer.maxvideoplayer.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class Step9SettingsPrivacyInstrumentedTest {
    @Test
    fun typedSettingsPersistExportImportAndResetWithoutTouchingSensitiveStores() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("max_advanced_settings_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val sensitive = context.getSharedPreferences("private_vault_auth_v1", Context.MODE_PRIVATE)
        sensitive.edit().putString("sentinel", "SECRET-MUST-SURVIVE").commit()

        val repository = SettingsRepository(context)
        repository.setAppLockEnabled(true)
        repository.setAutoLockTimeout(AutoLockTimeout.THIRTY_SECONDS)
        repository.setProtectPrivateScreens(true)
        repository.setReduceMotion(true)
        repository.setContrastMode(AccessibilityContrastMode.HIGH_CONTRAST)
        repository.setUseSystemCaptionStyle(true)
        repository.setSleepFadeDuration(SleepFadeDuration.THIRTY_SECONDS)

        val reloaded = SettingsRepository(context).state.value
        assertTrue(reloaded.appLockEnabled)
        assertEquals(AutoLockTimeout.THIRTY_SECONDS, reloaded.autoLockTimeout)
        assertEquals(AccessibilityContrastMode.HIGH_CONTRAST, reloaded.contrastMode)
        assertTrue(reloaded.useSystemCaptionStyle)

        val output = ByteArrayOutputStream()
        repository.exportTo(output)
        val exported = output.toString(Charsets.UTF_8.name())
        assertTrue(exported.contains("max-video-player-settings"))
        assertFalse(exported.contains("SECRET-MUST-SURVIVE"))
        assertFalse(exported.contains("private_vault_auth_v1"))

        val modified = exported.replace("\"reduceMotion\":true", "\"reduceMotion\":false")
        val parsed = repository.parseImport(ByteArrayInputStream(modified.toByteArray(Charsets.UTF_8)))
        assertTrue(parsed is SettingsImportResult.Ready)
        assertTrue(repository.applyImport(parsed as SettingsImportResult.Ready))
        assertFalse(repository.state.value.reduceMotion)

        repository.resetAllNonSensitive()
        assertEquals(AppSettings(), repository.state.value)
        assertEquals("SECRET-MUST-SURVIVE", sensitive.getString("sentinel", null))
        sensitive.edit().clear().commit()
    }

    @Test
    fun oversizedImportFailsWithoutChangingCurrentSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("max_advanced_settings_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val repository = SettingsRepository(context)
        repository.setAppLockEnabled(true)
        val before = repository.state.value
        val oversized = ByteArray(SettingsJsonCodec.MAX_IMPORT_BYTES + 1) { 'x'.code.toByte() }
        val result = repository.parseImport(ByteArrayInputStream(oversized))
        assertTrue(result is SettingsImportResult.Failure)
        assertEquals(before, repository.state.value)
    }
}

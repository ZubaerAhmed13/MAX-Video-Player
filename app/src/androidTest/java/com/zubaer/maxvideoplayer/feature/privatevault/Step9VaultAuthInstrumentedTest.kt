package com.zubaer.maxvideoplayer.feature.privatevault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.feature.privatevault.auth.AppLockController
import com.zubaer.maxvideoplayer.feature.privatevault.auth.AppLockState
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultAuthenticator
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultSession
import com.zubaer.maxvideoplayer.feature.privatevault.auth.VaultAuthResult
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import com.zubaer.maxvideoplayer.feature.settings.AutoLockTimeout
import com.zubaer.maxvideoplayer.feature.settings.SettingsRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step9VaultAuthInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun cleanStores() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("private_vault_auth_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("max_advanced_settings_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun pinIsNeverStoredAndWrongPinFailsClosed() {
        var now = 1_000L
        val session = PrivateVaultSession()
        val auth = PrivateVaultAuthenticator(context, session) { now }
        assertTrue(auth.createVault("123456".toCharArray()) is VaultAuthResult.Success)
        val stored = context.getSharedPreferences("private_vault_auth_v1", Context.MODE_PRIVATE).all
        assertFalse(stored.values.any { it?.toString()?.contains("123456") == true })
        assertTrue(stored.containsKey("wrapped_master_secret"))

        auth.lock()
        val wrong = auth.unlock("654321".toCharArray())
        assertTrue(wrong is VaultAuthResult.InvalidCredential)
        assertEquals(PrivateVaultState.LOCKED, session.state.value)
        assertTrue(auth.unlock("123456".toCharArray()) is VaultAuthResult.Success)
        assertEquals(PrivateVaultState.UNLOCKED, session.state.value)
    }

    @Test
    fun credentialChangeRewrapsTheSameMasterSecretAndOldPinStopsWorking() {
        var now = 10_000L
        val session = PrivateVaultSession()
        val auth = PrivateVaultAuthenticator(context, session) { now }
        assertTrue(auth.createVault("123456".toCharArray()) is VaultAuthResult.Success)
        val before = session.masterSecretCopy()
        try {
            assertTrue(auth.changeCredential("123456".toCharArray(), "987654".toCharArray()) is VaultAuthResult.Success)
            val after = session.masterSecretCopy()
            try {
                assertArrayEquals(before, after)
            } finally {
                PrivateVaultCrypto.zero(after)
            }
            auth.lock()
            assertTrue(auth.unlock("123456".toCharArray()) is VaultAuthResult.InvalidCredential)
            now += 1L
            assertTrue(auth.unlock("987654".toCharArray()) is VaultAuthResult.Success)
        } finally {
            PrivateVaultCrypto.zero(before)
        }
    }

    @Test
    fun repeatedFailuresAreRateLimitedWithoutDestructiveWipe() {
        var now = 20_000L
        val session = PrivateVaultSession()
        val auth = PrivateVaultAuthenticator(context, session) { now }
        assertTrue(auth.createVault("123456".toCharArray()) is VaultAuthResult.Success)
        auth.lock()
        repeat(3) { assertTrue(auth.unlock("000000".toCharArray()) is VaultAuthResult.InvalidCredential) }
        val fourth = auth.unlock("000000".toCharArray()) as VaultAuthResult.InvalidCredential
        assertTrue(fourth.retryAfterMs >= 1_000L)
        assertTrue(auth.isConfigured())
        val blockedCorrect = auth.unlock("123456".toCharArray()) as VaultAuthResult.InvalidCredential
        assertTrue(blockedCorrect.retryAfterMs > 0L)
        now += blockedCorrect.retryAfterMs
        assertTrue(auth.unlock("123456".toCharArray()) is VaultAuthResult.Success)
    }

    @Test
    fun appLockHonorsConfiguredMonotonicTimeoutAndLocksVaultSession() {
        var now = 30_000L
        val session = PrivateVaultSession()
        val auth = PrivateVaultAuthenticator(context, session) { now }
        assertTrue(auth.createVault("123456".toCharArray()) is VaultAuthResult.Success)
        val settings = SettingsRepository(context)
        settings.setAppLockEnabled(true)
        settings.setAutoLockTimeout(AutoLockTimeout.THIRTY_SECONDS)
        val appLock = AppLockController(settings, auth, session) { now }

        appLock.onBackground()
        now += 29_999L
        appLock.onForeground()
        assertEquals(AppLockState.UNLOCKED, appLock.state.value)

        appLock.onBackground()
        now += 30_000L
        appLock.onForeground()
        assertEquals(AppLockState.LOCKED, appLock.state.value)
        assertEquals(PrivateVaultState.LOCKED, session.state.value)
        assertTrue(appLock.unlock("123456".toCharArray()) is VaultAuthResult.Success)
        assertEquals(AppLockState.UNLOCKED, appLock.state.value)
    }
}

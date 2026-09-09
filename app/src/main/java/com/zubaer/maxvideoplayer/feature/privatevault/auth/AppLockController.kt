package com.zubaer.maxvideoplayer.feature.privatevault.auth

import android.os.SystemClock
import com.zubaer.maxvideoplayer.feature.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLockState { UNLOCKED, LOCKED }

/** Optional app lock; the mandatory Private Vault lock remains a separate session policy. */
class AppLockController(
    private val settings: SettingsRepository,
    private val authenticator: PrivateVaultAuthenticator,
    private val session: PrivateVaultSession,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _state = MutableStateFlow(AppLockState.UNLOCKED)
    val state: StateFlow<AppLockState> = _state.asStateFlow()
    private var backgroundAtMs: Long? = null

    fun onBackground() {
        val configured = settings.state.value
        if (!configured.appLockEnabled || !authenticator.isConfigured()) return
        backgroundAtMs = clock()
        if (configured.autoLockTimeout.millis == 0L) lockNow()
    }

    fun onForeground() {
        val configured = settings.state.value
        if (!configured.appLockEnabled || !authenticator.isConfigured()) {
            _state.value = AppLockState.UNLOCKED
            backgroundAtMs = null
            return
        }
        val background = backgroundAtMs ?: return
        if (clock() - background >= configured.autoLockTimeout.millis) lockNow()
        backgroundAtMs = null
    }

    fun lockNow() {
        session.lock()
        _state.value = AppLockState.LOCKED
    }

    fun unlock(credential: CharArray): VaultAuthResult {
        val result = authenticator.unlock(credential)
        if (result is VaultAuthResult.Success) _state.value = AppLockState.UNLOCKED
        return result
    }
}

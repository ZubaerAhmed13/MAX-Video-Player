package com.zubaer.maxvideoplayer.feature.privatevault.auth

import com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultState
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single authority for whether decrypted vault key material is available in this process. */
class PrivateVaultSession {
    private val lock = Any()
    private var masterSecret: ByteArray? = null
    private val _state = MutableStateFlow(PrivateVaultState.UNCONFIGURED)
    val state: StateFlow<PrivateVaultState> = _state.asStateFlow()

    fun setConfigured(configured: Boolean) = synchronized(lock) {
        if (!configured) {
            destroySecretLocked()
            _state.value = PrivateVaultState.UNCONFIGURED
        } else if (_state.value == PrivateVaultState.UNCONFIGURED) {
            _state.value = PrivateVaultState.LOCKED
        }
    }

    fun beginUnlock() {
        if (_state.value != PrivateVaultState.UNCONFIGURED) _state.value = PrivateVaultState.UNLOCKING
    }

    fun unlock(secret: ByteArray) = synchronized(lock) {
        require(secret.size == PrivateVaultCrypto.KEY_BYTES) { "Invalid vault master secret" }
        destroySecretLocked()
        masterSecret = secret.copyOf()
        _state.value = PrivateVaultState.UNLOCKED
    }

    fun lock() = synchronized(lock) {
        destroySecretLocked()
        _state.value = if (_state.value == PrivateVaultState.UNCONFIGURED) {
            PrivateVaultState.UNCONFIGURED
        } else {
            PrivateVaultState.LOCKED
        }
    }

    fun markError() = synchronized(lock) {
        destroySecretLocked()
        _state.value = PrivateVaultState.ERROR
    }

    fun masterSecretCopy(): ByteArray = synchronized(lock) {
        check(_state.value == PrivateVaultState.UNLOCKED) { "Private Vault is locked" }
        checkNotNull(masterSecret).copyOf()
    }

    fun <T> withMasterSecret(block: (ByteArray) -> T): T {
        val copy = masterSecretCopy()
        return try {
            block(copy)
        } finally {
            PrivateVaultCrypto.zero(copy)
        }
    }

    private fun destroySecretLocked() {
        PrivateVaultCrypto.zero(masterSecret)
        masterSecret = null
    }
}

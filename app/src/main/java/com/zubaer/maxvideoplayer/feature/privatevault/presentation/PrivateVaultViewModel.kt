package com.zubaer.maxvideoplayer.feature.privatevault.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateImportMode
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateImportResult
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultItem
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultState
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultAuthenticator
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultSession
import com.zubaer.maxvideoplayer.feature.privatevault.auth.VaultAuthResult
import com.zubaer.maxvideoplayer.feature.privatevault.repository.PrivateVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class PrivateVaultUiState(
    val vaultState: PrivateVaultState = PrivateVaultState.UNCONFIGURED,
    val items: List<PrivateVaultItem> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class PrivateVaultViewModel(
    private val repository: PrivateVaultRepository,
    private val authenticator: PrivateVaultAuthenticator,
    private val session: PrivateVaultSession,
) : ViewModel() {
    private val _state = MutableStateFlow(PrivateVaultUiState(vaultState = session.state.value))
    val state: StateFlow<PrivateVaultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { vaultState ->
                if (vaultState == PrivateVaultState.UNLOCKED) {
                    refreshUnlocked(vaultState)
                } else {
                    _state.value = _state.value.copy(vaultState = vaultState, items = emptyList(), busy = false)
                }
            }
        }
    }

    fun create(credential: String) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch(Dispatchers.Default) {
            val result = authenticator.createVault(credential.toCharArray())
            withContext(Dispatchers.Main) { handleAuth(result) }
        }
    }

    fun unlock(credential: String) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch(Dispatchers.Default) {
            val result = authenticator.unlock(credential.toCharArray())
            withContext(Dispatchers.Main) { handleAuth(result) }
        }
    }

    fun lock() {
        authenticator.lock()
        _state.value = _state.value.copy(items = emptyList(), message = "Private Vault locked")
    }

    fun import(uri: Uri, mode: PrivateImportMode) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val result = repository.import(uri, mode)
            when (result) {
                is PrivateImportResult.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        message = if (result.originalDeleted) "Moved to Private Vault" else "Encrypted private copy created",
                    )
                    refreshUnlocked(PrivateVaultState.UNLOCKED)
                }
                is PrivateImportResult.EncryptedCopyCreatedOriginalRemains -> {
                    _state.value = _state.value.copy(busy = false, message = result.reason)
                    refreshUnlocked(PrivateVaultState.UNLOCKED)
                }
                is PrivateImportResult.Failure -> _state.value = _state.value.copy(busy = false, message = result.message)
            }
        }
    }

    fun delete(vaultId: String) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val ok = repository.delete(vaultId)
            _state.value = _state.value.copy(busy = false, message = if (ok) "Deleted from Private Vault" else "Private item could not be deleted")
            if (ok) refreshUnlocked(PrivateVaultState.UNLOCKED)
        }
    }

    fun eraseVault() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val ok = repository.eraseAll()
            if (ok) {
                authenticator.clearAuthenticationAfterVaultErase()
                _state.value = PrivateVaultUiState(
                    vaultState = PrivateVaultState.UNCONFIGURED,
                    message = "Private Vault erased",
                )
            } else {
                _state.value = _state.value.copy(busy = false, message = "Private Vault could not be erased completely")
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun refreshUnlocked(vaultState: PrivateVaultState) {
        _state.value = _state.value.copy(vaultState = vaultState, busy = true, items = emptyList())
        runCatching { repository.recoverAbandonedTransactions() }
        val items = runCatching { repository.loadUnlockedItems() }.getOrDefault(emptyList())
        _state.value = _state.value.copy(vaultState = vaultState, items = items, busy = false)
    }

    private fun handleAuth(result: VaultAuthResult) {
        _state.value = _state.value.copy(
            busy = false,
            message = when (result) {
                VaultAuthResult.Success -> null
                is VaultAuthResult.InvalidCredential -> if (result.retryAfterMs > 0L) {
                    "Incorrect PIN/passphrase. Try again in ${(result.retryAfterMs + 999L) / 1000L} seconds."
                } else "Incorrect PIN/passphrase."
                is VaultAuthResult.Rejected -> result.message
                is VaultAuthResult.Failure -> result.message
            },
        )
    }
}

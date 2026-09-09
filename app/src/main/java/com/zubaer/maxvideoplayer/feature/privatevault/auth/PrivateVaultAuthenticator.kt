package com.zubaer.maxvideoplayer.feature.privatevault.auth

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.VaultAuthenticationException

sealed interface VaultAuthResult {
    data object Success : VaultAuthResult
    data class InvalidCredential(val retryAfterMs: Long) : VaultAuthResult
    data class Rejected(val message: String) : VaultAuthResult
    data class Failure(val message: String) : VaultAuthResult
}

/**
 * PIN/passphrase envelope for the vault master secret. The credential itself is never persisted.
 * Repeated failures receive a bounded in-process exponential delay; no destructive self-wipe is
 * performed.
 */
class PrivateVaultAuthenticator(
    context: Context,
    private val session: PrivateVaultSession,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var failedAttempts = 0
    private var nextAllowedElapsedMs = 0L

    init {
        session.setConfigured(isConfigured())
    }

    fun isConfigured(): Boolean =
        prefs.getInt(KEY_VERSION, 0) == ENVELOPE_VERSION &&
            prefs.contains(KEY_SALT) && prefs.contains(KEY_NONCE) && prefs.contains(KEY_CIPHERTEXT)

    fun validateNewCredential(value: CharArray): String? {
        if (value.isEmpty()) return "Enter a PIN or passphrase."
        val numeric = value.all(Char::isDigit)
        return when {
            numeric && value.size < 6 -> "PIN must contain at least 6 digits."
            !numeric && value.size < 8 -> "Passphrase must contain at least 8 characters."
            value.size > 256 -> "Passphrase is too long."
            else -> null
        }
    }

    fun createVault(credential: CharArray): VaultAuthResult {
        validateNewCredential(credential)?.let { return VaultAuthResult.Rejected(it) }
        if (isConfigured()) return VaultAuthResult.Rejected("Private Vault is already configured.")
        val master = PrivateVaultCrypto.randomKey()
        return try {
            if (!writeEnvelope(credential, master)) return VaultAuthResult.Failure("Could not save the vault key envelope.")
            session.setConfigured(true)
            session.unlock(master)
            failedAttempts = 0
            nextAllowedElapsedMs = 0L
            VaultAuthResult.Success
        } catch (_: Throwable) {
            session.markError()
            VaultAuthResult.Failure("Private Vault could not be created.")
        } finally {
            PrivateVaultCrypto.zero(master)
            credential.fill('\u0000')
        }
    }

    fun unlock(credential: CharArray): VaultAuthResult {
        if (!isConfigured()) {
            credential.fill('\u0000')
            session.setConfigured(false)
            return VaultAuthResult.Rejected("Private Vault is not configured.")
        }
        val now = elapsedRealtime()
        val remaining = (nextAllowedElapsedMs - now).coerceAtLeast(0L)
        if (remaining > 0L) {
            credential.fill('\u0000')
            return VaultAuthResult.InvalidCredential(remaining)
        }

        session.beginUnlock()
        val salt = decode(KEY_SALT) ?: return failConfiguration(credential)
        val nonce = decode(KEY_NONCE) ?: return failConfiguration(credential)
        val ciphertext = decode(KEY_CIPHERTEXT) ?: return failConfiguration(credential)
        val iterations = prefs.getInt(KEY_ITERATIONS, PrivateVaultCrypto.PRODUCTION_KDF_ITERATIONS)
        if (iterations < PrivateVaultCrypto.MIN_KDF_ITERATIONS) return failConfiguration(credential)
        val derived = runCatching { PrivateVaultCrypto.derivePinKey(credential, salt, iterations) }.getOrNull()
            ?: return failConfiguration(credential)
        return try {
            val secret = PrivateVaultCrypto.decrypt(derived, ciphertext, MASTER_AAD, nonce)
            try {
                if (secret.size != PrivateVaultCrypto.KEY_BYTES) return failConfiguration(credential)
                session.unlock(secret)
                failedAttempts = 0
                nextAllowedElapsedMs = 0L
                VaultAuthResult.Success
            } finally {
                PrivateVaultCrypto.zero(secret)
            }
        } catch (_: VaultAuthenticationException) {
            recordFailure()
            session.lock()
            VaultAuthResult.InvalidCredential((nextAllowedElapsedMs - elapsedRealtime()).coerceAtLeast(0L))
        } catch (_: Throwable) {
            session.markError()
            VaultAuthResult.Failure("Private Vault authentication failed safely.")
        } finally {
            PrivateVaultCrypto.zero(derived)
            salt.fill(0)
            nonce.fill(0)
            ciphertext.fill(0)
            credential.fill('\u0000')
        }
    }

    fun changeCredential(current: CharArray, replacement: CharArray): VaultAuthResult {
        validateNewCredential(replacement)?.let {
            current.fill('\u0000')
            replacement.fill('\u0000')
            return VaultAuthResult.Rejected(it)
        }
        val currentResult = unlock(current)
        if (currentResult !is VaultAuthResult.Success) {
            replacement.fill('\u0000')
            return currentResult
        }
        val secret = session.masterSecretCopy()
        return try {
            if (!writeEnvelope(replacement, secret)) return VaultAuthResult.Failure("Could not commit the new vault credential.")
            // Verify the newly committed envelope before accepting the change.
            val salt = decode(KEY_SALT) ?: return VaultAuthResult.Failure("New vault envelope could not be verified.")
            val nonce = decode(KEY_NONCE) ?: return VaultAuthResult.Failure("New vault envelope could not be verified.")
            val ciphertext = decode(KEY_CIPHERTEXT) ?: return VaultAuthResult.Failure("New vault envelope could not be verified.")
            val derived = PrivateVaultCrypto.derivePinKey(replacement, salt, prefs.getInt(KEY_ITERATIONS, 0))
            val verified = try {
                PrivateVaultCrypto.decrypt(derived, ciphertext, MASTER_AAD, nonce)
            } finally {
                PrivateVaultCrypto.zero(derived)
                salt.fill(0)
                nonce.fill(0)
                ciphertext.fill(0)
            }
            try {
                if (!PrivateVaultCrypto.constantTimeEquals(secret, verified)) {
                    return VaultAuthResult.Failure("New vault envelope verification failed.")
                }
            } finally {
                PrivateVaultCrypto.zero(verified)
            }
            VaultAuthResult.Success
        } catch (_: Throwable) {
            VaultAuthResult.Failure("Vault credential change failed safely.")
        } finally {
            PrivateVaultCrypto.zero(secret)
            replacement.fill('\u0000')
        }
    }

    fun lock() = session.lock()

    /** Authentication reset only; encrypted media deletion is a separate explicit repository action. */
    fun clearAuthenticationAfterVaultErase() {
        prefs.edit().clear().commit()
        failedAttempts = 0
        nextAllowedElapsedMs = 0L
        session.setConfigured(false)
    }

    fun retryRemainingMs(): Long = (nextAllowedElapsedMs - elapsedRealtime()).coerceAtLeast(0L)

    private fun writeEnvelope(credential: CharArray, masterSecret: ByteArray): Boolean {
        val salt = PrivateVaultCrypto.randomSalt()
        val derived = PrivateVaultCrypto.derivePinKey(credential, salt, PrivateVaultCrypto.PRODUCTION_KDF_ITERATIONS)
        return try {
            val encrypted = PrivateVaultCrypto.encrypt(derived, masterSecret, MASTER_AAD)
            prefs.edit()
                .putInt(KEY_VERSION, ENVELOPE_VERSION)
                .putInt(KEY_ITERATIONS, PrivateVaultCrypto.PRODUCTION_KDF_ITERATIONS)
                .putString(KEY_SALT, encode(salt))
                .putString(KEY_NONCE, encode(encrypted.nonce))
                .putString(KEY_CIPHERTEXT, encode(encrypted.ciphertext))
                .commit()
        } finally {
            PrivateVaultCrypto.zero(derived)
            salt.fill(0)
        }
    }

    private fun recordFailure() {
        failedAttempts++
        val delayMs = when {
            failedAttempts <= 3 -> 0L
            else -> (1_000L shl (failedAttempts - 4).coerceAtMost(5)).coerceAtMost(MAX_RETRY_DELAY_MS)
        }
        nextAllowedElapsedMs = elapsedRealtime() + delayMs
    }

    private fun failConfiguration(credential: CharArray): VaultAuthResult {
        credential.fill('\u0000')
        session.markError()
        return VaultAuthResult.Failure("Private Vault key envelope is invalid.")
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(key: String): ByteArray? = prefs.getString(key, null)?.let {
        runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
    }

    private companion object {
        const val PREFS = "private_vault_auth_v1"
        const val ENVELOPE_VERSION = 1
        const val KEY_VERSION = "envelope_version"
        const val KEY_ITERATIONS = "kdf_iterations"
        const val KEY_SALT = "kdf_salt"
        const val KEY_NONCE = "master_nonce"
        const val KEY_CIPHERTEXT = "wrapped_master_secret"
        const val MAX_RETRY_DELAY_MS = 30_000L
        val MASTER_AAD = "MAXVAULT_MASTER_ENVELOPE_V1".toByteArray(Charsets.US_ASCII)
    }
}

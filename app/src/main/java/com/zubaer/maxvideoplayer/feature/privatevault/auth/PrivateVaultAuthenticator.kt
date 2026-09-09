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
        if (isConfigured()) {
            credential.fill('\u0000')
            return VaultAuthResult.Rejected("Private Vault is already configured.")
        }
        val master = PrivateVaultCrypto.randomKey()
        var envelope: Envelope? = null
        return try {
            envelope = buildEnvelope(credential, master)
            if (!verifyEnvelope(credential, envelope, master)) {
                return VaultAuthResult.Failure("Could not verify the vault key envelope.")
            }
            if (!persistEnvelope(envelope)) {
                return VaultAuthResult.Failure("Could not save the vault key envelope.")
            }
            session.setConfigured(true)
            session.unlock(master)
            failedAttempts = 0
            nextAllowedElapsedMs = 0L
            VaultAuthResult.Success
        } catch (_: Throwable) {
            session.markError()
            VaultAuthResult.Failure("Private Vault could not be created.")
        } finally {
            envelope?.clear()
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
        if (iterations < PrivateVaultCrypto.MIN_KDF_ITERATIONS) {
            salt.fill(0)
            nonce.fill(0)
            ciphertext.fill(0)
            return failConfiguration(credential)
        }
        val derived = runCatching { PrivateVaultCrypto.derivePinKey(credential, salt, iterations) }.getOrNull()
            ?: run {
                salt.fill(0)
                nonce.fill(0)
                ciphertext.fill(0)
                return failConfiguration(credential)
            }
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

    /**
     * Rewraps the unchanged master secret. The new envelope is authenticated in memory before the
     * single SharedPreferences commit replaces the old envelope, so a verification failure cannot
     * discard the last valid credential. Media containers are never re-encrypted for a PIN change.
     */
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
        var candidate: Envelope? = null
        return try {
            candidate = buildEnvelope(replacement, secret)
            if (!verifyEnvelope(replacement, candidate, secret)) {
                return VaultAuthResult.Failure("New vault envelope verification failed; the previous credential remains active.")
            }
            if (!persistEnvelope(candidate)) {
                return VaultAuthResult.Failure("Could not commit the new vault credential; the previous credential remains active.")
            }
            failedAttempts = 0
            nextAllowedElapsedMs = 0L
            VaultAuthResult.Success
        } catch (_: Throwable) {
            VaultAuthResult.Failure("Vault credential change failed safely; the previous credential remains active.")
        } finally {
            candidate?.clear()
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

    private fun buildEnvelope(credential: CharArray, masterSecret: ByteArray): Envelope {
        val salt = PrivateVaultCrypto.randomSalt()
        val derived = PrivateVaultCrypto.derivePinKey(credential, salt, PrivateVaultCrypto.PRODUCTION_KDF_ITERATIONS)
        return try {
            val encrypted = PrivateVaultCrypto.encrypt(derived, masterSecret, MASTER_AAD)
            Envelope(
                iterations = PrivateVaultCrypto.PRODUCTION_KDF_ITERATIONS,
                salt = salt,
                nonce = encrypted.nonce,
                ciphertext = encrypted.ciphertext,
            )
        } catch (error: Throwable) {
            salt.fill(0)
            throw error
        } finally {
            PrivateVaultCrypto.zero(derived)
        }
    }

    private fun verifyEnvelope(credential: CharArray, envelope: Envelope, expectedMaster: ByteArray): Boolean {
        val derived = PrivateVaultCrypto.derivePinKey(credential, envelope.salt, envelope.iterations)
        return try {
            val decrypted = PrivateVaultCrypto.decrypt(derived, envelope.ciphertext, MASTER_AAD, envelope.nonce)
            try {
                decrypted.size == PrivateVaultCrypto.KEY_BYTES &&
                    PrivateVaultCrypto.constantTimeEquals(expectedMaster, decrypted)
            } finally {
                PrivateVaultCrypto.zero(decrypted)
            }
        } catch (_: Throwable) {
            false
        } finally {
            PrivateVaultCrypto.zero(derived)
        }
    }

    private fun persistEnvelope(envelope: Envelope): Boolean = prefs.edit()
        .putInt(KEY_VERSION, ENVELOPE_VERSION)
        .putInt(KEY_ITERATIONS, envelope.iterations)
        .putString(KEY_SALT, encode(envelope.salt))
        .putString(KEY_NONCE, encode(envelope.nonce))
        .putString(KEY_CIPHERTEXT, encode(envelope.ciphertext))
        .commit()

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

    private data class Envelope(
        val iterations: Int,
        val salt: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
    ) {
        fun clear() {
            salt.fill(0)
            nonce.fill(0)
            ciphertext.fill(0)
        }
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

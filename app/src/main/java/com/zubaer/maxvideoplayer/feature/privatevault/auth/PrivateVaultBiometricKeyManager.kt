package com.zubaer.maxvideoplayer.feature.privatevault.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface BiometricPreparation {
    data class Ready(val cipher: Cipher) : BiometricPreparation
    data class Unavailable(val reason: String) : BiometricPreparation
}

/**
 * Optional Android-Keystore wrapping path. Biometric failure/invalidation never removes the PIN
 * envelope, so the passphrase remains the recovery path.
 */
class PrivateVaultBiometricKeyManager(
    context: Context,
    private val session: PrivateVaultSession,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var pendingEnrollmentSecret: ByteArray? = null
    private var pendingUnlockCiphertext: ByteArray? = null

    fun isEnabled(): Boolean = prefs.contains(KEY_NONCE) && prefs.contains(KEY_CIPHERTEXT)

    fun prepareEnrollment(): BiometricPreparation {
        if (Build.VERSION.SDK_INT < 28) return BiometricPreparation.Unavailable("Biometric unlock requires Android 9 or newer on this build.")
        val secret = runCatching { session.masterSecretCopy() }.getOrElse {
            return BiometricPreparation.Unavailable("Unlock the Private Vault with your PIN first.")
        }
        clearPending()
        pendingEnrollmentSecret = secret
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            BiometricPreparation.Ready(cipher)
        } catch (error: Throwable) {
            clearPending()
            BiometricPreparation.Unavailable(error.message ?: "Biometric key is unavailable.")
        }
    }

    fun completeEnrollment(authenticatedCipher: Cipher): Boolean {
        val secret = pendingEnrollmentSecret ?: return false
        return try {
            val ciphertext = authenticatedCipher.doFinal(secret)
            val nonce = authenticatedCipher.iv ?: return false
            prefs.edit()
                .putString(KEY_NONCE, Base64.encodeToString(nonce, Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .commit()
        } catch (_: Throwable) {
            false
        } finally {
            clearPending()
        }
    }

    fun prepareUnlock(): BiometricPreparation {
        if (Build.VERSION.SDK_INT < 28 || !isEnabled()) {
            return BiometricPreparation.Unavailable("Biometric unlock is unavailable. Use PIN.")
        }
        clearPending()
        val nonce = decode(KEY_NONCE) ?: return BiometricPreparation.Unavailable("Biometric wrapping data is invalid. Use PIN.")
        val ciphertext = decode(KEY_CIPHERTEXT) ?: return BiometricPreparation.Unavailable("Biometric wrapping data is invalid. Use PIN.")
        pendingUnlockCiphertext = ciphertext
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(128, nonce))
            BiometricPreparation.Ready(cipher)
        } catch (_: KeyPermanentlyInvalidatedException) {
            disable()
            BiometricPreparation.Unavailable("Biometric key changed or was invalidated. Use PIN.")
        } catch (error: Throwable) {
            pendingUnlockCiphertext?.fill(0)
            pendingUnlockCiphertext = null
            BiometricPreparation.Unavailable(error.message ?: "Biometric unlock is unavailable. Use PIN.")
        } finally {
            nonce.fill(0)
        }
    }

    fun completeUnlock(authenticatedCipher: Cipher): Boolean {
        val ciphertext = pendingUnlockCiphertext ?: return false
        return try {
            val master = authenticatedCipher.doFinal(ciphertext)
            try {
                if (master.size != PrivateVaultCrypto.KEY_BYTES) return false
                session.unlock(master)
                true
            } finally {
                PrivateVaultCrypto.zero(master)
            }
        } catch (_: Throwable) {
            false
        } finally {
            clearPending()
        }
    }

    fun disable() {
        clearPending()
        prefs.edit().clear().commit()
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Biometric key is missing")
    }

    private fun getOrCreateKey(): SecretKey = runCatching { getExistingKey() }.getOrElse {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
            if (Build.VERSION.SDK_INT >= 24) builder.setInvalidatedByBiometricEnrollment(true)
        }
        generator.init(builder.build())
        generator.generateKey()
    }

    private fun decode(key: String): ByteArray? = prefs.getString(key, null)?.let {
        runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
    }

    private fun clearPending() {
        PrivateVaultCrypto.zero(pendingEnrollmentSecret)
        pendingEnrollmentSecret = null
        pendingUnlockCiphertext?.fill(0)
        pendingUnlockCiphertext = null
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "max_private_vault_biometric_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS = "private_vault_biometric_v1"
        const val KEY_NONCE = "nonce"
        const val KEY_CIPHERTEXT = "wrapped_master_secret"
    }
}

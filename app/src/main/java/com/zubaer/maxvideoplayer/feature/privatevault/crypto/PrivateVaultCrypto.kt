package com.zubaer.maxvideoplayer.feature.privatevault.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM and PBKDF2 primitives used by the versioned private-vault format. */
object PrivateVaultCrypto {
    const val KEY_BYTES = 32
    const val GCM_NONCE_BYTES = 12
    const val GCM_TAG_BYTES = 16
    const val NONCE_PREFIX_BYTES = 8
    const val PRODUCTION_KDF_ITERATIONS = 310_000
    const val MIN_KDF_ITERATIONS = 100_000
    const val MAX_CHUNK_INDEX = 0xffff_ffffL

    private val random = SecureRandom()

    data class GcmBlob(val nonce: ByteArray, val ciphertext: ByteArray)

    fun randomKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)
    fun randomNonce(): ByteArray = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
    fun randomNoncePrefix(): ByteArray = ByteArray(NONCE_PREFIX_BYTES).also(random::nextBytes)
    fun randomSalt(bytes: Int = 16): ByteArray = ByteArray(bytes.coerceAtLeast(16)).also(random::nextBytes)

    fun derivePinKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(salt.size >= 16) { "KDF salt must be at least 16 bytes" }
        require(iterations >= 1) { "KDF iterations must be positive" }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray, nonce: ByteArray = randomNonce()): GcmBlob {
        require(key.size == KEY_BYTES) { "AES-256 requires a 32-byte key" }
        require(nonce.size == GCM_NONCE_BYTES) { "GCM nonce must be 12 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BYTES * 8, nonce))
        cipher.updateAAD(aad)
        return GcmBlob(nonce.copyOf(), cipher.doFinal(plaintext))
    }

    fun decrypt(key: ByteArray, ciphertext: ByteArray, aad: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 requires a 32-byte key" }
        require(nonce.size == GCM_NONCE_BYTES) { "GCM nonce must be 12 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BYTES * 8, nonce))
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(ciphertext)
        } catch (badTag: AEADBadTagException) {
            throw VaultAuthenticationException("Encrypted vault data failed authentication", badTag)
        }
    }

    /** 8-byte random per-file prefix + unsigned 32-bit chunk index = unique 96-bit nonce per file key. */
    fun chunkNonce(prefix: ByteArray, chunkIndex: Long): ByteArray {
        require(prefix.size == NONCE_PREFIX_BYTES) { "Nonce prefix must be 8 bytes" }
        require(chunkIndex in 0..MAX_CHUNK_INDEX) { "Chunk index exceeds nonce-safe range" }
        return ByteBuffer.allocate(GCM_NONCE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .put(prefix)
            .putInt(chunkIndex.toInt())
            .array()
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun zero(bytes: ByteArray?) {
        bytes?.fill(0)
    }
}

class VaultAuthenticationException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)

object VaultLongMath {
    fun checkedAdd(a: Long, b: Long): Long = Math.addExact(a, b)
    fun checkedMultiply(a: Long, b: Long): Long = Math.multiplyExact(a, b)

    fun ceilDiv(value: Long, divisor: Long): Long {
        require(value >= 0L && divisor > 0L)
        if (value == 0L) return 0L
        return checkedAdd(value - 1L, divisor) / divisor
    }
}

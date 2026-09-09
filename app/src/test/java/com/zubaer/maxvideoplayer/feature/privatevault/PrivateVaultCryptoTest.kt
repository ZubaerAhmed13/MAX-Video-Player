package com.zubaer.maxvideoplayer.feature.privatevault

import com.zubaer.maxvideoplayer.feature.privatevault.crypto.PrivateVaultCrypto
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.VaultAuthenticationException
import com.zubaer.maxvideoplayer.feature.privatevault.crypto.VaultLongMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateVaultCryptoTest {
    @Test
    fun aes256Gcm_roundTrip_andTamperingFails() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { (it + 10).toByte() }
        val aad = "container-v1|vault|chunk-7|length-123".toByteArray()
        val plain = ByteArray(4097) { (it * 31).toByte() }
        val encrypted = PrivateVaultCrypto.encrypt(key, plain, aad, nonce)

        assertArrayEquals(plain, PrivateVaultCrypto.decrypt(key, encrypted.ciphertext, aad, nonce))

        val modified = encrypted.ciphertext.copyOf().also { it[it.lastIndex / 2] = (it[it.lastIndex / 2].toInt() xor 1).toByte() }
        assertThrows(VaultAuthenticationException::class.java) {
            PrivateVaultCrypto.decrypt(key, modified, aad, nonce)
        }
        val badTag = encrypted.ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x40).toByte() }
        assertThrows(VaultAuthenticationException::class.java) {
            PrivateVaultCrypto.decrypt(key, badTag, aad, nonce)
        }
        assertThrows(VaultAuthenticationException::class.java) {
            PrivateVaultCrypto.decrypt(ByteArray(32) { 99 }, encrypted.ciphertext, aad, nonce)
        }
        assertThrows(VaultAuthenticationException::class.java) {
            PrivateVaultCrypto.decrypt(key, encrypted.ciphertext, "wrong-aad".toByteArray(), nonce)
        }
    }

    @Test
    fun pbkdf2HmacSha256_matchesKnownAnswerVector() {
        val derived = PrivateVaultCrypto.derivePinKey(
            "password".toCharArray(),
            "salt-salt-salt-123".toByteArray(),
            1_000,
        )
        assertEquals(
            "ffc15d6dd1ea8bc692bc4e4cc3eb3587d81ed48cee3f9eddc20e023ae613372f",
            derived.joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
    }

    @Test
    fun chunkNonce_isUniqueAtBoundaries_andRejectsOverflow() {
        val prefix = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val indices = listOf(0L, 1L, 2L, 0x7fff_ffffL, 0x8000_0000L, 0xffff_ffffL)
        val nonces = indices.map { PrivateVaultCrypto.chunkNonce(prefix, it).toList() }
        assertEquals(indices.size, nonces.toSet().size)
        assertThrows(IllegalArgumentException::class.java) { PrivateVaultCrypto.chunkNonce(prefix, -1L) }
        assertThrows(IllegalArgumentException::class.java) { PrivateVaultCrypto.chunkNonce(prefix, 0x1_0000_0000L) }
    }

    @Test
    fun longMath_handles2GiBAnd3Point2GiBWithoutIntNarrowing() {
        val values = listOf(
            2L * 1024 * 1024 * 1024 - 1,
            2L * 1024 * 1024 * 1024,
            2L * 1024 * 1024 * 1024 + 1,
            3_435_973_837L,
        )
        values.forEach { bytes ->
            val chunks = VaultLongMath.ceilDiv(bytes, PRIVATE_VAULT_CHUNK_SIZE.toLong())
            assertTrue(chunks > 0)
            assertTrue(chunks <= PrivateVaultCrypto.MAX_CHUNK_INDEX + 1L)
            assertTrue(VaultLongMath.checkedMultiply(chunks - 1L, PRIVATE_VAULT_CHUNK_SIZE.toLong()) < bytes)
        }
        assertThrows(ArithmeticException::class.java) { VaultLongMath.checkedAdd(Long.MAX_VALUE, 1L) }
        assertThrows(ArithmeticException::class.java) { VaultLongMath.checkedMultiply(Long.MAX_VALUE, 2L) }
    }

    @Test
    fun constantTimeComparison_reportsEqualityOnlyForEqualDigests() {
        assertTrue(PrivateVaultCrypto.constantTimeEquals(ByteArray(32) { 7 }, ByteArray(32) { 7 }))
        assertFalse(PrivateVaultCrypto.constantTimeEquals(ByteArray(32) { 7 }, ByteArray(32) { 8 }))
    }
}

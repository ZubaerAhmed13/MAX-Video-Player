package com.zubaer.maxvideoplayer.feature.network.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android-Keystore-backed credential storage. Room only keeps opaque references; passwords,
 * tokens, cookies and sensitive headers are encrypted before SharedPreferences persistence.
 */
class CredentialVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()

    fun save(credential: NetworkCredential, existingRef: String? = null): String = synchronized(lock) {
        val ref = existingRef ?: UUID.randomUUID().toString()
        preferences.edit().putString(ref, encrypt(encode(credential))).commit()
        ref
    }

    fun get(ref: String?): NetworkCredential? {
        if (ref.isNullOrBlank()) return null
        return synchronized(lock) {
            val payload = preferences.getString(ref, null) ?: return@synchronized null
            runCatching { decode(decrypt(payload)) }.getOrElse {
                preferences.edit().remove(ref).apply()
                null
            }
        }
    }

    fun delete(ref: String?) {
        if (ref.isNullOrBlank()) return
        synchronized(lock) { preferences.edit().remove(ref).commit() }
    }

    fun contains(ref: String?): Boolean = !ref.isNullOrBlank() && preferences.contains(ref)

    private fun encrypt(plainText: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText)
        val payload = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): ByteArray {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(payload)
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength in 12..16 && buffer.remaining() > ivLength) { "Invalid encrypted credential." }
        val iv = ByteArray(ivLength).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(value: NetworkCredential): ByteArray = JSONObject().apply {
        put("username", value.username)
        put("password", value.password)
        put("domain", value.domain)
        put("bearerToken", value.bearerToken)
        put("headers", JSONObject(value.headers))
        put("nonce", SecureRandom().nextLong())
    }.toString().toByteArray(Charsets.UTF_8)

    private fun decode(bytes: ByteArray): NetworkCredential {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val headersJson = json.optJSONObject("headers") ?: JSONObject()
        val headers = buildMap {
            val keys = headersJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, headersJson.optString(key))
            }
        }
        return NetworkCredential(
            username = json.optString("username"),
            password = json.optString("password"),
            domain = json.optString("domain"),
            bearerToken = json.optString("bearerToken"),
            headers = headers,
        )
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "max_video_player_network_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES = "network_credential_vault_v1"
    }
}

object SensitiveHeaderPolicy {
    private val blockedLogHeaders = setOf("authorization", "cookie", "proxy-authorization", "x-api-key")

    fun isSensitive(name: String): Boolean = name.lowercase() in blockedLogHeaders ||
        name.contains("token", ignoreCase = true) || name.contains("secret", ignoreCase = true)
}

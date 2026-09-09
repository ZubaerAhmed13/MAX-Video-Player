package com.zubaer.maxvideoplayer.feature.cloud.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CloudTokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long?,
    val scopes: Set<String> = emptySet(),
)

/**
 * Cloud authorization material is encrypted with an Android-Keystore AES-GCM key. Room stores only
 * the opaque reference returned by [save]. Provider access/refresh tokens are never persisted in
 * plaintext and never returned by diagnostics/logging helpers.
 */
class CloudTokenVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val lock = Any()

    fun save(tokens: CloudTokenSet, existingRef: String? = null): String = synchronized(lock) {
        val ref = existingRef ?: UUID.randomUUID().toString()
        val json = JSONObject().apply {
            put("accessToken", tokens.accessToken)
            put("refreshToken", tokens.refreshToken)
            put("expiresAtMs", tokens.expiresAtMs)
            put("scopes", org.json.JSONArray(tokens.scopes.toList()))
        }.toString().toByteArray(Charsets.UTF_8)
        preferences.edit().putString(ref, encrypt(json)).commit()
        ref
    }

    fun get(ref: String?): CloudTokenSet? {
        if (ref.isNullOrBlank()) return null
        return synchronized(lock) {
            val encoded = preferences.getString(ref, null) ?: return@synchronized null
            runCatching {
                val json = JSONObject(String(decrypt(encoded), Charsets.UTF_8))
                val scopesArray = json.optJSONArray("scopes")
                val scopes = buildSet {
                    if (scopesArray != null) for (i in 0 until scopesArray.length()) add(scopesArray.optString(i))
                }
                CloudTokenSet(
                    accessToken = json.getString("accessToken"),
                    refreshToken = json.optString("refreshToken").takeIf { it.isNotBlank() && it != "null" },
                    expiresAtMs = json.optLong("expiresAtMs").takeIf { json.has("expiresAtMs") && !json.isNull("expiresAtMs") },
                    scopes = scopes,
                )
            }.getOrElse {
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

    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain)
        return Base64.encodeToString(
            ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
                .put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array(),
            Base64.NO_WRAP,
        )
    }

    private fun decrypt(encoded: String): ByteArray {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(payload)
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength in 12..16 && buffer.remaining() > ivLength) { "Invalid cloud token payload." }
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

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "max_video_player_cloud_tokens_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES = "cloud_token_vault_v1"
    }
}

object CloudSensitiveDataPolicy {
    private val querySecretKeys = setOf("sig", "signature", "token", "access_token", "auth", "authorization", "se", "sp", "sv")

    fun redactUrl(value: String): String {
        val uri = runCatching { android.net.Uri.parse(value) }.getOrNull() ?: return "[redacted-url]"
        val queryNames = runCatching { uri.queryParameterNames }.getOrDefault(emptySet())
        if (queryNames.any { name -> querySecretKeys.any { secret -> name.contains(secret, ignoreCase = true) } }) {
            return uri.buildUpon().clearQuery().fragment(null).build().toString() + "?[redacted]"
        }
        return value
    }

    fun redactedTokenState(tokens: CloudTokenSet?): String = if (tokens == null) "none" else "present"
}

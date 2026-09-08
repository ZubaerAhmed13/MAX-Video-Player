package com.zubaer.maxvideoplayer.feature.cloud.provider

import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object CloudHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw CloudFailure.Unavailable("Cloud provider is unavailable.", error)
        }
    }

    fun requireSuccess(response: Response, providerLabel: String): Response {
        if (response.isSuccessful) return response
        val retryAfterMs = response.header("Retry-After")?.toLongOrNull()?.times(1000L)
        val message = runCatching {
            val body = response.peekBody(16_384L).string()
            if (body.isBlank()) null else parseErrorMessage(body)
        }.getOrNull() ?: "$providerLabel request failed (${response.code})."
        when (response.code) {
            401 -> throw CloudFailure.AuthenticationRequired()
            403 -> throw CloudFailure.PermissionDenied(message)
            404 -> throw CloudFailure.NotFound(message)
            429 -> throw CloudFailure.RateLimited(retryAfterMs, message)
            else -> if (response.code >= 500) throw CloudFailure.Unavailable(message) else throw CloudFailure.Unavailable(message)
        }
    }

    private fun parseErrorMessage(body: String): String? {
        val json = JSONObject(body)
        val error = json.opt("error")
        return when (error) {
            is JSONObject -> error.optString("message").takeIf { it.isNotBlank() }
                ?: error.optString("summary").takeIf { it.isNotBlank() }
            is String -> error.takeIf { it.isNotBlank() }
            else -> json.optString("error_summary").takeIf { it.isNotBlank() }
        }
    }
}

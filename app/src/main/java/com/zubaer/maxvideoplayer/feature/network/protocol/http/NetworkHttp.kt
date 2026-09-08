package com.zubaer.maxvideoplayer.feature.network.protocol.http

import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkRequestRegistry
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

object NetworkHttpClientFactory {
    fun create(registry: NetworkRequestRegistry): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(RegistryHeaderInterceptor(registry))
        .build()
}

/** Re-resolves every redirected request, preventing credentials from following to another host. */
private class RegistryHeaderInterceptor(
    private val registry: NetworkRequestRegistry,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = registry.resolveHttp(request.url.toString())?.requestHeaders().orEmpty()
        val builder = request.newBuilder()
            .header("User-Agent", "MAXVideoPlayer/0.7 Android")
        headers.forEach { (name, value) -> builder.header(name, value) }
        return chain.proceed(builder.build())
    }
}

fun credentialHeaders(credential: NetworkCredential?): Map<String, String> = buildMap {
    credential?.headers?.let(::putAll)
    credential?.bearerToken?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
    val username = credential?.username.orEmpty()
    if (username.isNotBlank() && !containsKey("Authorization")) {
        put("Authorization", okhttp3.Credentials.basic(username, credential?.password.orEmpty(), Charsets.UTF_8))
    }
}

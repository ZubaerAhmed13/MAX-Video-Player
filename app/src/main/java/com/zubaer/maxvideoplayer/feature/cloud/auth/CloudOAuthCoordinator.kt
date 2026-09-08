package com.zubaer.maxvideoplayer.feature.cloud.auth

import android.net.Uri
import com.zubaer.maxvideoplayer.BuildConfig
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudAccount
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudAuthState
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudFailure
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudProvider
import com.zubaer.maxvideoplayer.feature.cloud.persistence.CloudAccountDao
import com.zubaer.maxvideoplayer.feature.cloud.persistence.CloudAccountEntity
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudPlaybackRegistry
import com.zubaer.maxvideoplayer.feature.cloud.provider.CloudAccessTokenProvider
import com.zubaer.maxvideoplayer.feature.cloud.provider.DropboxClient
import com.zubaer.maxvideoplayer.feature.cloud.provider.GoogleDriveClient
import com.zubaer.maxvideoplayer.feature.cloud.provider.OneDriveClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class CloudOAuthProviderConfig(
    val provider: CloudProvider,
    val clientId: String,
    val redirectUri: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val scopes: List<String>,
    val extraAuthorizationParameters: Map<String, String> = emptyMap(),
) {
    val configured: Boolean get() = clientId.isNotBlank() && redirectUri.isNotBlank()
}

data class CloudAuthorizationRequest(
    val provider: CloudProvider,
    val authorizationUri: Uri,
)

data class CloudOAuthUiState(
    val providerStates: Map<CloudProvider, CloudAuthState> = CloudProvider.entries.associateWith { CloudAuthState.SIGNED_OUT },
    val error: String? = null,
)

/**
 * Production OAuth coordinator for Google Drive, OneDrive and Dropbox. Authorization uses the
 * system browser; token exchange/refresh uses PKCE and never requires a client secret. Access and
 * refresh tokens are written only to CloudTokenVault (Android Keystore AES-GCM). Room receives
 * opaque auth references plus non-secret account metadata.
 */
class CloudOAuthCoordinator(
    private val accountDao: CloudAccountDao,
    private val tokenVault: CloudTokenVault,
    private val playbackRegistry: CloudPlaybackRegistry,
    private val configs: Map<CloudProvider, CloudOAuthProviderConfig> = productionConfigs(),
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val pending = ConcurrentHashMap<CloudProvider, PendingAuthorization>()
    private val tokenProviders = ConcurrentHashMap<String, VaultBackedAccessTokenProvider>()
    private val _uiState = MutableStateFlow(initialUiState(configs))
    val uiState: StateFlow<CloudOAuthUiState> = _uiState.asStateFlow()

    val accounts: Flow<List<CloudAccount>> = accountDao.observeAll().map { entities -> entities.mapNotNull(::toAccount) }

    suspend fun restoreRegisteredAccounts() {
        accountDao.all().forEach { entity ->
            val provider = runCatching { CloudProvider.valueOf(entity.provider) }.getOrNull() ?: return@forEach
            if (!tokenVault.contains(entity.authReference)) {
                updateProviderState(provider, CloudAuthState.REAUTH_REQUIRED)
                return@forEach
            }
            registerClient(entity)
            updateProviderState(provider, CloudAuthState.CONNECTED)
        }
    }

    fun beginAuthorization(provider: CloudProvider): CloudAuthorizationRequest {
        val config = requireNotNull(configs[provider]) { "OAuth provider configuration is missing." }
        if (!config.configured) {
            updateProviderState(provider, CloudAuthState.NOT_CONFIGURED)
            throw CloudFailure.ProviderNotConfigured(provider)
        }
        val verifier = CloudPkce.newVerifier()
        val challenge = CloudPkce.challenge(verifier)
        val state = CloudPkce.newState()
        pending[provider] = PendingAuthorization(verifier, state, nowMs())
        updateProviderState(provider, CloudAuthState.AUTHORIZING)

        val builder = Uri.parse(config.authorizationEndpoint).buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", config.scopes.joinToString(" "))
        config.extraAuthorizationParameters.forEach(builder::appendQueryParameter)
        return CloudAuthorizationRequest(provider, builder.build())
    }

    suspend fun handleRedirect(uri: Uri): CloudAccount? {
        val provider = configs.values.firstOrNull { config -> redirectMatches(uri, config.redirectUri) }?.provider ?: return null
        val config = requireNotNull(configs[provider])
        val transaction = pending.remove(provider)
            ?: throw CloudFailure.AuthenticationRequired("The authorization session expired. Start sign-in again.")
        if (nowMs() - transaction.createdAtMs > AUTH_SESSION_MAX_AGE_MS) {
            updateProviderState(provider, CloudAuthState.REAUTH_REQUIRED)
            throw CloudFailure.AuthenticationRequired("The authorization session expired. Start sign-in again.")
        }
        val state = uri.getQueryParameter("state")
        if (state.isNullOrBlank() || !CloudPkce.constantTimeEquals(transaction.state, state)) {
            updateProviderState(provider, CloudAuthState.FAILED)
            throw CloudFailure.AuthenticationRequired("OAuth state validation failed.")
        }
        uri.getQueryParameter("error")?.let { error ->
            val description = uri.getQueryParameter("error_description")?.take(200)
            updateProviderState(provider, CloudAuthState.FAILED)
            throw CloudFailure.AuthenticationRequired(description ?: "Authorization failed: $error")
        }
        val code = uri.getQueryParameter("code")?.takeIf(String::isNotBlank)
            ?: throw CloudFailure.AuthenticationRequired("Authorization response did not include a code.")

        return try {
            val tokens = exchangeAuthorizationCode(config, code, transaction.verifier)
            val profile = fetchProfile(config, tokens.accessToken)
            val existing = accountDao.find(provider.name, profile.providerAccountId)
            val authRef = tokenVault.save(tokens, existing?.authReference)
            val timestamp = nowMs()
            val entity = CloudAccountEntity(
                id = existing?.id ?: "${provider.name.lowercase()}:${profile.providerAccountId}",
                provider = provider.name,
                providerAccountId = profile.providerAccountId,
                displayName = profile.displayName,
                emailHint = profile.emailHint,
                authReference = authRef,
                createdAtMs = existing?.createdAtMs ?: timestamp,
                lastUsedAtMs = timestamp,
            )
            accountDao.upsert(entity)
            registerClient(entity)
            updateProviderState(provider, CloudAuthState.CONNECTED)
            toAccount(entity)
        } catch (error: Throwable) {
            updateProviderState(provider, CloudAuthState.FAILED, error.message)
            throw error
        }
    }

    suspend fun disconnect(accountId: String) {
        val entity = accountDao.get(accountId) ?: return
        val provider = runCatching { CloudProvider.valueOf(entity.provider) }.getOrNull()
        provider?.let { playbackRegistry.unregister(it, entity.id) }
        tokenProviders.remove(entity.id)
        tokenVault.delete(entity.authReference)
        accountDao.delete(accountId)
        if (provider != null && accountDao.all().none { it.provider == provider.name }) {
            updateProviderState(provider, if (configs[provider]?.configured == true) CloudAuthState.SIGNED_OUT else CloudAuthState.NOT_CONFIGURED)
        }
    }

    suspend fun providerClient(accountId: String): com.zubaer.maxvideoplayer.feature.cloud.provider.CloudProviderClient? {
        val entity = accountDao.get(accountId) ?: return null
        val provider = CloudProvider.valueOf(entity.provider)
        registerClient(entity)
        return playbackRegistry.client(com.zubaer.maxvideoplayer.feature.cloud.model.CloudFileIdentity(provider, entity.id, "_probe"))
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun registerClient(entity: CloudAccountEntity) {
        val provider = CloudProvider.valueOf(entity.provider)
        val config = configs[provider] ?: return
        if (!config.configured) return
        val tokenProvider = tokenProviders.getOrPut(entity.id) {
            VaultBackedAccessTokenProvider(
                provider = provider,
                authReference = entity.authReference,
                tokenVault = tokenVault,
                config = config,
                httpClient = httpClient,
                nowMs = nowMs,
                onState = { state -> updateProviderState(provider, state) },
            )
        }
        val client = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> GoogleDriveClient(entity.id, tokenProvider)
            CloudProvider.ONEDRIVE -> OneDriveClient(entity.id, tokenProvider)
            CloudProvider.DROPBOX -> DropboxClient(entity.id, tokenProvider)
        }
        playbackRegistry.register(provider, entity.id, client)
    }

    private suspend fun exchangeAuthorizationCode(config: CloudOAuthProviderConfig, code: String, verifier: String): CloudTokenSet = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", config.clientId)
            .add("redirect_uri", config.redirectUri)
            .add("code_verifier", verifier)
            .build()
        val request = Request.Builder().url(config.tokenEndpoint).post(form).header("Accept", "application/json").build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string().take(MAX_OAUTH_RESPONSE_CHARS)
            if (!response.isSuccessful) throw oauthFailure(config.provider, response.code, body)
            parseTokenResponse(body, nowMs())
        }
    }

    private suspend fun fetchProfile(config: CloudOAuthProviderConfig, token: String): CloudProfile = withContext(Dispatchers.IO) {
        val request = when (config.provider) {
            CloudProvider.GOOGLE_DRIVE -> Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user(displayName,emailAddress,permissionId)")
                .header("Authorization", "Bearer $token").get().build()
            CloudProvider.ONEDRIVE -> Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me?%24select=id,displayName,mail,userPrincipalName")
                .header("Authorization", "Bearer $token").get().build()
            CloudProvider.DROPBOX -> Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_current_account")
                .header("Authorization", "Bearer $token")
                .post(FormBody.Builder().build()).build()
        }
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string().take(MAX_OAUTH_RESPONSE_CHARS)
            if (!response.isSuccessful) throw oauthFailure(config.provider, response.code, body)
            val json = JSONObject(body)
            when (config.provider) {
                CloudProvider.GOOGLE_DRIVE -> {
                    val user = json.getJSONObject("user")
                    CloudProfile(
                        providerAccountId = user.optString("permissionId").takeIf(String::isNotBlank)
                            ?: user.optString("emailAddress").takeIf(String::isNotBlank)
                            ?: throw CloudFailure.Unavailable("Google Drive account identity is unavailable."),
                        displayName = user.optString("displayName").takeIf(String::isNotBlank) ?: "Google Drive",
                        emailHint = user.optString("emailAddress").takeIf(String::isNotBlank),
                    )
                }
                CloudProvider.ONEDRIVE -> CloudProfile(
                    providerAccountId = json.getString("id"),
                    displayName = json.optString("displayName").takeIf(String::isNotBlank) ?: "OneDrive",
                    emailHint = json.optString("mail").takeIf(String::isNotBlank)
                        ?: json.optString("userPrincipalName").takeIf(String::isNotBlank),
                )
                CloudProvider.DROPBOX -> CloudProfile(
                    providerAccountId = json.getString("account_id"),
                    displayName = json.optJSONObject("name")?.optString("display_name")?.takeIf(String::isNotBlank) ?: "Dropbox",
                    emailHint = json.optString("email").takeIf(String::isNotBlank),
                )
            }
        }
    }

    private fun toAccount(entity: CloudAccountEntity): CloudAccount? {
        val provider = runCatching { CloudProvider.valueOf(entity.provider) }.getOrNull() ?: return null
        val state = if (!tokenVault.contains(entity.authReference)) CloudAuthState.REAUTH_REQUIRED else CloudAuthState.CONNECTED
        return CloudAccount(entity.id, provider, entity.providerAccountId, entity.displayName, entity.emailHint, state)
    }

    private fun updateProviderState(provider: CloudProvider, state: CloudAuthState, error: String? = null) {
        _uiState.value = _uiState.value.copy(
            providerStates = _uiState.value.providerStates + (provider to state),
            error = error?.take(240),
        )
    }

    private data class PendingAuthorization(val verifier: String, val state: String, val createdAtMs: Long)
    private data class CloudProfile(val providerAccountId: String, val displayName: String, val emailHint: String?)

    companion object {
        private const val AUTH_SESSION_MAX_AGE_MS = 10L * 60L * 1000L
        private const val MAX_OAUTH_RESPONSE_CHARS = 1_000_000

        fun productionConfigs(): Map<CloudProvider, CloudOAuthProviderConfig> = listOf(
            CloudOAuthProviderConfig(
                provider = CloudProvider.GOOGLE_DRIVE,
                clientId = BuildConfig.CLOUD_GOOGLE_CLIENT_ID,
                redirectUri = BuildConfig.CLOUD_GOOGLE_REDIRECT_URI,
                authorizationEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
                tokenEndpoint = "https://oauth2.googleapis.com/token",
                scopes = listOf("openid", "email", "profile", "https://www.googleapis.com/auth/drive.readonly"),
                extraAuthorizationParameters = mapOf("access_type" to "offline", "prompt" to "consent"),
            ),
            CloudOAuthProviderConfig(
                provider = CloudProvider.ONEDRIVE,
                clientId = BuildConfig.CLOUD_MICROSOFT_CLIENT_ID,
                redirectUri = BuildConfig.CLOUD_MICROSOFT_REDIRECT_URI,
                authorizationEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                scopes = listOf("openid", "profile", "offline_access", "User.Read", "Files.Read"),
            ),
            CloudOAuthProviderConfig(
                provider = CloudProvider.DROPBOX,
                clientId = BuildConfig.CLOUD_DROPBOX_CLIENT_ID,
                redirectUri = BuildConfig.CLOUD_DROPBOX_REDIRECT_URI,
                authorizationEndpoint = "https://www.dropbox.com/oauth2/authorize",
                tokenEndpoint = "https://api.dropboxapi.com/oauth2/token",
                scopes = listOf("account_info.read", "files.metadata.read", "files.content.read"),
                extraAuthorizationParameters = mapOf("token_access_type" to "offline"),
            ),
        ).associateBy { it.provider }

        private fun initialUiState(configs: Map<CloudProvider, CloudOAuthProviderConfig>) = CloudOAuthUiState(
            providerStates = CloudProvider.entries.associateWith { provider ->
                if (configs[provider]?.configured == true) CloudAuthState.SIGNED_OUT else CloudAuthState.NOT_CONFIGURED
            },
        )

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        internal fun parseTokenResponse(body: String, nowMs: Long): CloudTokenSet {
            val json = JSONObject(body)
            val access = json.optString("access_token").takeIf(String::isNotBlank)
                ?: throw CloudFailure.AuthenticationRequired("OAuth token response did not contain an access token.")
            val expiresIn = json.optLong("expires_in", -1L).takeIf { it > 0L }
            val scopes = json.optString("scope").split(Regex("[ ,]+"))
                .filter(String::isNotBlank).toSet()
            return CloudTokenSet(
                accessToken = access,
                refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank),
                expiresAtMs = expiresIn?.let { seconds -> nowMs + seconds.coerceAtMost(86_400L * 365L) * 1000L },
                scopes = scopes,
            )
        }

        internal fun redirectMatches(actual: Uri, configured: String): Boolean {
            val expected = Uri.parse(configured)
            return actual.scheme.equals(expected.scheme, true) &&
                actual.host.equals(expected.host, true) &&
                actual.port == expected.port &&
                actual.path.orEmpty() == expected.path.orEmpty()
        }

        private fun oauthFailure(provider: CloudProvider, code: Int, body: String): CloudFailure {
            val description = runCatching {
                val json = JSONObject(body)
                json.optString("error_description").takeIf(String::isNotBlank)
                    ?: json.optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
                    ?: json.optString("error_summary").takeIf(String::isNotBlank)
            }.getOrNull()?.take(200)
            return when (code) {
                400, 401 -> CloudFailure.AuthenticationRequired(description ?: "${provider.name} authorization was rejected.")
                403 -> CloudFailure.PermissionDenied(description ?: "${provider.name} denied the requested permission.")
                429 -> CloudFailure.RateLimited(null, description ?: "${provider.name} rate limit reached.")
                else -> CloudFailure.Unavailable(description ?: "${provider.name} authorization failed with HTTP $code.")
            }
        }
    }
}

private class VaultBackedAccessTokenProvider(
    private val provider: CloudProvider,
    private val authReference: String,
    private val tokenVault: CloudTokenVault,
    private val config: CloudOAuthProviderConfig,
    private val httpClient: OkHttpClient,
    private val nowMs: () -> Long,
    private val onState: (CloudAuthState) -> Unit,
) : CloudAccessTokenProvider {
    private val mutex = Mutex()

    override suspend fun accessToken(forceRefresh: Boolean): String = mutex.withLock {
        val current = tokenVault.get(authReference)
            ?: throw CloudFailure.AuthenticationRequired("Cloud credentials are no longer available. Sign in again.")
        val expiresSoon = current.expiresAtMs?.let { it <= nowMs() + TOKEN_REFRESH_SKEW_MS } ?: false
        if (!forceRefresh && !expiresSoon) return@withLock current.accessToken
        val refresh = current.refreshToken
            ?: if (!forceRefresh && current.expiresAtMs == null) return@withLock current.accessToken
            else throw CloudFailure.AuthenticationRequired("This cloud session cannot be refreshed. Sign in again.")

        onState(CloudAuthState.TOKEN_REFRESHING)
        try {
            val refreshed = refresh(refresh, current)
            tokenVault.save(refreshed, authReference)
            onState(CloudAuthState.CONNECTED)
            refreshed.accessToken
        } catch (error: Throwable) {
            onState(CloudAuthState.REAUTH_REQUIRED)
            throw error
        }
    }

    private suspend fun refresh(refreshToken: String, previous: CloudTokenSet): CloudTokenSet = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", config.clientId)
            .build()
        val request = Request.Builder().url(config.tokenEndpoint).post(form).header("Accept", "application/json").build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string().take(1_000_000)
            if (!response.isSuccessful) {
                throw when (response.code) {
                    400, 401 -> CloudFailure.AuthenticationRequired("${provider.name} refresh token is no longer valid.")
                    429 -> CloudFailure.RateLimited(null)
                    else -> CloudFailure.Unavailable("${provider.name} token refresh failed with HTTP ${response.code}.")
                }
            }
            val parsed = CloudOAuthCoordinator.parseTokenResponse(body, nowMs())
            parsed.copy(
                refreshToken = parsed.refreshToken ?: previous.refreshToken,
                scopes = if (parsed.scopes.isEmpty()) previous.scopes else parsed.scopes,
            )
        }
    }

    private companion object {
        const val TOKEN_REFRESH_SKEW_MS = 60_000L
    }
}

object CloudPkce {
    private val random = SecureRandom()

    fun newVerifier(): String = randomUrlSafe(64)
    fun newState(): String = randomUrlSafe(32)

    fun challenge(verifier: String): String {
        require(verifier.length in 43..128) { "PKCE verifier length is invalid." }
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return digest.toByteString().base64Url().trimEnd('=')
    }

    fun constantTimeEquals(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount).also(random::nextBytes)
        return bytes.toByteString().base64Url().trimEnd('=')
    }
}

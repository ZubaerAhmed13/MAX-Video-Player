package com.zubaer.maxvideoplayer.feature.cloud.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zubaer.maxvideoplayer.BuildConfig
import com.zubaer.maxvideoplayer.core.database.CloudAccountDao
import com.zubaer.maxvideoplayer.core.database.CloudAccountEntity
import com.zubaer.maxvideoplayer.feature.cloud.CloudAccount
import com.zubaer.maxvideoplayer.feature.cloud.CloudAuthState
import com.zubaer.maxvideoplayer.feature.cloud.CloudFailure
import com.zubaer.maxvideoplayer.feature.cloud.CloudOAuthUiState
import com.zubaer.maxvideoplayer.feature.cloud.CloudProvider
import com.zubaer.maxvideoplayer.feature.cloud.provider.CloudAccessTokenProvider
import com.zubaer.maxvideoplayer.feature.cloud.provider.CloudProviderClient
import com.zubaer.maxvideoplayer.feature.cloud.provider.DropboxCloudProviderClient
import com.zubaer.maxvideoplayer.feature.cloud.provider.GoogleDriveCloudProviderClient
import com.zubaer.maxvideoplayer.feature.cloud.provider.OneDriveCloudProviderClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** OAuth configuration whose client id / redirect URI are supplied by BuildConfig, never hard-coded credentials. */
data class CloudOAuthProviderConfig(
    val provider: CloudProvider,
    val clientId: String,
    val redirectUri: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val scopes: List<String>,
    val extraAuthorizationParameters: Map<String, String> = emptyMap(),
) {
    val configured: Boolean
        get() = clientId.isNotBlank() && redirectUri.isNotBlank()
}

data class CloudTokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long?,
    val scopes: Set<String>,
)

class CloudTokenVault(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "max_cloud_oauth_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun contains(reference: String): Boolean = preferences.contains(reference)

    fun save(tokens: CloudTokenSet, reference: String = "cloud:${UUID.randomUUID()}"): String {
        val json = JSONObject()
            .put("access_token", tokens.accessToken)
            .put("refresh_token", tokens.refreshToken)
            .put("expires_at_ms", tokens.expiresAtMs)
            .put("scopes", tokens.scopes.joinToString(" "))
        preferences.edit().putString(reference, json.toString()).apply()
        return reference
    }

    fun get(reference: String): CloudTokenSet? {
        val raw = preferences.getString(reference, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            CloudTokenSet(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank),
                expiresAtMs = json.optLong("expires_at_ms", -1L).takeIf { it >= 0L },
                scopes = json.optString("scopes").split(' ').filter(String::isNotBlank).toSet(),
            )
        }.getOrNull()
    }

    fun remove(reference: String) {
        preferences.edit().remove(reference).apply()
    }
}

/**
 * Production OAuth / PKCE coordinator. Tokens remain encrypted at rest and are never persisted in
 * Room, logs, playback URIs or saved-location rows. Provider clients receive only a token supplier.
 */
class CloudOAuthCoordinator(
    context: Context,
    private val accountDao: CloudAccountDao,
    private val tokenVault: CloudTokenVault = CloudTokenVault(context),
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val configs: Map<CloudProvider, CloudOAuthProviderConfig> = productionConfigs(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<CloudProvider, PendingAuthorization>()
    private val _uiState = MutableStateFlow(initialUiState(configs))
    val uiState: StateFlow<CloudOAuthUiState> = _uiState.asStateFlow()

    init {
        scope.launch { refreshAccounts() }
    }

    fun beginAuthorization(provider: CloudProvider): Intent {
        val config = configs[provider]
            ?: throw CloudFailure.Unsupported("OAuth configuration is unavailable for ${provider.name}.")
        if (!config.configured) throw CloudFailure.NotConfigured(provider)
        val verifier = CloudPkce.newVerifier()
        val state = CloudPkce.newState()
        pending[provider] = PendingAuthorization(verifier, state, nowMs())
        updateProviderState(provider, CloudAuthState.AUTHORIZING)
        val uri = Uri.parse(config.authorizationEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("scope", config.scopes.joinToString(" "))
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", CloudPkce.challenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .apply { config.extraAuthorizationParameters.forEach { (key, value) -> appendQueryParameter(key, value) } }
            .build()
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun handleRedirect(uri: Uri): Boolean {
        val provider = configs.values.firstOrNull { redirectMatches(uri, it.redirectUri) }?.provider ?: return false
        val config = configs.getValue(provider)
        val pendingAuthorization = pending.remove(provider)
        if (pendingAuthorization == null || nowMs() - pendingAuthorization.createdAtMs > AUTH_SESSION_MAX_AGE_MS) {
            updateProviderState(provider, CloudAuthState.REAUTH_REQUIRED, "Authorization session expired. Try again.")
            return true
        }
        val returnedState = uri.getQueryParameter("state").orEmpty()
        if (!CloudPkce.constantTimeEquals(pendingAuthorization.state, returnedState)) {
            updateProviderState(provider, CloudAuthState.REAUTH_REQUIRED, "Authorization state did not match. Try again.")
            return true
        }
        val oauthError = uri.getQueryParameter("error")
        if (!oauthError.isNullOrBlank()) {
            updateProviderState(provider, CloudAuthState.SIGNED_OUT, uri.getQueryParameter("error_description") ?: oauthError)
            return true
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            updateProviderState(provider, CloudAuthState.REAUTH_REQUIRED, "Authorization code was missing.")
            return true
        }
        scope.launch { exchangeCode(provider, config, pendingAuthorization.verifier, code) }
        return true
    }

    suspend fun refreshAccounts() {
        val accounts = accountDao.observeAccounts().first().mapNotNull(::toAccount)
        _uiState.value = _uiState.value.copy(accounts = accounts)
    }

    suspend fun signOut(accountId: String) {
        val entity = accountDao.findById(accountId) ?: return
        tokenVault.remove(entity.authReference)
        accountDao.delete(accountId)
        refreshAccounts()
        updateProviderState(runCatching { CloudProvider.valueOf(entity.provider) }.getOrDefault(CloudProvider.GOOGLE_DRIVE), CloudAuthState.SIGNED_OUT)
    }

    suspend fun clientForAccount(accountId: String): CloudProviderClient {
        val entity = accountDao.findById(accountId) ?: throw CloudFailure.NotFound("Cloud account is no longer available.")
        val provider = runCatching { CloudProvider.valueOf(entity.provider) }.getOrNull()
            ?: throw CloudFailure.Unsupported("Unknown cloud provider.")
        val config = configs[provider] ?: throw CloudFailure.NotConfigured(provider)
        val tokenProvider = VaultBackedAccessTokenProvider(
            provider = provider,
            authReference = entity.authReference,
            tokenVault = tokenVault,
            config = config,
            httpClient = httpClient,
            nowMs = nowMs,
            onState = { state -> updateProviderState(provider, state) },
        )
        return when (provider) {
            CloudProvider.GOOGLE_DRIVE -> GoogleDriveCloudProviderClient(httpClient, tokenProvider)
            CloudProvider.ONEDRIVE -> OneDriveCloudProviderClient(httpClient, tokenProvider)
            CloudProvider.DROPBOX -> DropboxCloudProviderClient(httpClient, tokenProvider)
        }
    }

    private suspend fun exchangeCode(
        provider: CloudProvider,
        config: CloudOAuthProviderConfig,
        verifier: String,
        code: String,
    ) = withContext(Dispatchers.IO) {
        try {
            updateProviderState(provider, CloudAuthState.EXCHANGING_CODE)
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
                if (!response.isSuccessful) throw oauthFailure(provider, response.code, body)
                val tokens = parseTokenResponse(body, nowMs())
                val profile = fetchProfile(provider, tokens.accessToken)
                val reference = tokenVault.save(tokens)
                val accountId = "${provider.name.lowercase()}:${profile.providerAccountId}"
                runCatching {
                    accountDao.upsert(
                        CloudAccountEntity(
                            id = accountId,
                            provider = provider.name,
                            providerAccountId = profile.providerAccountId,
                            displayName = profile.displayName,
                            emailHint = profile.emailHint,
                            authReference = reference,
                            createdAtMs = nowMs(),
                            updatedAtMs = nowMs(),
                        ),
                    )
                }.onFailure {
                    tokenVault.remove(reference)
                    throw it
                }
            }
            refreshAccounts()
            updateProviderState(provider, CloudAuthState.CONNECTED)
        } catch (error: Throwable) {
            updateProviderState(provider, CloudAuthState.REAUTH_REQUIRED, error.message ?: "Cloud sign-in failed.")
        }
    }

    private suspend fun fetchProfile(provider: CloudProvider, accessToken: String): CloudProfile = withContext(Dispatchers.IO) {
        val request = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .get()
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
            CloudProvider.ONEDRIVE -> Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me?${'$'}select=id,displayName,mail,userPrincipalName")
                .get()
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
            CloudProvider.DROPBOX -> Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_current_account")
                .post(FormBody.Builder().build())
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
        }
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string().take(MAX_OAUTH_RESPONSE_CHARS)
            if (!response.isSuccessful) throw oauthFailure(provider, response.code, body)
            val json = JSONObject(body)
            when (provider) {
                CloudProvider.GOOGLE_DRIVE -> CloudProfile(
                    providerAccountId = json.optString("sub").takeIf(String::isNotBlank) ?: throw CloudFailure.Unavailable("Google account id was missing."),
                    displayName = json.optString("name").takeIf(String::isNotBlank) ?: "Google Drive",
                    emailHint = json.optString("email").takeIf(String::isNotBlank),
                )
                CloudProvider.ONEDRIVE -> CloudProfile(
                    providerAccountId = json.optString("id").takeIf(String::isNotBlank) ?: throw CloudFailure.Unavailable("OneDrive account id was missing."),
                    displayName = json.optString("displayName").takeIf(String::isNotBlank) ?: "OneDrive",
                    emailHint = json.optString("mail").takeIf(String::isNotBlank)
                        ?: json.optString("userPrincipalName").takeIf(String::isNotBlank),
                )
                CloudProvider.DROPBOX -> CloudProfile(
                    providerAccountId = json.optString("account_id").takeIf(String::isNotBlank) ?: throw CloudFailure.Unavailable("Dropbox account id was missing."),
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

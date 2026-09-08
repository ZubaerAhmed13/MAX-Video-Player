package com.zubaer.maxvideoplayer.feature.cloud.model

import android.net.Uri

enum class CloudProvider {
    GOOGLE_DRIVE,
    ONEDRIVE,
    DROPBOX,
}

enum class CloudAuthState {
    NOT_CONFIGURED,
    SIGNED_OUT,
    AUTHORIZING,
    CONNECTED,
    TOKEN_REFRESHING,
    REAUTH_REQUIRED,
    FAILED,
}

data class CloudAccount(
    val id: String,
    val provider: CloudProvider,
    val providerAccountId: String,
    val displayName: String,
    val emailHint: String?,
    val authState: CloudAuthState,
)

data class CloudFileIdentity(
    val provider: CloudProvider,
    val accountId: String,
    val providerFileId: String,
    val driveId: String? = null,
) {
    /** Stable playback/history identity. Never uses a temporary download URL. */
    val stableId: String = buildString {
        append("cloud:")
        append(provider.name.lowercase())
        append(':')
        append(accountId)
        append(':')
        if (!driveId.isNullOrBlank()) append(driveId).append(':')
        append(providerFileId)
    }
}

data class CloudEntry(
    val identity: CloudFileIdentity,
    val parentId: String?,
    val name: String,
    val isFolder: Boolean,
    val sizeBytes: Long?,
    val mimeType: String?,
    val modifiedAt: String?,
    val revision: String?,
    val canDownload: Boolean = true,
    val pathHint: String? = null,
    val thumbnailUrl: String? = null,
)

data class CloudPage(
    val entries: List<CloudEntry>,
    val nextPageToken: String?,
)

data class CloudPlaybackResource(
    val identity: CloudFileIdentity,
    /** Resolved endpoint. May be short lived and must never be used as media identity. */
    val uri: Uri,
    val mimeType: String?,
    val sizeBytes: Long?,
    val revision: String?,
    val authorizationHeader: String? = null,
    val expiresAtMs: Long? = null,
    val supportsRange: Boolean = true,
)

sealed class CloudFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationRequired(message: String = "Sign in again to continue.") : CloudFailure(message)
    class PermissionDenied(message: String) : CloudFailure(message)
    class RateLimited(val retryAfterMs: Long?, message: String = "Cloud provider rate limit reached.") : CloudFailure(message)
    class FileChanged(message: String = "The cloud file changed while it was open.") : CloudFailure(message)
    class NotFound(message: String) : CloudFailure(message)
    class DownloadDisabled(message: String) : CloudFailure(message)
    class ProviderNotConfigured(provider: CloudProvider) : CloudFailure("${provider.name} is not configured in this build.")
    class Unavailable(message: String, cause: Throwable? = null) : CloudFailure(message, cause)
}

object CloudMediaPolicy {
    const val GOOGLE_FOLDER_MIME = "application/vnd.google-apps.folder"

    fun isGoogleWorkspaceDocument(mimeType: String?): Boolean =
        mimeType?.startsWith("application/vnd.google-apps.") == true && mimeType != GOOGLE_FOLDER_MIME

    fun isPlayableCandidate(entry: CloudEntry): Boolean {
        if (entry.isFolder) return false
        if (entry.identity.provider == CloudProvider.GOOGLE_DRIVE && isGoogleWorkspaceDocument(entry.mimeType)) return false
        val mime = entry.mimeType?.lowercase()
        if (mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true) return true
        val ext = entry.name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts", "flv", "3gp", "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
    }
}

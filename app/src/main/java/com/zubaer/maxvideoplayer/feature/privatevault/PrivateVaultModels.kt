package com.zubaer.maxvideoplayer.feature.privatevault

const val PRIVATE_VAULT_SCHEME = "maxvault"
const val PRIVATE_VAULT_FORMAT_VERSION = 1
const val PRIVATE_VAULT_CHUNK_SIZE = 1024 * 1024

enum class PrivateVaultState {
    UNCONFIGURED,
    LOCKED,
    UNLOCKING,
    UNLOCKED,
    ERROR,
}

enum class PrivateMediaStatus {
    AVAILABLE,
    CORRUPTED,
    MISSING,
}

data class PrivateMediaMetadata(
    val originalDisplayName: String,
    val title: String,
    val mimeType: String?,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val rotationDegrees: Int? = null,
)

data class PrivateVaultItem(
    val vaultId: String,
    val metadata: PrivateMediaMetadata,
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val importedAtMs: Long,
    val status: PrivateMediaStatus,
) {
    val stableUri: String get() = "$PRIVATE_VAULT_SCHEME://$vaultId"
}

enum class PrivateImportMode {
    COPY,
    MOVE,
}

sealed interface PrivateImportResult {
    data class Success(val item: PrivateVaultItem, val originalDeleted: Boolean) : PrivateImportResult
    data class EncryptedCopyCreatedOriginalRemains(
        val item: PrivateVaultItem,
        val reason: String,
    ) : PrivateImportResult
    data class Failure(val message: String) : PrivateImportResult
}

object PrivateVaultIdentity {
    fun isPrivateUri(value: String?): Boolean = value
        ?.substringBefore(':', missingDelimiterValue = "")
        ?.equals(PRIVATE_VAULT_SCHEME, ignoreCase = true) == true

    fun vaultId(value: String?): String? {
        if (!isPrivateUri(value)) return null
        val remainder = value.orEmpty().substringAfter("://", missingDelimiterValue = "")
        return remainder.takeIf { it.isNotBlank() && '/' !in it && '?' !in it && '#' !in it }
    }
}

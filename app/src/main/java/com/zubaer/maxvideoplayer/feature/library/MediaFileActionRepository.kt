package com.zubaer.maxvideoplayer.feature.library

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Android-policy-compliant local media mutations.
 *
 * It never requests MANAGE_EXTERNAL_STORAGE and never assumes a filesystem path. Modern
 * MediaStore mutations return a system confirmation IntentSender. SAF operations are delegated
 * to the owning DocumentsProvider and are reported truthfully when unsupported.
 * Private Vault media is intentionally managed only by PrivateVaultRepository.
 */
class MediaFileActionRepository(private val resolver: ContentResolver) {
    sealed interface Result {
        data class Completed(val resultingUri: String? = null) : Result
        data class ConfirmationRequired(
            val intentSender: IntentSender,
            val retryAfterApproval: Boolean,
        ) : Result
        data class Unsupported(val reason: String) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun delete(media: AppMedia): Result = withContext(Dispatchers.IO) {
        if (media.sourceType == MediaSourceType.PRIVATE) {
            return@withContext Result.Unsupported("Private Vault media must be deleted from Private Vault")
        }
        val uri = runCatching { Uri.parse(media.uri) }.getOrNull()
            ?: return@withContext Result.Failed("Invalid media URI")
        if (media.sourceType == MediaSourceType.NETWORK) {
            return@withContext Result.Unsupported("Network media is not a local file")
        }
        try {
            when (media.sourceType) {
                MediaSourceType.MEDIA_STORE -> deleteMediaStore(uri)
                MediaSourceType.SAF -> {
                    val deleted = DocumentsContract.deleteDocument(resolver, uri)
                    if (deleted) Result.Completed() else Result.Unsupported("This document provider does not allow deletion")
                }
                MediaSourceType.NETWORK -> Result.Unsupported("Network media is not a local file")
                MediaSourceType.PRIVATE -> Result.Unsupported("Private Vault media must be deleted from Private Vault")
            }
        } catch (security: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && security is RecoverableSecurityException) {
                Result.ConfirmationRequired(
                    security.userAction.actionIntent.intentSender,
                    retryAfterApproval = true,
                )
            } else {
                Result.Unsupported("Permission is required to delete this media")
            }
        } catch (failure: Throwable) {
            Result.Failed(failure.message ?: "Delete failed")
        }
    }

    suspend fun rename(media: AppMedia, requestedName: String): Result = withContext(Dispatchers.IO) {
        if (media.sourceType == MediaSourceType.PRIVATE) {
            return@withContext Result.Unsupported("Private Vault media names are managed inside Private Vault")
        }
        val clean = requestedName.trim().take(240)
        if (clean.isBlank()) return@withContext Result.Failed("File name cannot be empty")
        val uri = runCatching { Uri.parse(media.uri) }.getOrNull()
            ?: return@withContext Result.Failed("Invalid media URI")
        if (media.sourceType == MediaSourceType.NETWORK) {
            return@withContext Result.Unsupported("Network media cannot be renamed")
        }
        try {
            when (media.sourceType) {
                MediaSourceType.SAF -> {
                    val renamed = DocumentsContract.renameDocument(resolver, uri, clean)
                    if (renamed != null) Result.Completed(renamed.toString())
                    else Result.Unsupported("This document provider does not allow rename")
                }
                MediaSourceType.MEDIA_STORE -> renameMediaStore(uri, clean)
                MediaSourceType.NETWORK -> Result.Unsupported("Network media cannot be renamed")
                MediaSourceType.PRIVATE -> Result.Unsupported("Private Vault media names are managed inside Private Vault")
            }
        } catch (security: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && security is RecoverableSecurityException -> {
                    Result.ConfirmationRequired(
                        security.userAction.actionIntent.intentSender,
                        retryAfterApproval = true,
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && media.sourceType == MediaSourceType.MEDIA_STORE -> {
                    runCatching {
                        MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender
                    }.fold(
                        onSuccess = { Result.ConfirmationRequired(it, retryAfterApproval = true) },
                        onFailure = { Result.Unsupported("Permission is required to rename this media") },
                    )
                }
                else -> Result.Unsupported("Permission is required to rename this media")
            }
        } catch (failure: Throwable) {
            Result.Failed(failure.message ?: "Rename failed")
        }
    }

    private fun deleteMediaStore(uri: Uri): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Result.ConfirmationRequired(
                intentSender = MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender,
                retryAfterApproval = false,
            )
        }
        val deleted = resolver.delete(uri, null, null)
        return if (deleted > 0) Result.Completed() else Result.Unsupported("MediaStore did not delete this item")
    }

    private fun renameMediaStore(uri: Uri, cleanName: String): Result {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName) }
        val updated = resolver.update(uri, values, null, null)
        return if (updated > 0) Result.Completed(uri.toString())
        else Result.Unsupported("MediaStore did not rename this item")
    }
}

/** Stable-ID-preserving validation used for missing-source relink and post-rename reconciliation. */
object MediaRelinkValidator {
    data class Validation(val accepted: Boolean, val reason: String? = null)

    fun validate(original: AppMedia, replacement: AppMedia): Validation {
        val oldSize = original.sizeBytes
        val newSize = replacement.sizeBytes
        if (oldSize != null && newSize != null) {
            val tolerance = max(1_048_576L, oldSize / 100L)
            if (abs(oldSize - newSize) > tolerance) {
                return Validation(false, "Selected file size does not plausibly match the missing media")
            }
        }
        val oldDuration = original.durationMs
        val newDuration = replacement.durationMs
        if (oldDuration != null && oldDuration > 0L && newDuration != null && newDuration > 0L) {
            val tolerance = max(2_000L, oldDuration / 50L)
            if (abs(oldDuration - newDuration) > tolerance) {
                return Validation(false, "Selected file duration does not plausibly match the missing media")
            }
        }
        if (replacement.mimeType != null && !replacement.mimeType.startsWith("video/")) {
            return Validation(false, "Selected source is not a video")
        }
        return Validation(true)
    }
}

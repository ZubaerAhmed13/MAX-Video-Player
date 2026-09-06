package com.zubaer.maxvideoplayer.feature.library

import com.zubaer.maxvideoplayer.core.database.MediaIndexEntity
import com.zubaer.maxvideoplayer.core.model.AppMedia

/**
 * Reuses an already-known provider URI's app-level stable ID when a fresh scan recomputes a
 * different fallback ID (for example after a rename changed DISPLAY_NAME/title).
 *
 * The caller supplies rows for one source only, so URI equality is a provider/source-local
 * identity signal. When a transient duplicate already exists for the newly scanned fallback ID,
 * prefer the different historical ID so favourites/playlists/history stay attached to the
 * identity that existed before the rename.
 */
internal object LibraryIndexReconciler {
    fun preserveKnownStableIds(
        scanned: List<AppMedia>,
        existing: List<MediaIndexEntity>,
    ): List<AppMedia> {
        if (scanned.isEmpty() || existing.isEmpty()) return scanned
        val existingByUri = existing.groupBy(MediaIndexEntity::uri)
        return scanned.map { media ->
            val candidates = existingByUri[media.uri].orEmpty()
            val preserved = candidates.firstOrNull { it.stableMediaId != media.stableId }
                ?: candidates.firstOrNull()
            if (preserved == null || preserved.stableMediaId == media.stableId) media
            else media.copy(stableId = preserved.stableMediaId)
        }
    }
}

internal data class SafScanFailureDisposition(
    val sourceStatus: String,
    val rethrow: Boolean,
)

/** All real SAF scan failures invalidate previously indexed availability consistently. */
internal object SafScanFailurePolicy {
    fun classify(failure: Exception): SafScanFailureDisposition =
        if (failure is SecurityException) {
            SafScanFailureDisposition(
                sourceStatus = LibraryRepository.STATUS_PERMISSION_LOST,
                rethrow = false,
            )
        } else {
            SafScanFailureDisposition(
                sourceStatus = LibraryRepository.STATUS_UNAVAILABLE,
                rethrow = true,
            )
        }
}

package com.zubaer.maxvideoplayer.feature.library

import com.zubaer.maxvideoplayer.core.database.MediaIndexEntity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScanHardeningTest {
    @Test fun rescanAfterRenameKeepsHistoricalStableId() {
        val renamedUri = "content://media/external/video/media/42"
        val existing = listOf(indexRow(stableId = "stable-before-rename", uri = renamedUri))
        val scanned = listOf(
            AppMedia(
                stableId = "fallback-after-rename",
                uri = renamedUri,
                title = "Renamed video",
                fileName = "Renamed video.mp4",
                mimeType = "video/mp4",
                sizeBytes = 5_000L,
                sourceId = "mediastore:videos",
                sourceType = MediaSourceType.MEDIA_STORE,
            )
        )

        val reconciled = LibraryIndexReconciler.preserveKnownStableIds(scanned, existing)

        assertEquals("stable-before-rename", reconciled.single().stableId)
        assertEquals("Renamed video.mp4", reconciled.single().fileName)
    }

    @Test fun historicalStableIdWinsOverTransientFallbackDuplicateForSameUri() {
        val uri = "content://provider/document/renamed"
        val scanned = listOf(
            AppMedia(
                stableId = "fresh-fallback",
                uri = uri,
                title = "Renamed",
                fileName = "Renamed.mkv",
                sourceId = "saf-tree:test",
                sourceType = MediaSourceType.SAF,
            )
        )
        val existing = listOf(
            indexRow(stableId = "fresh-fallback", uri = uri, sourceId = "saf-tree:test", sourceType = "SAF"),
            indexRow(stableId = "stable-before-rename", uri = uri, sourceId = "saf-tree:test", sourceType = "SAF"),
        )

        val reconciled = LibraryIndexReconciler.preserveKnownStableIds(scanned, existing)

        assertEquals("stable-before-rename", reconciled.single().stableId)
    }

    @Test fun safPermissionFailureAndGenericFailureBothInvalidateIndexedAvailability() {
        val permission = SafScanFailurePolicy.classify(SecurityException("permission lost"))
        assertEquals(LibraryRepository.STATUS_PERMISSION_LOST, permission.sourceStatus)
        assertFalse(permission.rethrow)

        val providerFailure = SafScanFailurePolicy.classify(IllegalStateException("provider unavailable"))
        assertEquals(LibraryRepository.STATUS_UNAVAILABLE, providerFailure.sourceStatus)
        assertTrue(providerFailure.rethrow)
    }

    private fun indexRow(
        stableId: String,
        uri: String,
        sourceId: String = "mediastore:videos",
        sourceType: String = "MEDIA_STORE",
    ) = MediaIndexEntity(
        stableMediaId = stableId,
        sourceId = sourceId,
        uri = uri,
        title = "Renamed video",
        fileName = "Renamed video.mp4",
        mimeType = "video/mp4",
        durationMs = 10_000L,
        sizeBytes = 5_000L,
        width = 1920,
        height = 1080,
        dateAddedMs = 1L,
        dateModifiedMs = 2L,
        relativePath = null,
        folderKey = null,
        folderName = null,
        sourceType = sourceType,
        availability = "AVAILABLE",
    )
}

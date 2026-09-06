package com.zubaer.maxvideoplayer.feature.library

import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.core.model.SourceAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQueuePlannerTest {
    @Test fun selectingMiddleItemPreservesVisibleFolderOrPlaylistOrdering() {
        val a = media("a")
        val b = media("b")
        val c = media("c")

        val request = LibraryQueuePlanner.create(listOf(a, b, c), b)

        assertEquals(listOf("a", "b", "c"), request.queue.map { it.stableId })
        assertEquals(1, request.startIndex)
        assertTrue(request.startIndex > 0)
        assertTrue(request.startIndex < request.queue.lastIndex)
        assertEquals("a", request.queue[request.startIndex - 1].stableId)
        assertEquals("c", request.queue[request.startIndex + 1].stableId)
    }

    @Test fun unavailablePlaylistRowsAreVisibleButNotSentToPlaybackQueue() {
        val a = media("a")
        val missing = media("missing", SourceAvailability.UNAVAILABLE)
        val c = media("c")

        val request = LibraryQueuePlanner.create(listOf(a, missing, c), c)

        assertEquals(listOf("a", "c"), request.queue.map { it.stableId })
        assertFalse(request.queue.any { it.stableId == "missing" })
        assertEquals(1, request.startIndex)
    }

    @Test fun selectedExternalOrRecoveryItemStillGetsAPlayableSingleItemQueue() {
        val visible = listOf(media("a"), media("b"))
        val selected = media("external")

        val request = LibraryQueuePlanner.create(visible, selected)

        assertEquals(listOf("external"), request.queue.map { it.stableId })
        assertEquals(0, request.startIndex)
    }

    private fun media(id: String, availability: SourceAvailability = SourceAvailability.AVAILABLE) = AppMedia(
        stableId = id,
        uri = "content://media/$id",
        title = id,
        mimeType = "video/mp4",
        durationMs = 120_000L,
        sizeBytes = 4_000_000_000L,
        width = 1920,
        height = 1080,
        availability = availability,
        sourceType = MediaSourceType.MEDIA_STORE,
    )
}

package com.zubaer.maxvideoplayer.feature.library

import com.zubaer.maxvideoplayer.core.database.MediaHistoryEntity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.core.model.SourceAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQueryEngineTest {
    @Test fun searchSortAndFilterTenThousandItemsDeterministically() {
        val media = (0 until 10_000).map { index ->
            media(
                id = "id-$index",
                title = "Video %05d".format(index),
                duration = index.toLong() * 1_000L,
                size = 5_000_000_000L + index,
                modified = index.toLong(),
            )
        }
        val result = LibraryQueryEngine.apply(
            input = media,
            query = "  VIDEO   09999 ",
            sort = VideoSort.NAME,
            direction = SortDirection.ASCENDING,
            filter = LibraryFilter.ALL,
            history = emptyMap(),
            favouriteIds = emptySet(),
        )
        assertEquals(1, result.size)
        assertEquals("id-9999", result.single().stableId)
        assertTrue(result.single().sizeBytes!! > Int.MAX_VALUE.toLong())
    }

    @Test fun everySortSupportsDirectionsAndNulls() {
        val a = media("a", "Alpha", 10L, 20L, 30L, width = 1920, height = 1080)
        val b = media("b", "Beta", null, null, null, width = null, height = null)
        val history = mapOf("a" to history("a", 100L), "b" to history("b", 200L))
        VideoSort.entries.forEach { sort ->
            val asc = LibraryQueryEngine.apply(listOf(b, a), "", sort, SortDirection.ASCENDING, LibraryFilter.ALL, history, emptySet())
            val desc = LibraryQueryEngine.apply(listOf(b, a), "", sort, SortDirection.DESCENDING, LibraryFilter.ALL, history, emptySet())
            assertEquals(2, asc.size)
            assertEquals(asc.map { it.stableId }.reversed(), desc.map { it.stableId })
        }
    }

    @Test fun continueWatchingUsesExistingResumePolicy() {
        val early = history("early", 1L, position = 1_000L, duration = 120_000L)
        val progress = history("progress", 2L, position = 45_000L, duration = 120_000L)
        val complete = history("complete", 3L, position = 0L, duration = 120_000L, completed = true)
        assertFalse(LibraryQueryEngine.isContinueWatching(early))
        assertTrue(LibraryQueryEngine.isContinueWatching(progress))
        assertFalse(LibraryQueryEngine.isContinueWatching(complete))
    }

    @Test fun playlistOrderCanBePreservedAndUnavailableEntryShown() {
        val first = media("first", "Zeta", 1L, 1L, 1L)
        val missing = media("missing", "Alpha", 1L, 1L, 1L, availability = SourceAvailability.UNAVAILABLE)
        val result = LibraryQueryEngine.apply(
            input = listOf(first, missing),
            query = "",
            sort = VideoSort.NAME,
            direction = SortDirection.ASCENDING,
            filter = LibraryFilter.ALL,
            history = emptyMap(),
            favouriteIds = emptySet(),
            includeUnavailable = true,
            preserveInputOrder = true,
        )
        assertEquals(listOf("first", "missing"), result.map { it.stableId })
    }

    private fun media(
        id: String,
        title: String,
        duration: Long?,
        size: Long?,
        modified: Long?,
        width: Int? = 1280,
        height: Int? = 720,
        availability: SourceAvailability = SourceAvailability.AVAILABLE,
    ) = AppMedia(
        stableId = id,
        uri = "content://media/$id",
        title = title,
        fileName = "$title.mp4",
        mimeType = "video/mp4",
        durationMs = duration,
        sizeBytes = size,
        width = width,
        height = height,
        dateAddedMs = modified,
        dateModifiedMs = modified,
        folderKey = "folder-a",
        folderName = "Movies",
        availability = availability,
        sourceType = MediaSourceType.MEDIA_STORE,
    )

    private fun history(
        id: String,
        playedAt: Long,
        position: Long = 30_000L,
        duration: Long = 120_000L,
        completed: Boolean = false,
    ) = MediaHistoryEntity(
        stableMediaId = id,
        uri = "content://media/$id",
        title = id,
        mimeType = "video/mp4",
        sizeBytes = 1L,
        width = 1280,
        height = 720,
        lastPositionMs = position,
        durationMs = duration,
        lastPlayedAtMs = playedAt,
        completed = completed,
    )
}

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

    @Test fun professionalFiltersCoverProgressResolutionDurationAndFavourites() {
        val short720 = media("short720", "Short HD", 300_000L, 1L, 1L, width = 1280, height = 720)
        val long1080 = media("long1080", "Long FHD", 4_000_000L, 1L, 2L, width = 1920, height = 1080)
        val uhd = media("uhd", "4K", 7_200_000L, 1L, 3L, width = 3840, height = 2160)
        val history = mapOf(
            "short720" to history("short720", 1L, completed = true),
            "long1080" to history("long1080", 2L, position = 45_000L, duration = 120_000L),
        )
        val all = listOf(short720, long1080, uhd)

        assertEquals(listOf("short720"), ids(all, LibraryFilter.WATCHED, history, emptySet()))
        assertEquals(listOf("uhd"), ids(all, LibraryFilter.UNWATCHED, history, emptySet()))
        assertEquals(listOf("long1080"), ids(all, LibraryFilter.IN_PROGRESS, history, emptySet()))
        assertEquals(listOf("uhd"), ids(all, LibraryFilter.FAVOURITES, history, setOf("uhd")))
        assertEquals(setOf("short720", "long1080", "uhd"), ids(all, LibraryFilter.RESOLUTION_720P_PLUS, history, emptySet()).toSet())
        assertEquals(setOf("long1080", "uhd"), ids(all, LibraryFilter.RESOLUTION_1080P_PLUS, history, emptySet()).toSet())
        assertEquals(listOf("uhd"), ids(all, LibraryFilter.RESOLUTION_2160P, history, emptySet()))
        assertEquals(listOf("short720"), ids(all, LibraryFilter.UNDER_10_MINUTES, history, emptySet()))
        assertEquals(setOf("long1080", "uhd"), ids(all, LibraryFilter.OVER_60_MINUTES, history, emptySet()).toSet())
    }

    @Test fun folderSortingKeepsSameNamedFoldersDistinctAndSupportsEveryMode() {
        val moviesInternal = FolderItem("mediastore:bucket:1", "Movies", listOf(media("a", "A", 1L, 10L, 10L)), 10L, 10L)
        val moviesSaf = FolderItem(
            "saf:source:document-7",
            "Movies",
            listOf(media("b", "B", 1L, 20L, 20L), media("c", "C", 1L, 30L, 30L)),
            50L,
            30L,
        )
        val downloads = FolderItem("mediastore:bucket:3", "Downloads", listOf(media("d", "D", 1L, 5L, 5L)), 5L, 5L)
        val folders = listOf(moviesInternal, moviesSaf, downloads)

        FolderSort.entries.forEach { sort ->
            val asc = LibraryFolderEngine.sort(folders, sort, SortDirection.ASCENDING)
            val desc = LibraryFolderEngine.sort(folders, sort, SortDirection.DESCENDING)
            assertEquals(3, asc.size)
            assertEquals(asc.map { it.key }.reversed(), desc.map { it.key })
        }
        assertEquals(2, folders.count { it.name == "Movies" })
        assertTrue(folders.map { it.key }.distinct().size == folders.size)
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

    private fun ids(
        media: List<AppMedia>,
        filter: LibraryFilter,
        history: Map<String, MediaHistoryEntity>,
        favourites: Set<String>,
    ) = LibraryQueryEngine.apply(
        input = media,
        query = "",
        sort = VideoSort.NAME,
        direction = SortDirection.ASCENDING,
        filter = filter,
        history = history,
        favouriteIds = favourites,
    ).map { it.stableId }

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

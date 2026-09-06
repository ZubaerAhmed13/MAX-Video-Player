package com.zubaer.maxvideoplayer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaxDatabaseTest {
    private lateinit var database: MaxDatabase

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MaxDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun insertUpdateAndReadHistory() = runTest {
        val dao = database.mediaHistoryDao()
        val first = MediaHistoryEntity("id", "content://id", "Video", "video/mp4", 3_500_000_000L, 3840, 2160, 42_000L, 120_000L, 1L, false)
        dao.upsert(first)
        assertEquals(42_000L, dao.get("id")?.lastPositionMs)
        dao.upsert(first.copy(lastPositionMs = 0L, completed = true))
        val updated = dao.get("id")!!
        assertEquals(0L, updated.lastPositionMs)
        assertEquals(3_500_000_000L, updated.sizeBytes)
        assertFalse(updated.title.isBlank())
    }

    @Test fun favouritesPersistWithoutDuplicatingMediaRows() = runTest {
        val dao = database.favouriteDao()
        dao.upsert(FavouriteEntity("media-a", 10L))
        dao.upsert(FavouriteEntity("media-a", 20L))
        dao.upsert(FavouriteEntity("media-b", 30L))

        assertEquals(setOf("media-a", "media-b"), dao.ids().toSet())
        assertEquals(2, dao.observeAll().first().size)

        dao.delete("media-a")
        assertEquals(listOf("media-b"), dao.ids())
    }

    @Test fun playlistItemsPersistInOrderAndCascadeOnPlaylistDelete() = runTest {
        val dao = database.playlistDao()
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Weekend", createdAtMs = 1L, updatedAtMs = 1L))
        assertTrue(playlistId > 0L)

        assertTrue(dao.insertItem(PlaylistItemEntity(playlistId, "a", 0, 2L)) > 0L)
        assertTrue(dao.insertItem(PlaylistItemEntity(playlistId, "b", 1, 3L)) > 0L)
        assertEquals(-1L, dao.insertItem(PlaylistItemEntity(playlistId, "a", 2, 4L)))
        assertEquals(listOf("a", "b"), dao.items(playlistId).map { it.stableMediaId })

        // Exercise the same collision-safe two-phase strategy used by LibraryRepository.
        dao.updateOrder(playlistId, "a", -2)
        dao.updateOrder(playlistId, "b", -1)
        dao.updateOrder(playlistId, "b", 0)
        dao.updateOrder(playlistId, "a", 1)
        assertEquals(listOf("b", "a"), dao.items(playlistId).map { it.stableMediaId })

        dao.deletePlaylist(playlistId)
        assertTrue(dao.items(playlistId).isEmpty())
    }

    @Test fun librarySourcesExclusionsIndexAndPreferencesPersistIndependently() = runTest {
        database.librarySourceDao().upsert(
            LibrarySourceEntity(
                id = "saf-tree:test",
                uri = "content://tree/test",
                displayName = "Movies",
                sourceType = "SAF_TREE",
                status = "AVAILABLE",
                permissionPersisted = true,
                addedAtMs = 1L,
                lastScanAtMs = 2L,
            )
        )
        database.excludedFolderDao().upsert(ExcludedFolderEntity("folder:test", "Temp", 3L))
        database.mediaIndexDao().upsertAll(
            listOf(
                MediaIndexEntity(
                    stableMediaId = "media-large",
                    sourceId = "saf-tree:test",
                    uri = "content://tree/test/video",
                    title = "Large video",
                    fileName = "large.mkv",
                    mimeType = "video/x-matroska",
                    durationMs = 18_000_000L,
                    sizeBytes = 10_000_000_000L,
                    width = 3840,
                    height = 2160,
                    dateAddedMs = 4L,
                    dateModifiedMs = 5L,
                    relativePath = null,
                    folderKey = "folder:test",
                    folderName = "Movies",
                    sourceType = "SAF",
                    availability = "AVAILABLE",
                )
            )
        )
        database.libraryPreferenceDao().upsert(LibraryPreferenceEntity("library.view_mode", "GRID"))

        assertEquals("Movies", database.librarySourceDao().get("saf-tree:test")?.displayName)
        assertEquals(listOf("folder:test"), database.excludedFolderDao().keys())
        assertEquals(10_000_000_000L, database.mediaIndexDao().get("media-large")?.sizeBytes)
        assertEquals("GRID", database.libraryPreferenceDao().get("library.view_mode")?.value)
    }
}
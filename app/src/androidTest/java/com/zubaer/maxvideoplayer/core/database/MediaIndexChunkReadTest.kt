package com.zubaer.maxvideoplayer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaIndexChunkReadTest {
    private lateinit var database: MaxDatabase

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MaxDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun mediaIndexCanBeReadInDeterministicBoundedChunks() = runTest {
        val dao = database.mediaIndexDao()
        val total = 1_200
        dao.upsertAll((0 until total).map(::row))

        assertEquals(total, dao.observeCount().first())

        val first = dao.page(limit = 512, offset = 0)
        val second = dao.page(limit = 512, offset = 512)
        val third = dao.page(limit = 512, offset = 1_024)

        assertEquals(512, first.size)
        assertEquals(512, second.size)
        assertEquals(176, third.size)
        assertEquals("media-0000", first.first().stableMediaId)
        assertEquals("media-0511", first.last().stableMediaId)
        assertEquals("media-0512", second.first().stableMediaId)
        assertEquals("media-1023", second.last().stableMediaId)
        assertEquals("media-1024", third.first().stableMediaId)
        assertEquals("media-1199", third.last().stableMediaId)

        val allIds = (first + second + third).map { it.stableMediaId }
        assertEquals(total, allIds.size)
        assertEquals(total, allIds.toSet().size)
        assertTrue(allIds.zipWithNext().all { (a, b) -> a < b })
    }

    private fun row(index: Int): MediaIndexEntity {
        val id = "media-%04d".format(index)
        return MediaIndexEntity(
            stableMediaId = id,
            sourceId = "mediastore:videos",
            uri = "content://media/external/video/media/$index",
            title = id,
            fileName = "$id.mp4",
            mimeType = "video/mp4",
            durationMs = 7_200_000L + index,
            sizeBytes = 10_000_000_000L + index,
            width = 3840,
            height = 2160,
            dateAddedMs = index.toLong(),
            dateModifiedMs = index.toLong(),
            relativePath = "Movies/",
            folderKey = "mediastore:bucket:movies",
            folderName = "Movies",
            sourceType = "MEDIA_STORE",
            availability = "AVAILABLE",
        )
    }
}

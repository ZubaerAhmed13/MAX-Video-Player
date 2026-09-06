package com.zubaer.maxvideoplayer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}

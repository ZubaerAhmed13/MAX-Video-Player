package com.zubaer.maxvideoplayer.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaxDatabaseMigrationTest {
    @Test fun migration1To2PreservesStep1HistoryAndLongValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-1-2-test.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS media_history (stableMediaId TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, title TEXT NOT NULL, mimeType TEXT, sizeBytes INTEGER, width INTEGER, height INTEGER, lastPositionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, lastPlayedAtMs INTEGER NOT NULL, completed INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS playback_preferences (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL(
                "INSERT INTO media_history(stableMediaId,uri,title,mimeType,sizeBytes,width,height,lastPositionMs,durationMs,lastPlayedAtMs,completed) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf("legacy-id", "content://legacy/video", "Legacy Video", "video/mp4", 5_500_000_000L, 3840, 2160, 88_000L, 180_000L, 1234L, 0),
            )
            db.version = 1
        }

        val migrated = Room.databaseBuilder(context, MaxDatabase::class.java, name)
            .addMigrations(MaxDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            migrated.openHelper.writableDatabase
            val history = migrated.mediaHistoryDao().getBlockingForMigrationTest("legacy-id")
            assertEquals(5_500_000_000L, history?.sizeBytes)
            assertEquals(88_000L, history?.lastPositionMs)
            assertEquals(180_000L, history?.durationMs)
            assertFalse(history?.completed ?: true)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}

package com.zubaer.maxvideoplayer.core.database

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaxDatabaseMigrationTest {
    @Test
    fun migration1To3PreservesStep1HistoryAndLongValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-1-3-test.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            createV1Tables(db)
            db.execSQL(
                "INSERT INTO media_history(stableMediaId,uri,title,mimeType,sizeBytes,width,height,lastPositionMs,durationMs,lastPlayedAtMs,completed) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>("legacy-id", "content://legacy/video", "Legacy Video", "video/mp4", 5_500_000_000L, 3840, 2160, 88_000L, 180_000L, 1234L, 0),
            )
            db.version = 1
        }

        val migrated = Room.databaseBuilder(context, MaxDatabase::class.java, name)
            .addMigrations(MaxDatabase.MIGRATION_1_2, MaxDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            migrated.openHelper.writableDatabase
            val history = migrated.mediaHistoryDao().getBlockingForMigrationTest("legacy-id")
            assertEquals(5_500_000_000L, history?.sizeBytes)
            assertEquals(88_000L, history?.lastPositionMs)
            assertEquals(180_000L, history?.durationMs)
            assertFalse(history?.completed ?: true)
            assertTrue(migrated.subtitleDao().associationsForMediaBlockingForMigrationTest("legacy-id").isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration2To3PreservesStep2LibraryPlaylistsFavouritesAndPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-2-3-test.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            createV2Tables(db)
            db.execSQL("INSERT INTO media_history VALUES('media-A','content://video/A','Movie A','video/mp4',5500000000,3840,2160,12000,180000,99,0)")
            db.execSQL("INSERT INTO playback_preferences VALUES('speed','1.25')")
            db.execSQL("INSERT INTO favourites VALUES('media-A',100)")
            db.execSQL("INSERT INTO playlists(id,name,createdAtMs,updatedAtMs) VALUES(7,'Watchlist',100,200)")
            db.execSQL("INSERT INTO playlist_items VALUES(7,'media-A',0,200)")
            db.execSQL("INSERT INTO library_sources VALUES('tree-A','content://provider/tree/root','Movies','SAF','AVAILABLE',1,100,NULL)")
            db.execSQL("INSERT INTO media_index VALUES('media-A','tree-A','content://video/A','Movie A','Movie.A.mkv','video/x-matroska',180000,5500000000,3840,2160,10,20,'Movies/','folder-A','Movies','SAF','AVAILABLE')")
            db.execSQL("INSERT INTO library_preferences VALUES('view_mode','grid')")
            db.version = 2
        }

        val migrated = Room.databaseBuilder(context, MaxDatabase::class.java, name)
            .addMigrations(MaxDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            migrated.openHelper.writableDatabase
            assertEquals(5_500_000_000L, migrated.mediaHistoryDao().getBlockingForMigrationTest("media-A")?.sizeBytes)
            assertTrue("media-A" in migrated.favouriteDao().ids())
            assertEquals("Watchlist", migrated.playlistDao().getPlaylist(7)?.name)
            assertEquals("media-A", migrated.playlistDao().items(7).single().stableMediaId)
            assertEquals("Movie A", migrated.mediaIndexDao().get("media-A")?.title)
            assertEquals("grid", migrated.libraryPreferenceDao().get("view_mode")?.value)
            assertEquals("1.25", migrated.playbackPreferenceDao().get("speed")?.value)

            migrated.subtitleDao().upsertAssociation(
                SubtitleAssociationEntity(
                    id = "sub-1",
                    stableMediaId = "media-A",
                    subtitleUri = "content://subtitle/movie.en.srt",
                    displayName = "Movie.A.en.srt",
                    language = "en",
                    mimeType = MimeTypes.APPLICATION_SUBRIP,
                    format = "SRT",
                    encoding = "UTF_8",
                    addedAtMs = 300,
                    isPreferred = true,
                    availability = "AVAILABLE",
                    delayMs = 750L,
                ),
            )
            migrated.subtitleDao().upsertMediaState(SubtitleMediaStateEntity("media-A", "sub-1", 750L, 301L))
            assertEquals(1, migrated.subtitleDao().associationsForMediaBlockingForMigrationTest("media-A").size)
            assertEquals(750L, migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-A")?.delayMs)
            assertNotNull(migrated.subtitleDao().association("sub-1"))
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private fun createV1Tables(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS media_history (stableMediaId TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, title TEXT NOT NULL, mimeType TEXT, sizeBytes INTEGER, width INTEGER, height INTEGER, lastPositionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, lastPlayedAtMs INTEGER NOT NULL, completed INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS playback_preferences (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
    }

    private fun createV2Tables(db: android.database.sqlite.SQLiteDatabase) {
        createV1Tables(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS `favourites` (`stableMediaId` TEXT NOT NULL, `addedAtMs` INTEGER NOT NULL, PRIMARY KEY(`stableMediaId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_name` ON `playlists` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_items` (`playlistId` INTEGER NOT NULL, `stableMediaId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `addedAtMs` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `stableMediaId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId` ON `playlist_items` (`playlistId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_items_playlistId_orderIndex` ON `playlist_items` (`playlistId`, `orderIndex`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `library_sources` (`id` TEXT NOT NULL, `uri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `status` TEXT NOT NULL, `permissionPersisted` INTEGER NOT NULL, `addedAtMs` INTEGER NOT NULL, `lastScanAtMs` INTEGER, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `excluded_folders` (`folderKey` TEXT NOT NULL, `displayName` TEXT NOT NULL, `excludedAtMs` INTEGER NOT NULL, PRIMARY KEY(`folderKey`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `media_index` (`stableMediaId` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `uri` TEXT NOT NULL, `title` TEXT NOT NULL, `fileName` TEXT, `mimeType` TEXT, `durationMs` INTEGER, `sizeBytes` INTEGER, `width` INTEGER, `height` INTEGER, `dateAddedMs` INTEGER, `dateModifiedMs` INTEGER, `relativePath` TEXT, `folderKey` TEXT, `folderName` TEXT, `sourceType` TEXT NOT NULL, `availability` TEXT NOT NULL, PRIMARY KEY(`stableMediaId`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_sourceId` ON `media_index` (`sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_folderKey` ON `media_index` (`folderKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_title` ON `media_index` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_index_dateModifiedMs` ON `media_index` (`dateModifiedMs`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `library_preferences` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
    }
}

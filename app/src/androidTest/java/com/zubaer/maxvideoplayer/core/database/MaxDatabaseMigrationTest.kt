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
    fun migration1To4PreservesStep1HistoryAndLongValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-1-4-test.db"
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
            .addMigrations(MaxDatabase.MIGRATION_1_2, MaxDatabase.MIGRATION_2_3, MaxDatabase.MIGRATION_3_4)
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
            assertTrue(migrated.audioDao().associationsForMediaBlockingForMigrationTest("legacy-id").isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration2To4PreservesStep2LibraryAndAllowsStep4AndStep5State() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-2-4-test.db"
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
            .addMigrations(MaxDatabase.MIGRATION_2_3, MaxDatabase.MIGRATION_3_4)
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
            migrated.audioDao().upsertAssociation(
                AudioAssociationEntity("audio-1", "media-A", "content://audio/en.m4a", "English.m4a", "en", "audio/mp4", 400L, true, "AVAILABLE"),
            )
            migrated.audioDao().upsertMediaState(
                AudioMediaStateEntity("media-A", "audio-1", "MANUAL", "en", "English.m4a", "audio/mp4", 2, 250L, 401L),
            )
            assertEquals(1, migrated.subtitleDao().associationsForMediaBlockingForMigrationTest("media-A").size)
            assertEquals(750L, migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-A")?.delayMs)
            assertNotNull(migrated.subtitleDao().association("sub-1"))
            assertEquals("audio-1", migrated.audioDao().mediaStateBlockingForMigrationTest("media-A")?.selectedExternalId)
            assertEquals(250L, migrated.audioDao().mediaStateBlockingForMigrationTest("media-A")?.delayMs)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration3To4PreservesAllStep1To4RowsAndCreatesAudioTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-3-4-test.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            createV3Tables(db)
            db.execSQL("INSERT INTO media_history VALUES('media-B','content://video/B','Movie B','video/mp4',6200000000,3840,2160,45000,240000,500,0)")
            db.execSQL("INSERT INTO playback_preferences VALUES('speed','1.50')")
            db.execSQL("INSERT INTO favourites VALUES('media-B',501)")
            db.execSQL("INSERT INTO playlists(id,name,createdAtMs,updatedAtMs) VALUES(9,'Step4',501,502)")
            db.execSQL("INSERT INTO playlist_items VALUES(9,'media-B',0,503)")
            db.execSQL("INSERT INTO library_sources VALUES('tree-B','content://provider/tree/b','Library B','SAF','AVAILABLE',1,504,NULL)")
            db.execSQL("INSERT INTO media_index VALUES('media-B','tree-B','content://video/B','Movie B','Movie.B.mkv','video/x-matroska',240000,6200000000,3840,2160,10,20,'Movies/','folder-B','Movies','SAF','AVAILABLE')")
            db.execSQL("INSERT INTO library_preferences VALUES('view_mode','list')")
            db.execSQL("INSERT INTO subtitle_associations VALUES('sub-B','media-B','content://subtitle/B.ass','Movie.B.ass','en','text/x-ssa','ASS','UTF_8',505,1,'AVAILABLE',-500)")
            db.execSQL("INSERT INTO subtitle_media_state VALUES('media-B','sub-B',-500,506)")
            db.version = 3
        }

        val migrated = Room.databaseBuilder(context, MaxDatabase::class.java, name)
            .addMigrations(MaxDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            migrated.openHelper.writableDatabase
            assertEquals(6_200_000_000L, migrated.mediaHistoryDao().getBlockingForMigrationTest("media-B")?.sizeBytes)
            assertTrue("media-B" in migrated.favouriteDao().ids())
            assertEquals("Step4", migrated.playlistDao().getPlaylist(9)?.name)
            assertEquals("media-B", migrated.playlistDao().items(9).single().stableMediaId)
            assertEquals("Movie B", migrated.mediaIndexDao().get("media-B")?.title)
            assertEquals("list", migrated.libraryPreferenceDao().get("view_mode")?.value)
            assertEquals("1.50", migrated.playbackPreferenceDao().get("speed")?.value)
            assertEquals("sub-B", migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-B")?.selectedExternalId)
            assertEquals(-500L, migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-B")?.delayMs)
            assertTrue(migrated.audioDao().associationsForMediaBlockingForMigrationTest("media-B").isEmpty())

            migrated.audioDao().upsertAssociation(
                AudioAssociationEntity("audio-B", "media-B", "content://audio/B.flac", "Movie.B.en.flac", "en", "audio/flac", 600L, true, "AVAILABLE"),
            )
            migrated.audioDao().upsertMediaState(
                AudioMediaStateEntity("media-B", "audio-B", "MANUAL", "en", "Movie.B.en.flac", "audio/flac", 2, 180L, 601L),
            )
            assertEquals("audio-B", migrated.audioDao().mediaStateBlockingForMigrationTest("media-B")?.selectedExternalId)
            assertEquals(180L, migrated.audioDao().mediaStateBlockingForMigrationTest("media-B")?.delayMs)
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

    private fun createV3Tables(db: android.database.sqlite.SQLiteDatabase) {
        createV2Tables(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS `subtitle_associations` (`id` TEXT NOT NULL, `stableMediaId` TEXT NOT NULL, `subtitleUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `language` TEXT, `mimeType` TEXT NOT NULL, `format` TEXT NOT NULL, `encoding` TEXT NOT NULL, `addedAtMs` INTEGER NOT NULL, `isPreferred` INTEGER NOT NULL, `availability` TEXT NOT NULL, `delayMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_associations_stableMediaId` ON `subtitle_associations` (`stableMediaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_associations_stableMediaId_isPreferred` ON `subtitle_associations` (`stableMediaId`, `isPreferred`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `subtitle_media_state` (`stableMediaId` TEXT NOT NULL, `selectedExternalId` TEXT, `delayMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`stableMediaId`))")
    }
}

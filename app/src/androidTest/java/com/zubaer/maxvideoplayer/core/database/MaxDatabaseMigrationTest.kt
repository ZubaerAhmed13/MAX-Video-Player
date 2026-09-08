package com.zubaer.maxvideoplayer.core.database

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.feature.decoder.persistence.DecoderMediaStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaxDatabaseMigrationTest {
    @Test
    fun migration1To5PreservesStep1HistoryAndLongValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-1-5-test.db"
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
            .addMigrations(MaxDatabase.MIGRATION_1_2, MaxDatabase.MIGRATION_2_3, MaxDatabase.MIGRATION_3_4, MaxDatabase.MIGRATION_4_5, MaxDatabase.MIGRATION_5_6, MaxDatabase.MIGRATION_6_7)
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
    fun migration2To5PreservesStep2LibraryAndAllowsLaterState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-2-5-test.db"
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
            .addMigrations(MaxDatabase.MIGRATION_2_3, MaxDatabase.MIGRATION_3_4, MaxDatabase.MIGRATION_4_5, MaxDatabase.MIGRATION_5_6, MaxDatabase.MIGRATION_6_7)
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
            migrated.decoderMediaStateDao().upsert(DecoderMediaStateEntity("media-A", "SOFTWARE", 500L))
            assertEquals(1, migrated.subtitleDao().associationsForMediaBlockingForMigrationTest("media-A").size)
            assertEquals(750L, migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-A")?.delayMs)
            assertNotNull(migrated.subtitleDao().association("sub-1"))
            assertEquals("audio-1", migrated.audioDao().mediaStateBlockingForMigrationTest("media-A")?.selectedExternalId)
            assertEquals(250L, migrated.audioDao().mediaStateBlockingForMigrationTest("media-A")?.delayMs)
            assertEquals("SOFTWARE", migrated.decoderMediaStateDao().get("media-A")?.requestedMode)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration3To5PreservesAllStep1To4RowsAndCreatesAudioAndDecoderTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-3-5-test.db"
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
            .addMigrations(MaxDatabase.MIGRATION_3_4, MaxDatabase.MIGRATION_4_5, MaxDatabase.MIGRATION_5_6, MaxDatabase.MIGRATION_6_7)
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
            assertNull(migrated.decoderMediaStateDao().get("media-B"))

            migrated.audioDao().upsertAssociation(
                AudioAssociationEntity("audio-B", "media-B", "content://audio/B.flac", "Movie.B.en.flac", "en", "audio/flac", 600L, true, "AVAILABLE"),
            )
            migrated.audioDao().upsertMediaState(
                AudioMediaStateEntity("media-B", "audio-B", "MANUAL", "en", "Movie.B.en.flac", "audio/flac", 2, 180L, 601L),
            )
            migrated.decoderMediaStateDao().upsert(DecoderMediaStateEntity("media-B", "ENHANCED_HARDWARE", 602L))
            assertEquals("audio-B", migrated.audioDao().mediaStateBlockingForMigrationTest("media-B")?.selectedExternalId)
            assertEquals(180L, migrated.audioDao().mediaStateBlockingForMigrationTest("media-B")?.delayMs)
            assertEquals("ENHANCED_HARDWARE", migrated.decoderMediaStateDao().get("media-B")?.requestedMode)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration4To5PreservesStep1To5DataAndAddsDecoderOverrideOnly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-4-5-test.db"
        context.deleteDatabase(name)

        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            createV4Tables(db)
            db.execSQL("INSERT INTO media_history VALUES('media-C','content://video/C','Movie C','video/mp4',7200000000,3840,2160,90000,300000,700,0)")
            db.execSQL("INSERT INTO playback_preferences VALUES('speed','1.75')")
            db.execSQL("INSERT INTO favourites VALUES('media-C',701)")
            db.execSQL("INSERT INTO playlists(id,name,createdAtMs,updatedAtMs) VALUES(11,'Step5',701,702)")
            db.execSQL("INSERT INTO playlist_items VALUES(11,'media-C',0,703)")
            db.execSQL("INSERT INTO library_sources VALUES('tree-C','content://provider/tree/c','Library C','SAF','AVAILABLE',1,704,NULL)")
            db.execSQL("INSERT INTO media_index VALUES('media-C','tree-C','content://video/C','Movie C','Movie.C.mkv','video/x-matroska',300000,7200000000,3840,2160,10,20,'Movies/','folder-C','Movies','SAF','AVAILABLE')")
            db.execSQL("INSERT INTO library_preferences VALUES('view_mode','grid')")
            db.execSQL("INSERT INTO subtitle_associations VALUES('sub-C','media-C','content://subtitle/C.srt','Movie.C.srt','en','application/x-subrip','SRT','UTF_8',705,1,'AVAILABLE',250)")
            db.execSQL("INSERT INTO subtitle_media_state VALUES('media-C','sub-C',250,706)")
            db.execSQL("INSERT INTO audio_associations VALUES('audio-C','media-C','content://audio/C.flac','Movie.C.flac','en','audio/flac',707,1,'AVAILABLE')")
            db.execSQL("INSERT INTO audio_media_state VALUES('media-C','audio-C','MANUAL','en','Movie.C.flac','audio/flac',2,300,708)")
            db.version = 4
        }

        val migrated = Room.databaseBuilder(context, MaxDatabase::class.java, name)
            .addMigrations(MaxDatabase.MIGRATION_4_5, MaxDatabase.MIGRATION_5_6, MaxDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            migrated.openHelper.writableDatabase
            assertEquals(7_200_000_000L, migrated.mediaHistoryDao().getBlockingForMigrationTest("media-C")?.sizeBytes)
            assertTrue("media-C" in migrated.favouriteDao().ids())
            assertEquals("Step5", migrated.playlistDao().getPlaylist(11)?.name)
            assertEquals("media-C", migrated.playlistDao().items(11).single().stableMediaId)
            assertEquals("Movie C", migrated.mediaIndexDao().get("media-C")?.title)
            assertEquals("grid", migrated.libraryPreferenceDao().get("view_mode")?.value)
            assertEquals("1.75", migrated.playbackPreferenceDao().get("speed")?.value)
            assertEquals("sub-C", migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-C")?.selectedExternalId)
            assertEquals(250L, migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-C")?.delayMs)
            assertEquals("audio-C", migrated.audioDao().mediaStateBlockingForMigrationTest("media-C")?.selectedExternalId)
            assertEquals(300L, migrated.audioDao().mediaStateBlockingForMigrationTest("media-C")?.delayMs)
            assertNull(migrated.decoderMediaStateDao().get("media-C"))

            migrated.decoderMediaStateDao().upsert(DecoderMediaStateEntity("media-C", "HARDWARE", 709L))
            assertEquals("HARDWARE", migrated.decoderMediaStateDao().get("media-C")?.requestedMode)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration5To6PreservesAllPriorDataAndAddsSecretFreeNetworkLocations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-5-6-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            createV5Tables(db)
            db.execSQL("INSERT INTO media_history VALUES('media-D','content://video/D','Movie D','video/mp4',8200000000,3840,2160,120000,360000,900,0)")
            db.execSQL("INSERT INTO playback_preferences VALUES('speed','1.85')")
            db.execSQL("INSERT INTO favourites VALUES('media-D',901)")
            db.execSQL("INSERT INTO playlists(id,name,createdAtMs,updatedAtMs) VALUES(13,'Step6',901,902)")
            db.execSQL("INSERT INTO playlist_items VALUES(13,'media-D',0,903)")
            db.execSQL("INSERT INTO library_sources VALUES('tree-D','content://provider/tree/d','Library D','SAF','AVAILABLE',1,904,NULL)")
            db.execSQL("INSERT INTO media_index VALUES('media-D','tree-D','content://video/D','Movie D','Movie.D.mkv','video/x-matroska',360000,8200000000,3840,2160,10,20,'Movies/','folder-D','Movies','SAF','AVAILABLE')")
            db.execSQL("INSERT INTO library_preferences VALUES('view_mode','list')")
            db.execSQL("INSERT INTO subtitle_associations VALUES('sub-D','media-D','content://subtitle/D.srt','Movie.D.srt','en','application/x-subrip','SRT','UTF_8',905,1,'AVAILABLE',400)")
            db.execSQL("INSERT INTO subtitle_media_state VALUES('media-D','sub-D',400,906)")
            db.execSQL("INSERT INTO audio_associations VALUES('audio-D','media-D','content://audio/D.flac','Movie.D.flac','en','audio/flac',907,1,'AVAILABLE')")
            db.execSQL("INSERT INTO audio_media_state VALUES('media-D','audio-D','MANUAL','en','Movie.D.flac','audio/flac',2,350,908)")
            db.execSQL("INSERT INTO decoder_media_state VALUES('media-D','SOFTWARE',901)")
            db.version = 5
        }
        val migrated = Room.databaseBuilder(context, MaxDatabase::class.java, name)
            .addMigrations(MaxDatabase.MIGRATION_5_6, MaxDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            migrated.openHelper.writableDatabase
            assertEquals(8_200_000_000L, migrated.mediaHistoryDao().getBlockingForMigrationTest("media-D")?.sizeBytes)
            assertEquals("1.85", migrated.playbackPreferenceDao().get("speed")?.value)
            assertTrue("media-D" in migrated.favouriteDao().ids())
            assertEquals("Step6", migrated.playlistDao().getPlaylist(13)?.name)
            assertEquals("media-D", migrated.playlistDao().items(13).single().stableMediaId)
            assertEquals("Movie D", migrated.mediaIndexDao().get("media-D")?.title)
            assertEquals("list", migrated.libraryPreferenceDao().get("view_mode")?.value)
            assertEquals("sub-D", migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-D")?.selectedExternalId)
            assertEquals(400L, migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-D")?.delayMs)
            assertEquals("audio-D", migrated.audioDao().mediaStateBlockingForMigrationTest("media-D")?.selectedExternalId)
            assertEquals(350L, migrated.audioDao().mediaStateBlockingForMigrationTest("media-D")?.delayMs)
            assertEquals("SOFTWARE", migrated.decoderMediaStateDao().get("media-D")?.requestedMode)
            migrated.networkLocationDao().upsert(
                NetworkLocationEntity(
                    id = "nas-1", displayName = "Home NAS", protocol = "SMB", host = "nas.local", port = 445,
                    basePath = "Movies", credentialRef = "vault-ref-only", usernameHint = "zubaer", useGuest = false,
                    ftpPassiveMode = true, ftpSecurityAcknowledged = false, createdAtMs = 1L, updatedAtMs = 1L, lastConnectedAtMs = null,
                ),
            )
            val row = migrated.networkLocationDao().get("nas-1")
            assertEquals("vault-ref-only", row?.credentialRef)
            assertEquals("zubaer", row?.usernameHint)
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

    private fun createV4Tables(db: android.database.sqlite.SQLiteDatabase) {
        createV3Tables(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS `audio_associations` (`id` TEXT NOT NULL, `stableMediaId` TEXT NOT NULL, `audioUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `language` TEXT, `mimeType` TEXT, `addedAtMs` INTEGER NOT NULL, `isPreferred` INTEGER NOT NULL, `availability` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_associations_stableMediaId` ON `audio_associations` (`stableMediaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_associations_stableMediaId_isPreferred` ON `audio_associations` (`stableMediaId`, `isPreferred`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `audio_media_state` (`stableMediaId` TEXT NOT NULL, `selectedExternalId` TEXT, `selectionMode` TEXT NOT NULL, `selectedLanguage` TEXT, `selectedLabel` TEXT, `selectedMimeType` TEXT, `selectedChannelCount` INTEGER, `delayMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`stableMediaId`))")
    }

    private fun createV5Tables(db: android.database.sqlite.SQLiteDatabase) {
        createV4Tables(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS `decoder_media_state` (`stableMediaId` TEXT NOT NULL, `requestedMode` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`stableMediaId`))")
    }
}

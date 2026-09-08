package com.zubaer.maxvideoplayer.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zubaer.maxvideoplayer.feature.cloud.persistence.CloudAccountEntity
import com.zubaer.maxvideoplayer.feature.decoder.persistence.DecoderMediaStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Step8DatabaseMigrationTest {
    @Test
    fun migration6To7PreservesStep1To7RowsAndAddsSecretFreeCloudAccounts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-6-7-step8.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            createV6Tables(db)
            db.execSQL("INSERT INTO media_history VALUES('media-step8','content://video/step8','Step 8 Movie','video/mp4',9200000000,3840,2160,125000,420000,1000,0)")
            db.execSQL("INSERT INTO playback_preferences VALUES('speed','1.90')")
            db.execSQL("INSERT INTO favourites VALUES('media-step8',1001)")
            db.execSQL("INSERT INTO playlists(id,name,createdAtMs,updatedAtMs) VALUES(17,'Step8',1001,1002)")
            db.execSQL("INSERT INTO playlist_items VALUES(17,'media-step8',0,1003)")
            db.execSQL("INSERT INTO library_sources VALUES('tree-step8','content://provider/tree/usb','USB Movies','SAF','AVAILABLE',1,1004,NULL)")
            db.execSQL("INSERT INTO media_index VALUES('media-step8','tree-step8','content://video/step8','Step 8 Movie','Step8.mkv','video/x-matroska',420000,9200000000,3840,2160,10,20,'Movies/','folder-step8','Movies','SAF','AVAILABLE')")
            db.execSQL("INSERT INTO library_preferences VALUES('view_mode','grid')")
            db.execSQL("INSERT INTO subtitle_associations VALUES('sub-step8','media-step8','content://subtitle/step8.srt','Step8.srt','en','application/x-subrip','SRT','UTF_8',1005,1,'AVAILABLE',150)")
            db.execSQL("INSERT INTO subtitle_media_state VALUES('media-step8','sub-step8',150,1006)")
            db.execSQL("INSERT INTO audio_associations VALUES('audio-step8','media-step8','content://audio/step8.flac','Step8.flac','en','audio/flac',1007,1,'AVAILABLE')")
            db.execSQL("INSERT INTO audio_media_state VALUES('media-step8','audio-step8','MANUAL','en','Step8.flac','audio/flac',2,200,1008)")
            db.execSQL("INSERT INTO decoder_media_state VALUES('media-step8','SOFTWARE',1009)")
            db.execSQL("INSERT INTO network_locations VALUES('nas-step8','Home NAS','SMB','nas.local',445,'Movies','vault-ref-step8','zubaer',0,1,0,1010,1011,NULL)")
            db.version = 6
        }

        val migrated = Room.databaseBuilder(context, MaxDatabase::class.java, name)
            .addMigrations(MaxDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            migrated.openHelper.writableDatabase
            assertEquals(9_200_000_000L, migrated.mediaHistoryDao().getBlockingForMigrationTest("media-step8")?.sizeBytes)
            assertEquals("1.90", migrated.playbackPreferenceDao().get("speed")?.value)
            assertTrue("media-step8" in migrated.favouriteDao().ids())
            assertEquals("Step8", migrated.playlistDao().getPlaylist(17)?.name)
            assertEquals("media-step8", migrated.playlistDao().items(17).single().stableMediaId)
            assertEquals("Step 8 Movie", migrated.mediaIndexDao().get("media-step8")?.title)
            assertEquals("sub-step8", migrated.subtitleDao().mediaStateBlockingForMigrationTest("media-step8")?.selectedExternalId)
            assertEquals("audio-step8", migrated.audioDao().mediaStateBlockingForMigrationTest("media-step8")?.selectedExternalId)
            assertEquals("SOFTWARE", migrated.decoderMediaStateDao().get("media-step8")?.requestedMode)
            assertEquals("vault-ref-step8", migrated.networkLocationDao().get("nas-step8")?.credentialRef)

            migrated.cloudAccountDao().upsert(
                CloudAccountEntity(
                    id = "google:user-1",
                    provider = "GOOGLE_DRIVE",
                    providerAccountId = "user-1",
                    displayName = "Google Drive",
                    emailHint = "u***@example.com",
                    authReference = "keystore-token-cache-ref",
                    createdAtMs = 2000L,
                    lastUsedAtMs = 2001L,
                ),
            )
            val cloud = migrated.cloudAccountDao().get("google:user-1")
            assertNotNull(cloud)
            assertEquals("keystore-token-cache-ref", cloud?.authReference)

            migrated.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cloud_accounts'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private fun createV6Tables(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS media_history (stableMediaId TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, title TEXT NOT NULL, mimeType TEXT, sizeBytes INTEGER, width INTEGER, height INTEGER, lastPositionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, lastPlayedAtMs INTEGER NOT NULL, completed INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS playback_preferences (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS favourites (stableMediaId TEXT NOT NULL PRIMARY KEY, addedAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlists_name ON playlists(name)")
        db.execSQL("CREATE TABLE IF NOT EXISTS playlist_items (playlistId INTEGER NOT NULL, stableMediaId TEXT NOT NULL, orderIndex INTEGER NOT NULL, addedAtMs INTEGER NOT NULL, PRIMARY KEY(playlistId,stableMediaId), FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_items_playlistId ON playlist_items(playlistId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_items_playlistId_orderIndex ON playlist_items(playlistId,orderIndex)")
        db.execSQL("CREATE TABLE IF NOT EXISTS library_sources (id TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, displayName TEXT NOT NULL, sourceType TEXT NOT NULL, status TEXT NOT NULL, permissionPersisted INTEGER NOT NULL, addedAtMs INTEGER NOT NULL, lastScanAtMs INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS excluded_folders (folderKey TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, excludedAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS media_index (stableMediaId TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, fileName TEXT, mimeType TEXT, durationMs INTEGER, sizeBytes INTEGER, width INTEGER, height INTEGER, dateAddedMs INTEGER, dateModifiedMs INTEGER, relativePath TEXT, folderKey TEXT, folderName TEXT, sourceType TEXT NOT NULL, availability TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_index_sourceId ON media_index(sourceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_index_folderKey ON media_index(folderKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_index_title ON media_index(title)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_index_dateModifiedMs ON media_index(dateModifiedMs)")
        db.execSQL("CREATE TABLE IF NOT EXISTS library_preferences (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS subtitle_associations (id TEXT NOT NULL PRIMARY KEY, stableMediaId TEXT NOT NULL, subtitleUri TEXT NOT NULL, displayName TEXT NOT NULL, language TEXT, mimeType TEXT NOT NULL, format TEXT NOT NULL, encoding TEXT NOT NULL, addedAtMs INTEGER NOT NULL, isPreferred INTEGER NOT NULL, availability TEXT NOT NULL, delayMs INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_subtitle_associations_stableMediaId ON subtitle_associations(stableMediaId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_subtitle_associations_stableMediaId_isPreferred ON subtitle_associations(stableMediaId,isPreferred)")
        db.execSQL("CREATE TABLE IF NOT EXISTS subtitle_media_state (stableMediaId TEXT NOT NULL PRIMARY KEY, selectedExternalId TEXT, delayMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS audio_associations (id TEXT NOT NULL PRIMARY KEY, stableMediaId TEXT NOT NULL, audioUri TEXT NOT NULL, displayName TEXT NOT NULL, language TEXT, mimeType TEXT, addedAtMs INTEGER NOT NULL, isPreferred INTEGER NOT NULL, availability TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_associations_stableMediaId ON audio_associations(stableMediaId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_associations_stableMediaId_isPreferred ON audio_associations(stableMediaId,isPreferred)")
        db.execSQL("CREATE TABLE IF NOT EXISTS audio_media_state (stableMediaId TEXT NOT NULL PRIMARY KEY, selectedExternalId TEXT, selectionMode TEXT NOT NULL, selectedLanguage TEXT, selectedLabel TEXT, selectedMimeType TEXT, selectedChannelCount INTEGER, delayMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS decoder_media_state (stableMediaId TEXT NOT NULL PRIMARY KEY, requestedMode TEXT NOT NULL, updatedAtMs INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS network_locations (id TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, protocol TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL, basePath TEXT NOT NULL, credentialRef TEXT, usernameHint TEXT, useGuest INTEGER NOT NULL, ftpPassiveMode INTEGER NOT NULL, ftpSecurityAcknowledged INTEGER NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, lastConnectedAtMs INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_network_locations_credentialRef ON network_locations(credentialRef)")
    }
}

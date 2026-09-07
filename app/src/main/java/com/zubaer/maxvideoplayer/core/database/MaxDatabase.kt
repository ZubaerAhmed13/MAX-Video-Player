package com.zubaer.maxvideoplayer.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MediaHistoryEntity::class,
        PlaybackPreferenceEntity::class,
        FavouriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        LibrarySourceEntity::class,
        ExcludedFolderEntity::class,
        MediaIndexEntity::class,
        LibraryPreferenceEntity::class,
        SubtitleAssociationEntity::class,
        SubtitleMediaStateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MaxDatabase : RoomDatabase() {
    abstract fun mediaHistoryDao(): MediaHistoryDao
    abstract fun playbackPreferenceDao(): PlaybackPreferenceDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun librarySourceDao(): LibrarySourceDao
    abstract fun excludedFolderDao(): ExcludedFolderDao
    abstract fun mediaIndexDao(): MediaIndexDao
    abstract fun libraryPreferenceDao(): LibraryPreferenceDao
    abstract fun subtitleDao(): SubtitleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `subtitle_associations` (`id` TEXT NOT NULL, `stableMediaId` TEXT NOT NULL, `subtitleUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `language` TEXT, `mimeType` TEXT NOT NULL, `format` TEXT NOT NULL, `encoding` TEXT NOT NULL, `addedAtMs` INTEGER NOT NULL, `isPreferred` INTEGER NOT NULL, `availability` TEXT NOT NULL, `delayMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_associations_stableMediaId` ON `subtitle_associations` (`stableMediaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_associations_stableMediaId_isPreferred` ON `subtitle_associations` (`stableMediaId`, `isPreferred`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `subtitle_media_state` (`stableMediaId` TEXT NOT NULL, `selectedExternalId` TEXT, `delayMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`stableMediaId`))")
            }
        }

        fun create(context: Context): MaxDatabase =
            Room.databaseBuilder(context, MaxDatabase::class.java, "max-video-player.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}

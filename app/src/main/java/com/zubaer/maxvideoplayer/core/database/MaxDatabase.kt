package com.zubaer.maxvideoplayer.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MediaHistoryEntity::class, PlaybackPreferenceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MaxDatabase : RoomDatabase() {
    abstract fun mediaHistoryDao(): MediaHistoryDao
    abstract fun playbackPreferenceDao(): PlaybackPreferenceDao

    companion object {
        fun create(context: Context): MaxDatabase =
            Room.databaseBuilder(context, MaxDatabase::class.java, "max-video-player.db")
                // Never use destructive migration fallback in production architecture.
                .build()
    }
}

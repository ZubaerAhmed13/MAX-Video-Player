package com.zubaer.maxvideoplayer

import android.content.Context
import com.zubaer.maxvideoplayer.core.database.MaxDatabase
import com.zubaer.maxvideoplayer.core.database.PlaybackHistoryRepository
import com.zubaer.maxvideoplayer.core.device.DeviceCapabilityProvider
import com.zubaer.maxvideoplayer.core.media.MediaMetadataExtractor
import com.zubaer.maxvideoplayer.core.media.MediaStoreRepository
import com.zubaer.maxvideoplayer.core.media.SafTreeScanner
import com.zubaer.maxvideoplayer.core.media.UriAvailabilityChecker
import com.zubaer.maxvideoplayer.feature.library.LibraryRepository
import com.zubaer.maxvideoplayer.feature.library.MediaFileActionRepository
import com.zubaer.maxvideoplayer.feature.library.ThumbnailRepository
import com.zubaer.maxvideoplayer.feature.player.PlayerPreferences
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: MaxDatabase by lazy { MaxDatabase.create(appContext) }
    val historyRepository: PlaybackHistoryRepository by lazy { PlaybackHistoryRepository(database.mediaHistoryDao()) }
    val mediaStoreRepository: MediaStoreRepository by lazy { MediaStoreRepository(appContext) }
    val metadataExtractor: MediaMetadataExtractor by lazy { MediaMetadataExtractor(appContext) }
    val uriAvailabilityChecker: UriAvailabilityChecker by lazy { UriAvailabilityChecker(appContext.contentResolver) }
    val deviceCapabilityProvider: DeviceCapabilityProvider by lazy { DeviceCapabilityProvider(appContext) }
    val subtitleRepository: SubtitleRepository by lazy { SubtitleRepository(appContext) }
    val playbackConnection: PlaybackConnection by lazy { PlaybackConnection(appContext, subtitleRepository) }
    val safTreeScanner: SafTreeScanner by lazy { SafTreeScanner(appContext.contentResolver) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database, mediaStoreRepository, safTreeScanner, historyRepository) }
    val mediaFileActionRepository: MediaFileActionRepository by lazy { MediaFileActionRepository(appContext.contentResolver) }
    val thumbnailRepository: ThumbnailRepository by lazy { ThumbnailRepository(appContext) }
    val playerPreferences: PlayerPreferences by lazy { PlayerPreferences(appContext) }
}

package com.zubaer.maxvideoplayer

import android.content.Context
import com.zubaer.maxvideoplayer.core.database.MaxDatabase
import com.zubaer.maxvideoplayer.core.database.PlaybackHistoryRepository
import com.zubaer.maxvideoplayer.core.device.DeviceCapabilityProvider
import com.zubaer.maxvideoplayer.core.media.MediaMetadataExtractor
import com.zubaer.maxvideoplayer.core.media.MediaStoreRepository
import com.zubaer.maxvideoplayer.core.media.UriAvailabilityChecker
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: MaxDatabase by lazy { MaxDatabase.create(appContext) }
    val historyRepository: PlaybackHistoryRepository by lazy { PlaybackHistoryRepository(database.mediaHistoryDao()) }
    val mediaStoreRepository: MediaStoreRepository by lazy { MediaStoreRepository(appContext) }
    val metadataExtractor: MediaMetadataExtractor by lazy { MediaMetadataExtractor(appContext) }
    val uriAvailabilityChecker: UriAvailabilityChecker by lazy { UriAvailabilityChecker(appContext.contentResolver) }
    val deviceCapabilityProvider: DeviceCapabilityProvider by lazy { DeviceCapabilityProvider(appContext) }
    val playbackConnection: PlaybackConnection by lazy { PlaybackConnection(appContext) }
}

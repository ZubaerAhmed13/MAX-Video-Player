package com.zubaer.maxvideoplayer

import android.content.Context
import com.zubaer.maxvideoplayer.core.database.MaxDatabase
import com.zubaer.maxvideoplayer.core.database.PlaybackHistoryRepository
import com.zubaer.maxvideoplayer.core.device.DeviceCapabilityProvider
import com.zubaer.maxvideoplayer.core.media.MediaMetadataExtractor
import com.zubaer.maxvideoplayer.core.media.MediaStoreRepository
import com.zubaer.maxvideoplayer.core.media.SafTreeScanner
import com.zubaer.maxvideoplayer.core.media.UriAvailabilityChecker
import com.zubaer.maxvideoplayer.feature.audio.AudioPlaybackController
import com.zubaer.maxvideoplayer.feature.audio.AudioRepository
import com.zubaer.maxvideoplayer.feature.cloud.auth.CloudOAuthCoordinator
import com.zubaer.maxvideoplayer.feature.cloud.auth.CloudTokenVault
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudPlaybackRegistry
import com.zubaer.maxvideoplayer.feature.decoder.runtime.DecoderRepository
import com.zubaer.maxvideoplayer.feature.library.LibraryRepository
import com.zubaer.maxvideoplayer.feature.library.MediaFileActionRepository
import com.zubaer.maxvideoplayer.feature.library.ThumbnailRepository
import com.zubaer.maxvideoplayer.feature.network.diagnostics.NetworkDiagnosticsMonitor
import com.zubaer.maxvideoplayer.feature.network.playback.NetworkRequestRegistry
import com.zubaer.maxvideoplayer.feature.network.protocol.http.NetworkHttpClientFactory
import com.zubaer.maxvideoplayer.feature.network.repository.NetworkLocationRepository
import com.zubaer.maxvideoplayer.feature.network.repository.NetworkRepository
import com.zubaer.maxvideoplayer.feature.network.security.CredentialVault
import com.zubaer.maxvideoplayer.feature.player.PlayerPreferences
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
import com.zubaer.maxvideoplayer.feature.usb.RemovableStorageController
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: MaxDatabase by lazy { MaxDatabase.create(appContext) }
    val credentialVault: CredentialVault by lazy { CredentialVault(appContext) }
    val cloudTokenVault: CloudTokenVault by lazy { CloudTokenVault(appContext) }
    val cloudPlaybackRegistry: CloudPlaybackRegistry by lazy { CloudPlaybackRegistry() }
    val cloudOAuthCoordinator: CloudOAuthCoordinator by lazy {
        CloudOAuthCoordinator(database.cloudAccountDao(), cloudTokenVault, cloudPlaybackRegistry)
    }
    val networkRequestRegistry: NetworkRequestRegistry by lazy { NetworkRequestRegistry() }
    val networkDiagnosticsMonitor: NetworkDiagnosticsMonitor by lazy { NetworkDiagnosticsMonitor(appContext) }
    val networkLocationRepository: NetworkLocationRepository by lazy {
        NetworkLocationRepository(database.networkLocationDao(), credentialVault)
    }
    val networkRepository: NetworkRepository by lazy {
        NetworkRepository(
            networkLocationRepository,
            networkRequestRegistry,
            NetworkHttpClientFactory.create(networkRequestRegistry),
        )
    }
    val historyRepository: PlaybackHistoryRepository by lazy { PlaybackHistoryRepository(database.mediaHistoryDao()) }
    val mediaStoreRepository: MediaStoreRepository by lazy { MediaStoreRepository(appContext) }
    val metadataExtractor: MediaMetadataExtractor by lazy { MediaMetadataExtractor(appContext) }
    val uriAvailabilityChecker: UriAvailabilityChecker by lazy { UriAvailabilityChecker(appContext.contentResolver) }
    val deviceCapabilityProvider: DeviceCapabilityProvider by lazy { DeviceCapabilityProvider(appContext) }
    val subtitleRepository: SubtitleRepository by lazy { SubtitleRepository(appContext, database) }
    val audioRepository: AudioRepository by lazy { AudioRepository(appContext, database) }
    val playerPreferences: PlayerPreferences by lazy { PlayerPreferences(appContext) }
    val decoderRepository: DecoderRepository by lazy {
        DecoderRepository(database.decoderMediaStateDao(), playerPreferences)
    }
    val playbackConnection: PlaybackConnection by lazy {
        PlaybackConnection(appContext, subtitleRepository, networkDiagnosticsMonitor)
    }
    val audioPlaybackController: AudioPlaybackController by lazy { AudioPlaybackController(audioRepository, playbackConnection) }
    val safTreeScanner: SafTreeScanner by lazy { SafTreeScanner(appContext.contentResolver) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database, mediaStoreRepository, safTreeScanner, historyRepository) }
    val mediaFileActionRepository: MediaFileActionRepository by lazy { MediaFileActionRepository(appContext.contentResolver) }
    val thumbnailRepository: ThumbnailRepository by lazy { ThumbnailRepository(appContext) }
    val removableStorageController: RemovableStorageController by lazy { RemovableStorageController(appContext) }
}

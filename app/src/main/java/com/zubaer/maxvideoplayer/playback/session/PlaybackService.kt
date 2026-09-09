package com.zubaer.maxvideoplayer.playback.session

import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.feature.audio.AudioRouteMonitor
import com.zubaer.maxvideoplayer.feature.cast.CastRelayManager
import com.zubaer.maxvideoplayer.feature.cast.CastTransferContinuityMonitor
import com.zubaer.maxvideoplayer.feature.cast.CastTransferEndpoint
import com.zubaer.maxvideoplayer.feature.cast.CastTransferStatePolicy
import com.zubaer.maxvideoplayer.feature.cast.SecureCastMediaItemConverter
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
import com.zubaer.maxvideoplayer.playback.engine.Media3PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private lateinit var engine: Media3PlaybackEngine
    private lateinit var castPlayer: CastPlayer
    private lateinit var castRelayManager: CastRelayManager
    private lateinit var castMediaItemConverter: SecureCastMediaItemConverter
    private lateinit var mediaSession: MediaSession
    private lateinit var routeMonitor: AudioRouteMonitor
    private val transferContinuityMonitor = CastTransferContinuityMonitor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var persistenceJob: Job? = null
    private var decoderActivatedMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            observeTransferContinuity(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val player = authoritativePlayerOrNull() ?: return
            (application as MaxVideoPlayerApplication).container.networkDiagnosticsMonitor.onPlayerState(player)
            if (isPlaying) startPersistenceTicker() else {
                persistenceJob?.cancel()
                persistenceJob = null
                persistCurrent()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = authoritativePlayerOrNull() ?: return
            (application as MaxVideoPlayerApplication).container.networkDiagnosticsMonitor.onPlayerState(player)
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) persistCurrent()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val container = (application as MaxVideoPlayerApplication).container
            val mediaId = mediaItem?.mediaId
            container.networkDiagnosticsMonitor.activate(mediaItem?.localConfiguration?.uri?.toString())
            container.audioRepository.activateMedia(mediaId)
            if (decoderActivatedMediaId != mediaId) {
                decoderActivatedMediaId = mediaId
                container.decoderRepository.activateMedia(mediaId)
            }
            if (mediaItem != null) container.audioRepository.refreshExternalAvailability(mediaItem.mediaId)
            persistCurrent()
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            val player = authoritativePlayerOrNull()
            if (player != null) observeTransferContinuity(player)
            if (deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                if (::castRelayManager.isInitialized) castRelayManager.stopSession()
                if (::castMediaItemConverter.isInitialized) castMediaItemConverter.clearOriginalMappings()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            (application as MaxVideoPlayerApplication).container.networkDiagnosticsMonitor.onFailure(error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = (application as MaxVideoPlayerApplication).container
        engine = Media3PlaybackEngine(
            this,
            container.subtitleRepository,
            container.audioRepository,
            container.decoderRepository,
            container.networkRequestRegistry,
            container.cloudPlaybackRegistry,
        )
        castRelayManager = CastRelayManager(
            this,
            container.networkRequestRegistry,
            container.cloudPlaybackRegistry,
        )
        castMediaItemConverter = SecureCastMediaItemConverter(
            castRelayManager,
            container.networkRequestRegistry,
        )
        val remoteCastPlayer = RemoteCastPlayer.Builder(this)
            .setMediaItemConverter(castMediaItemConverter)
            .build()
        castPlayer = CastPlayer.Builder(this)
            .setLocalPlayer(engine.player)
            .setRemotePlayer(remoteCastPlayer)
            .build()
        castPlayer.addListener(listener)
        observeTransferContinuity(castPlayer)
        container.audioRepository.activateMedia(castPlayer.currentMediaItem?.mediaId)
        container.decoderRepository.activateMedia(castPlayer.currentMediaItem?.mediaId)
        serviceScope.launch {
            container.decoderRepository.modeRequests.collect { mode ->
                if (::castPlayer.isInitialized && castPlayer.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                    engine.reconfigureVideoDecoder(mode)
                }
            }
        }
        routeMonitor = AudioRouteMonitor(this, container.audioRepository::setRoute).also { it.start() }
        mediaSession = MediaSession.Builder(this, castPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        persistCurrent()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistCurrent()
        persistenceJob?.cancel()
        if (::routeMonitor.isInitialized) routeMonitor.stop()
        if (::castPlayer.isInitialized) castPlayer.removeListener(listener)
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::castPlayer.isInitialized) castPlayer.release()
        if (::castRelayManager.isInitialized) castRelayManager.close()
        if (::engine.isInitialized) engine.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPersistenceTicker() {
        if (persistenceJob?.isActive == true) return
        persistenceJob = serviceScope.launch {
            while (isActive) {
                delay(5_000L)
                persistCurrent()
            }
        }
    }

    private fun observeTransferContinuity(player: Player) {
        if (!player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) return
        val endpoint = if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            CastTransferEndpoint.REMOTE
        } else {
            CastTransferEndpoint.LOCAL
        }
        transferContinuityMonitor.observe(endpoint, CastTransferStatePolicy.snapshot(player))
    }

    private fun authoritativePlayerOrNull(): Player? = when {
        ::castPlayer.isInitialized -> castPlayer
        ::engine.isInitialized -> engine.player
        else -> null
    }

    private fun persistCurrent() {
        val player = authoritativePlayerOrNull() ?: return
        val item = player.currentMediaItem ?: return
        val rawUri = item.localConfiguration?.uri?.toString() ?: return
        val uri = if (rawUri.substringBefore(':').lowercase() in setOf("http", "https", "rtsp", "ftp", "ftps", "maxsmb")) {
            NetworkUriPolicy.persistenceSafeUri(rawUri)
        } else rawUri
        val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { uri }
        val mime = item.localConfiguration?.mimeType
        val repository = (application as MaxVideoPlayerApplication).container.historyRepository
        serviceScope.launch(Dispatchers.IO) {
            repository.recordSnapshot(mediaId, uri, title, position, duration, mime)
        }
    }
}

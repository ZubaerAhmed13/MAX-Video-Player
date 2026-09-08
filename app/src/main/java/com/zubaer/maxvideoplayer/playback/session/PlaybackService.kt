package com.zubaer.maxvideoplayer.playback.session

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.feature.audio.AudioRouteMonitor
import com.zubaer.maxvideoplayer.playback.engine.Media3PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private lateinit var engine: Media3PlaybackEngine
    private lateinit var mediaSession: MediaSession
    private lateinit var routeMonitor: AudioRouteMonitor
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var persistenceJob: Job? = null
    private var decoderActivatedMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            (application as MaxVideoPlayerApplication).container.networkDiagnosticsMonitor.onPlayerState(engine.player)
            if (isPlaying) startPersistenceTicker() else {
                persistenceJob?.cancel()
                persistenceJob = null
                persistCurrent()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            (application as MaxVideoPlayerApplication).container.networkDiagnosticsMonitor.onPlayerState(engine.player)
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) persistCurrent()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val container = (application as MaxVideoPlayerApplication).container
            val mediaId = mediaItem?.mediaId
            container.networkDiagnosticsMonitor.activate(mediaItem?.localConfiguration?.uri?.toString())
            container.audioRepository.activateMedia(mediaId)
            // Decoder reconfiguration restores the same MediaItem through setMediaItems(), which
            // produces another transition callback. Re-activating decoder state for that identical
            // media ID would emit another mode request and create a reconfigure -> transition ->
            // reconfigure loop. Activate decoder persistence only when the authoritative media
            // identity actually changes; explicit user mode changes already emit their own request.
            if (decoderActivatedMediaId != mediaId) {
                decoderActivatedMediaId = mediaId
                container.decoderRepository.activateMedia(mediaId)
            }
            if (mediaItem != null) container.audioRepository.refreshExternalAvailability(mediaItem.mediaId)
            persistCurrent()
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
        )
        engine.player.addListener(listener)
        container.audioRepository.activateMedia(engine.player.currentMediaItem?.mediaId)
        container.decoderRepository.activateMedia(engine.player.currentMediaItem?.mediaId)
        serviceScope.launch {
            container.decoderRepository.modeRequests.collect { mode ->
                if (::engine.isInitialized) engine.reconfigureVideoDecoder(mode)
            }
        }
        routeMonitor = AudioRouteMonitor(this, container.audioRepository::setRoute).also { it.start() }
        mediaSession = MediaSession.Builder(this, engine.player).build()
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
        engine.player.removeListener(listener)
        mediaSession.release()
        engine.release()
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

    private fun persistCurrent() {
        if (!::engine.isInitialized) return
        val player = engine.player
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

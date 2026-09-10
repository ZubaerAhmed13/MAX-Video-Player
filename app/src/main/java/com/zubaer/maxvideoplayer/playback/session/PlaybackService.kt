package com.zubaer.maxvideoplayer.playback.session

import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultIdentity
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultState
import com.zubaer.maxvideoplayer.feature.sleeptimer.SleepTimerController
import com.zubaer.maxvideoplayer.playback.engine.Media3PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private lateinit var engine: Media3PlaybackEngine
    private lateinit var castPlayer: CastPlayer
    private lateinit var castRelayManager: CastRelayManager
    private lateinit var castMediaItemConverter: SecureCastMediaItemConverter
    private lateinit var mediaSession: MediaSession
    private lateinit var routeMonitor: AudioRouteMonitor
    private lateinit var sleepTimerController: SleepTimerController
    private val transferContinuityMonitor = CastTransferContinuityMonitor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var persistenceJob: Job? = null
    private var decoderActivatedMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            observeTransferContinuity(player)
        }

        override fun onTracksChanged(tracks: Tracks) {
            applySelectedExternalAudioTrack()
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
            if (mediaItem != null && !isPrivate(mediaItem)) container.audioRepository.refreshExternalAvailability(mediaItem.mediaId)
            persistCurrent()
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            val player = authoritativePlayerOrNull()
            if (player != null) observeTransferContinuity(player)
            if (deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                if (::castRelayManager.isInitialized) castRelayManager.stopSession()
                if (::castMediaItemConverter.isInitialized) castMediaItemConverter.clearOriginalMappings()
                applySelectedExternalAudioTrack()
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
            container.privateVaultResolver,
        )
        castRelayManager = CastRelayManager(this, container.networkRequestRegistry, container.cloudPlaybackRegistry)
        castMediaItemConverter = SecureCastMediaItemConverter(castRelayManager, container.networkRequestRegistry)
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
        // A decoder change requested while Audio-only is active is retained by DecoderRepository,
        // but Media3 correctly skips reconfiguration while its video track is disabled. When the
        // user restores video, wait until AudioPlaybackController has applied the track-selection
        // change, then force one local reprepare so renderer reuse cannot revive the old decoder.
        serviceScope.launch {
            var wasAudioOnly = container.audioRepository.state.value.audioOnlyMode
            container.audioRepository.state.collect { audioState ->
                val videoRestored = wasAudioOnly && !audioState.audioOnlyMode
                wasAudioOnly = audioState.audioOnlyMode
                if (!videoRestored) return@collect

                yield()
                if (
                    ::castPlayer.isInitialized &&
                    castPlayer.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE &&
                    C.TRACK_TYPE_VIDEO !in engine.player.trackSelectionParameters.disabledTrackTypes
                ) {
                    engine.reconfigureVideoDecoder(container.decoderRepository.requestedMode())
                }
            }
        }
        // Lock is authoritative even if UI remains composed: stop private playback and remove its
        // session metadata/queue immediately when the vault ceases to be unlocked.
        serviceScope.launch {
            container.privateVaultSession.state.collect { state ->
                if (state != PrivateVaultState.UNLOCKED) clearPrivatePlaybackIfActive()
            }
        }
        routeMonitor = AudioRouteMonitor(this, container.audioRepository::setRoute).also { it.start() }
        sleepTimerController = SleepTimerController(
            player = castPlayer,
            repository = container.sleepTimerRepository,
            scope = serviceScope,
            fadeDuration = { container.settingsRepository.state.value.sleepFadeDuration },
        )
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
        if (::sleepTimerController.isInitialized) sleepTimerController.release()
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

    /**
     * External audio is merged by the service-owned Media3PlaybackEngine, so the authoritative
     * TrackGroup identity exists here rather than in the MediaController proxy. Apply the explicit
     * external override directly to the local ExoPlayer as soon as the merged topology is exposed.
     * This keeps one player owner responsible for both source construction and TrackGroup selection.
     */
    private fun applySelectedExternalAudioTrack() {
        if (!::engine.isInitialized || !::castPlayer.isInitialized) return
        if (castPlayer.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return

        val player = engine.player
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val repository = (application as MaxVideoPlayerApplication).container.audioRepository
        repository.selectedExternalFor(mediaId) ?: return
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS) ||
            !player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)
        ) return

        player.currentTracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_AUDIO || !isExternalAudioGroup(group)) return@forEach
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                if (group.isTrackSelected(index)) return
                val current = player.trackSelectionParameters
                val desired = current.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
                if (desired != current) player.trackSelectionParameters = desired
                return
            }
        }
    }

    private fun isExternalAudioGroup(group: Tracks.Group): Boolean {
        if (group.mediaTrackGroup.id.startsWith("1:")) return true
        return (0 until group.length).any { index ->
            group.getTrackFormat(index).id?.startsWith("1:") == true
        }
    }

    private fun observeTransferContinuity(player: Player) {
        if (!player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) return
        val endpoint = if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            CastTransferEndpoint.REMOTE
        } else CastTransferEndpoint.LOCAL
        transferContinuityMonitor.observe(endpoint, CastTransferStatePolicy.snapshot(player))
    }

    private fun authoritativePlayerOrNull(): Player? = when {
        ::castPlayer.isInitialized -> castPlayer
        ::engine.isInitialized -> engine.player
        else -> null
    }

    private fun clearPrivatePlaybackIfActive() {
        val player = authoritativePlayerOrNull() ?: return
        val item = player.currentMediaItem ?: return
        if (!isPrivate(item)) return
        player.pause()
        if (player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) player.clearMediaItems()
        persistenceJob?.cancel()
        persistenceJob = null
    }

    private fun persistCurrent() {
        val player = authoritativePlayerOrNull() ?: return
        val item = player.currentMediaItem ?: return
        val rawUri = item.localConfiguration?.uri?.toString() ?: return
        val privateMedia = PrivateVaultIdentity.isPrivateUri(rawUri)
        val uri = if (rawUri.substringBefore(':').lowercase() in setOf("http", "https", "rtsp", "ftp", "ftps", "maxsmb")) {
            NetworkUriPolicy.persistenceSafeUri(rawUri)
        } else rawUri
        val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        val title = if (privateMedia) "Private media" else item.mediaMetadata.title?.toString().orEmpty().ifBlank { uri }
        val mime = item.localConfiguration?.mimeType
        val repository = (application as MaxVideoPlayerApplication).container.historyRepository
        serviceScope.launch(Dispatchers.IO) {
            repository.recordSnapshot(mediaId, uri, title, position, duration, mime)
        }
    }

    private fun isPrivate(item: MediaItem): Boolean =
        PrivateVaultIdentity.isPrivateUri(item.localConfiguration?.uri?.toString()) || PrivateVaultIdentity.isPrivateUri(item.mediaId)
}

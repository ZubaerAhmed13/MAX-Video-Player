package com.zubaer.maxvideoplayer.playback.session

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
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
    private lateinit var mediaSession: MediaSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var persistenceJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startPersistenceTicker() else {
                persistenceJob?.cancel()
                persistenceJob = null
                persistCurrent()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) persistCurrent()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) persistCurrent()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = (application as MaxVideoPlayerApplication).container
        engine = Media3PlaybackEngine(this, container.subtitleRepository)
        engine.player.addListener(listener)
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
        val uri = item.localConfiguration?.uri?.toString() ?: return
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

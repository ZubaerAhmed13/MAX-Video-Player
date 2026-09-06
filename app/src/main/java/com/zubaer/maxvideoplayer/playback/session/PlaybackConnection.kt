package com.zubaer.maxvideoplayer.playback.session

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class PlaybackConnection(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val directExecutor = Executor { it.run() }
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var connectRequested = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = PlaybackErrorMapper.map(error), isBuffering = false)
        }
    }

    fun connect() {
        if (connectRequested || controller != null) return
        connectRequested = true
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                mediaController.addListener(listener)
                publish(mediaController)
                startTicker()
            }.onFailure {
                connectRequested = false
                _state.value = _state.value.copy(connected = false)
            }
        }, directExecutor)
    }

    fun disconnect() {
        tickerJob?.cancel()
        tickerJob = null
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        connectRequested = false
        _state.value = PlaybackUiState()
    }

    fun load(media: AppMedia, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        withController { player ->
            val item = media.toMedia3Item()
            player.setMediaItem(item, startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = playWhenReady
        }
    }

    fun setQueue(media: List<AppMedia>, startIndex: Int = 0, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        if (media.isEmpty()) return
        withController { player ->
            player.setMediaItems(media.map { it.toMedia3Item() }, startIndex.coerceIn(media.indices), startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = playWhenReady
        }
    }

    fun play() = withController { it.play() }
    fun pause() = withController { it.pause() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }
    fun seekToNext() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun seekToPrevious() = withController { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    fun setPlaybackSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed.coerceIn(0.25f, 4f)) }
    fun setRepeatMode(mode: RepeatMode) = withController {
        it.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }
    fun playerOrNull(): Player? = controller

    private fun withController(block: (MediaController) -> Unit) {
        val current = controller
        if (current != null) block(current) else connect()
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                controller?.let(::publish)
                delay(if (controller?.isPlaying == true) 500L else 1_000L)
            }
        }
    }

    private fun publish(player: Player) {
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        _state.value = PlaybackUiState(
            connected = true,
            mediaId = player.currentMediaItem?.mediaId,
            title = player.mediaMetadata.title?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            playbackEnded = player.playbackState == Player.STATE_ENDED,
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPercentage = player.bufferedPercentage.coerceIn(0, 100),
            playbackSpeed = player.playbackParameters.speed,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            error = player.playerError?.let(PlaybackErrorMapper::map),
        )
    }

    private fun AppMedia.toMedia3Item(): MediaItem = MediaItem.Builder()
        .setMediaId(stableId)
        .setUri(uri)
        .setMimeType(mimeType)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()
}

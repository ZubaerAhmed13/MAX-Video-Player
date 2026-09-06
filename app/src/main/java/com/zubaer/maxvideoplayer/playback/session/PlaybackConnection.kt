package com.zubaer.maxvideoplayer.playback.session

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import com.zubaer.maxvideoplayer.core.model.SubtitlePlaybackState
import com.zubaer.maxvideoplayer.core.model.SubtitleTrackInfo
import com.zubaer.maxvideoplayer.feature.subtitle.ExternalSubtitleAttachment
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleFileDescriptor
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
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

class PlaybackConnection(
    context: Context,
    private val subtitleRepository: SubtitleRepository = SubtitleRepository(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val directExecutor = Executor { it.run() }
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var connectRequested = false
    private var pendingExternalSelectionMediaId: String? = null

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
        pendingExternalSelectionMediaId = null
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
    fun retry() = withController {
        if (it.playbackState == Player.STATE_ENDED) it.seekTo(0L)
        it.prepare()
        it.play()
    }

    fun seekTo(positionMs: Long) = withController { player ->
        val duration = player.duration.takeIf { it > 0L }
        val safe = if (duration != null) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        player.seekTo(safe)
    }

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

    fun setShuffleEnabled(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    fun setSubtitlesEnabled(enabled: Boolean) = withController { player ->
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return@withController
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
        publish(player)
    }

    fun selectSubtitleAuto() = withController { player ->
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return@withController
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        publish(player)
    }

    fun selectSubtitleTrack(trackKey: String) = withController { player ->
        selectSubtitleTrackInternal(player, trackKey)
    }

    fun attachExternalSubtitle(descriptor: SubtitleFileDescriptor) = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        val attachment = subtitleRepository.saveExternalAttachment(mediaId, descriptor)
        pendingExternalSelectionMediaId = mediaId
        replaceCurrentItemSubtitleConfiguration(player, attachment)
    }

    fun clearExternalSubtitle() = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        subtitleRepository.clearExternalAttachment(mediaId)
        pendingExternalSelectionMediaId = null
        if (player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
        replaceCurrentItemSubtitleConfiguration(player, null)
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
        val subtitleState = buildSubtitleState(player)
        val mediaId = player.currentMediaItem?.mediaId
        if (pendingExternalSelectionMediaId == mediaId) {
            val external = subtitleState.tracks.firstOrNull { it.external }
            if (external != null) {
                pendingExternalSelectionMediaId = null
                selectSubtitleTrackInternal(player, external.key)
                return
            }
        }
        _state.value = PlaybackUiState(
            connected = true,
            mediaId = mediaId,
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
            currentMediaItemIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            mediaItemCount = player.mediaItemCount.coerceAtLeast(0),
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            shuffleEnabled = player.shuffleModeEnabled,
            subtitles = subtitleState,
            error = player.playerError?.let(PlaybackErrorMapper::map),
        )
    }

    private fun buildSubtitleState(player: Player): SubtitlePlaybackState {
        val disabled = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS)) {
            return SubtitlePlaybackState(enabled = !disabled)
        }
        val mediaId = player.currentMediaItem?.mediaId
        val attachment = mediaId?.let(subtitleRepository::externalAttachmentFor)
        val tracks = mutableListOf<SubtitleTrackInfo>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val key = trackKey(groupIndex, trackIndex)
                val externalById = format.id?.startsWith(EXTERNAL_SUBTITLE_ID_PREFIX) == true
                val externalByDescriptor = attachment?.let { candidate ->
                    format.label?.toString() == candidate.label &&
                        format.sampleMimeType == candidate.mimeType &&
                        (candidate.language == null || format.language == candidate.language)
                } == true
                tracks += SubtitleTrackInfo(
                    key = key,
                    label = format.label?.toString()
                        ?: format.language?.uppercase()
                        ?: "Subtitle ${tracks.size + 1}",
                    language = format.language,
                    mimeType = format.sampleMimeType,
                    selected = group.isTrackSelected(trackIndex),
                    supported = group.isTrackSupported(trackIndex),
                    external = externalById || externalByDescriptor,
                )
            }
        }
        return SubtitlePlaybackState(
            enabled = !disabled,
            tracks = tracks,
            selectedTrackKey = tracks.firstOrNull { it.selected }?.key,
            externalAttached = attachment != null,
            externalLabel = attachment?.label,
        )
    }

    private fun selectSubtitleTrackInternal(player: Player, trackKey: String) {
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS) ||
            !player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)
        ) return
        val parsed = parseTrackKey(trackKey) ?: return
        val group = player.currentTracks.groups.getOrNull(parsed.first) ?: return
        if (group.type != C.TRACK_TYPE_TEXT || parsed.second !in 0 until group.length) return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, parsed.second))
            .build()
        publish(player)
    }

    private fun replaceCurrentItemSubtitleConfiguration(
        player: MediaController,
        attachment: ExternalSubtitleAttachment?,
    ) {
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return
        val current = player.currentMediaItem ?: return
        val index = player.currentMediaItemIndex
        if (index < 0) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        val existing = current.localConfiguration?.subtitleConfigurations.orEmpty()
            .filterNot { it.id?.startsWith(EXTERNAL_SUBTITLE_ID_PREFIX) == true }
        val updatedConfigurations = if (attachment == null) existing else existing + attachment.toMedia3Configuration()
        val updated = current.buildUpon().setSubtitleConfigurations(updatedConfigurations).build()
        player.replaceMediaItem(index, updated)
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    private fun AppMedia.toMedia3Item(): MediaItem {
        val attachment = subtitleRepository.externalAttachmentFor(stableId)
        return MediaItem.Builder()
            .setMediaId(stableId)
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .apply {
                if (attachment != null) setSubtitleConfigurations(listOf(attachment.toMedia3Configuration()))
            }
            .build()
    }

    private fun ExternalSubtitleAttachment.toMedia3Configuration(): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setId("$EXTERNAL_SUBTITLE_ID_PREFIX${stableSuffix(mediaId)}")
            .setLabel(label)
            .setLanguage(language)
            .setMimeType(mimeType)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT)
            .build()

    private fun trackKey(groupIndex: Int, trackIndex: Int): String = "g${groupIndex}t${trackIndex}"

    private fun parseTrackKey(key: String): Pair<Int, Int>? {
        val match = TRACK_KEY.matchEntire(key) ?: return null
        return match.groupValues[1].toIntOrNull()?.let { group ->
            match.groupValues[2].toIntOrNull()?.let { track -> group to track }
        }
    }

    private fun stableSuffix(mediaId: String): String = mediaId.hashCode().toUInt().toString(16)

    private companion object {
        const val EXTERNAL_SUBTITLE_ID_PREFIX = "max.external."
        val TRACK_KEY = Regex("g(\\d+)t(\\d+)")
    }
}

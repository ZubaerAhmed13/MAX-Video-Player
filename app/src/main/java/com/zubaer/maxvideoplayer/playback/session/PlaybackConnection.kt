package com.zubaer.maxvideoplayer.playback.session

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.ExternalSubtitleInfo
import com.zubaer.maxvideoplayer.core.model.PlaybackTarget
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import com.zubaer.maxvideoplayer.core.model.SubtitlePlaybackState
import com.zubaer.maxvideoplayer.core.model.SubtitleTrackInfo
import com.zubaer.maxvideoplayer.core.model.VideoTrackInfo
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.feature.subtitle.ExternalSubtitleAttachment
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleAvailability
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleEncoding
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleFileDescriptor
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleMatcher
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
import com.zubaer.maxvideoplayer.feature.network.diagnostics.NetworkDiagnosticsMonitor
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackConnection(
    context: Context,
    private val subtitleRepository: SubtitleRepository = SubtitleRepository(context.applicationContext),
    networkDiagnosticsMonitor: NetworkDiagnosticsMonitor? = null,
) {
    private val appContext = context.applicationContext
    private val networkDiagnosticsMonitor = networkDiagnosticsMonitor
        ?: (appContext as? MaxVideoPlayerApplication)?.container?.networkDiagnosticsMonitor
        ?: NetworkDiagnosticsMonitor(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val directExecutor = Executor { it.run() }
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var controllerListener: Player.Listener? = null
    private var tickerJob: Job? = null
    private var connectRequested = false
    private var pendingExternalSelectionMediaId: String? = null
    private var pendingExternalSelectionId: String? = null
    private var lastAutoDiscoveryMediaId: String? = null
    private var recoverableSubtitleError: String? = null
    private var pendingSeekMediaId: String? = null
    private var pendingSeekPositionMs: Long? = null
    private val knownMediaById = ConcurrentHashMap<String, AppMedia>()

    init {
        scope.launch {
            this@PlaybackConnection.networkDiagnosticsMonitor.state.collect { diagnostics ->
                _state.value = _state.value.copy(network = diagnostics)
            }
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
                if (controllerFuture !== future) {
                    MediaController.releaseFuture(future)
                    return@onSuccess
                }
                val listener = object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (controller !== mediaController) return
                        publish(player)
                    }

                    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                        if (controller !== mediaController) return
                        publish(mediaController)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (controller !== mediaController) return
                        // The callback payload can be queued before a media/session transition.
                        // Publish the controller's current Player state instead of copying the
                        // delayed payload into a newer media item. If the same error is still
                        // authoritative, mediaController.playerError retains it and publish maps it.
                        if (mediaController.playerError?.errorCode != error.errorCode) {
                            publish(mediaController)
                            return
                        }
                        publish(mediaController)
                    }
                }
                controller = mediaController
                controllerListener = listener
                mediaController.addListener(listener)
                publish(mediaController)
                startTicker()
            }.onFailure {
                if (controllerFuture !== future) return@onFailure
                controllerFuture = null
                connectRequested = false
                _state.value = _state.value.copy(connected = false)
            }
        }, directExecutor)
    }

    fun disconnect() {
        tickerJob?.cancel()
        tickerJob = null
        controller?.let { current ->
            controllerListener?.let(current::removeListener)
            if (current.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) current.clearVideoSurface()
        }
        controllerListener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        connectRequested = false
        pendingExternalSelectionMediaId = null
        pendingExternalSelectionId = null
        lastAutoDiscoveryMediaId = null
        recoverableSubtitleError = null
        clearPendingSeek()
        _state.value = PlaybackUiState()
    }

    fun load(media: AppMedia, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        knownMediaById[media.stableId] = media
        _state.value = _state.value.copy(error = null)
        withController { player ->
            clearPendingSeek()
            applyAutoPolicy(player)
            player.setMediaItem(media.toMedia3Item(), startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = playWhenReady
        }
    }

    fun setQueue(media: List<AppMedia>, startIndex: Int = 0, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        if (media.isEmpty()) return
        media.forEach { knownMediaById[it.stableId] = it }
        _state.value = _state.value.copy(error = null)
        withController { player ->
            clearPendingSeek()
            applyAutoPolicy(player)
            player.setMediaItems(media.map { it.toMedia3Item() }, startIndex.coerceIn(media.indices), startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = playWhenReady
        }
    }

    fun play() = withController { it.play() }
    fun pause() = withController { it.pause() }
    fun retry() = withController {
        networkDiagnosticsMonitor.recordReconnect()
        if (it.playbackState == Player.STATE_ENDED) it.seekTo(0L)
        it.prepare()
        it.play()
    }

    fun seekTo(positionMs: Long) = withController { player ->
        val duration = player.duration.takeIf { it > 0L }
        val safe = if (duration != null) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        val mediaId = player.currentMediaItem?.mediaId
        if (player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            clearPendingSeek()
            player.seekTo(safe)
        } else if (mediaId != null) {
            pendingSeekMediaId = mediaId
            pendingSeekPositionMs = safe
        }
    }

    fun goLive() = withController { player ->
        if (player.isCurrentMediaItemLive) {
            clearPendingSeek()
            player.seekToDefaultPosition()
            player.play()
        }
    }

    fun seekToNext() = withController {
        clearPendingSeek()
        if (it.hasNextMediaItem()) it.seekToNextMediaItem()
    }

    fun seekToPrevious() = withController {
        clearPendingSeek()
        if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
    }

    fun setPlaybackSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed.coerceIn(0.25f, 4f)) }

    fun setRepeatMode(mode: RepeatMode) = withController {
        it.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    fun setShuffleEnabled(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    fun selectVideoQualityAuto() = withController { player ->
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return@withController
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .build()
        publish(player)
    }

    fun selectVideoTrack(trackKey: String) = withController { player ->
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return@withController
        val parsed = parseTrackKey(trackKey) ?: return@withController
        val group = player.currentTracks.groups.getOrNull(parsed.first) ?: return@withController
        if (group.type != C.TRACK_TYPE_VIDEO || parsed.second !in 0 until group.length) return@withController
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, parsed.second))
            .build()
        publish(player)
    }

    fun setSubtitlesEnabled(enabled: Boolean) = withController { player ->
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return@withController
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
        if (enabled) recoverableSubtitleError = null
        publish(player)
    }

    fun selectSubtitleOff() = setSubtitlesEnabled(false)

    fun selectSubtitleAuto() = withController { player ->
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return@withController
        val languages = subtitleRepository.subtitlePreferences.value.preferredLanguages
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setPreferredTextLanguages(*languages.toTypedArray())
            .setSelectUndeterminedTextLanguage(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        player.currentMediaItem?.mediaId?.let { subtitleRepository.selectExternalAttachment(it, null) }
        recoverableSubtitleError = null
        publish(player)
    }

    fun selectSubtitleTrack(trackKey: String) = withController { player ->
        val parsed = parseTrackKey(trackKey) ?: return@withController
        val group = player.currentTracks.groups.getOrNull(parsed.first) ?: return@withController
        if (group.type != C.TRACK_TYPE_TEXT || parsed.second !in 0 until group.length) return@withController
        val mediaId = player.currentMediaItem?.mediaId
        val format = group.getTrackFormat(parsed.second)
        val attachment = mediaId?.let { resolveExternalAttachment(format, subtitleRepository.externalAttachmentsFor(it)) }
        if (attachment != null && mediaId != null) {
            val previousDelay = subtitleRepository.subtitleDelayFor(mediaId)
            subtitleRepository.selectExternalAttachment(mediaId, attachment.id)
            val restoredDelay = subtitleRepository.subtitleDelayFor(mediaId)
            if (restoredDelay != previousDelay) {
                pendingExternalSelectionMediaId = mediaId
                pendingExternalSelectionId = attachment.id
                rebuildCurrentMediaSource(player)
                return@withController
            }
        }
        selectSubtitleTrackInternal(player, trackKey)
    }

    fun selectExternalSubtitleAssociation(attachmentId: String) = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        val attachment = subtitleRepository.externalAttachmentById(mediaId, attachmentId) ?: return@withController
        subtitleRepository.selectExternalAttachment(mediaId, attachmentId)
        if (attachment.availability != SubtitleAvailability.AVAILABLE) {
            recoverableSubtitleError = subtitleRepository.recoverableErrorFor(mediaId)
                ?: "Subtitle source is unavailable. Relink or remove it; video playback can continue."
        }
        pendingExternalSelectionMediaId = mediaId
        pendingExternalSelectionId = attachmentId
        rebuildCurrentMediaSource(player)
    }

    fun attachExternalSubtitle(descriptor: SubtitleFileDescriptor) = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        val attachment = subtitleRepository.saveExternalAttachment(mediaId, descriptor, preferred = true)
        pendingExternalSelectionMediaId = mediaId
        pendingExternalSelectionId = attachment.id
        recoverableSubtitleError = null
        rebuildCurrentMediaSource(player)
        if (attachment.availability != SubtitleAvailability.AVAILABLE) refreshExternalSubtitles()
    }

    fun relinkExternalSubtitle(attachmentId: String, descriptor: SubtitleFileDescriptor) = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        scope.launch {
            val replacement = subtitleRepository.relinkExternalAttachment(mediaId, attachmentId, descriptor) ?: return@launch
            if (controller?.currentMediaItem?.mediaId != mediaId) return@launch
            recoverableSubtitleError = null
            pendingExternalSelectionMediaId = mediaId
            pendingExternalSelectionId = replacement.id
            controller?.let(::rebuildCurrentMediaSource)
        }
    }

    fun setExternalSubtitleEncoding(attachmentId: String, encoding: SubtitleEncoding) = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        if (!subtitleRepository.setExternalEncoding(mediaId, attachmentId, encoding)) return@withController
        pendingExternalSelectionMediaId = mediaId
        pendingExternalSelectionId = attachmentId
        scope.launch {
            subtitleRepository.refreshAvailability(mediaId)
            if (controller?.currentMediaItem?.mediaId == mediaId) controller?.let(::rebuildCurrentMediaSource)
        }
    }

    fun refreshExternalSubtitles() = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        scope.launch {
            val changed = subtitleRepository.refreshAvailability(mediaId)
            recoverableSubtitleError = subtitleRepository.recoverableErrorFor(mediaId)
            if (changed && controller?.currentMediaItem?.mediaId == mediaId) controller?.let(::rebuildCurrentMediaSource)
        }
    }

    fun removeExternalSubtitle(attachmentId: String) = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        subtitleRepository.removeExternalAttachment(mediaId, attachmentId)
        pendingExternalSelectionMediaId = null
        pendingExternalSelectionId = null
        if (player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
        recoverableSubtitleError = subtitleRepository.recoverableErrorFor(mediaId)
        rebuildCurrentMediaSource(player)
    }

    fun clearExternalSubtitle() = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        subtitleRepository.clearExternalAttachment(mediaId)
        pendingExternalSelectionMediaId = null
        pendingExternalSelectionId = null
        recoverableSubtitleError = null
        if (player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
        rebuildCurrentMediaSource(player)
    }

    fun setSubtitleDelay(delayMs: Long) = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        val selectedAssociation = currentExternalAssociationId(player)
            ?: subtitleRepository.selectedExternalAttachmentId(mediaId)
        if (selectedAssociation != null) subtitleRepository.selectExternalAttachment(mediaId, selectedAssociation)
        subtitleRepository.setSubtitleDelay(mediaId, delayMs)
        pendingExternalSelectionMediaId = mediaId.takeIf { selectedAssociation != null }
        pendingExternalSelectionId = selectedAssociation
        rebuildCurrentMediaSource(player)
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        val mediaId = state.value.mediaId ?: return
        setSubtitleDelay(subtitleRepository.subtitleDelayFor(mediaId) + deltaMs)
    }

    fun resetSubtitleDelay() = setSubtitleDelay(0L)

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
        val mediaId = player.currentMediaItem?.mediaId
        maybeApplyPendingSeek(player, mediaId)
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val subtitleState = buildSubtitleState(player)

        if (pendingExternalSelectionMediaId == mediaId) {
            val associationId = pendingExternalSelectionId
            val external = subtitleState.tracks.firstOrNull {
                it.external && (associationId == null || it.externalAssociationId == associationId)
            }
            if (external != null) {
                pendingExternalSelectionMediaId = null
                pendingExternalSelectionId = null
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
            playbackTarget = if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                PlaybackTarget.CAST_DEVICE
            } else {
                PlaybackTarget.LOCAL_DEVICE
            },
            subtitles = subtitleState,
            network = networkDiagnosticsMonitor.state.value,
            videoTracks = buildVideoTracks(player),
            videoQualityAuto = player.trackSelectionParameters.overrides.keys.none { it.type == C.TRACK_TYPE_VIDEO },
            isLive = player.isCurrentMediaItemLive,
            liveOffsetMs = player.currentLiveOffset.takeIf { player.isCurrentMediaItemLive && it != C.TIME_UNSET },
            error = player.playerError?.let(PlaybackErrorMapper::map),
        )

        maybeDiscoverSidecars(mediaId)
    }

    private fun maybeApplyPendingSeek(player: Player, mediaId: String?) {
        val expectedMediaId = pendingSeekMediaId ?: return
        val positionMs = pendingSeekPositionMs ?: return
        if (mediaId != null && mediaId != expectedMediaId) {
            clearPendingSeek()
            return
        }
        if (mediaId != expectedMediaId || !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
        clearPendingSeek()
        player.seekTo(positionMs)
    }

    private fun clearPendingSeek() {
        pendingSeekMediaId = null
        pendingSeekPositionMs = null
    }

    private fun maybeDiscoverSidecars(mediaId: String?) {
        if (mediaId == null || mediaId == lastAutoDiscoveryMediaId) return
        lastAutoDiscoveryMediaId = mediaId
        val media = knownMediaById[mediaId] ?: return
        scope.launch {
            val availabilityChanged = subtitleRepository.refreshAvailability(mediaId)
            val created = subtitleRepository.discoverMatchingSidecars(media)
            recoverableSubtitleError = subtitleRepository.recoverableErrorFor(mediaId)
            if ((availabilityChanged || created.isNotEmpty()) && controller?.currentMediaItem?.mediaId == mediaId) {
                controller?.let { current ->
                    val preferred = created.firstOrNull { it.isPreferred }
                    if (preferred != null) {
                        pendingExternalSelectionMediaId = mediaId
                        pendingExternalSelectionId = preferred.id
                    }
                    rebuildCurrentMediaSource(current)
                }
            }
        }
    }

    private fun buildSubtitleState(player: Player): SubtitlePlaybackState {
        val disabled = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes
        val mediaId = player.currentMediaItem?.mediaId
        val attachments = mediaId?.let(subtitleRepository::externalAttachmentsFor).orEmpty()
        val recoverable = recoverableSubtitleError ?: mediaId?.let(subtitleRepository::recoverableErrorFor)
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS)) {
            return SubtitlePlaybackState(
                enabled = !disabled,
                externalAttached = attachments.isNotEmpty(),
                externalAssociations = attachments.map(::externalInfo),
                selectedExternalAssociationId = mediaId?.let(subtitleRepository::selectedExternalAttachmentId),
                delayMs = mediaId?.let(subtitleRepository::subtitleDelayFor) ?: 0L,
                recoverableError = recoverable,
            )
        }

        val tracks = mutableListOf<SubtitleTrackInfo>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val key = trackKey(groupIndex, trackIndex)
                val attachment = resolveExternalAttachment(format, attachments)
                val associationId = attachment?.id ?: associationIdFromFormat(format)
                val external = attachment != null || associationId != null
                val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                val isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
                val readableLanguage = SubtitleMatcher.humanLanguageName(format.language)
                val baseLabel = when {
                    attachment != null && attachment.language != null -> "External — ${SubtitleMatcher.humanLanguageName(attachment.language)}"
                    attachment != null -> "External — ${attachment.label}"
                    !format.label.isNullOrBlank() -> format.label.toString()
                    format.language != null -> readableLanguage
                    else -> "Subtitle ${tracks.size + 1}"
                }
                val label = buildString {
                    append(baseLabel)
                    if (forced) append(" (Forced)")
                }
                tracks += SubtitleTrackInfo(
                    key = key,
                    label = label,
                    language = format.language,
                    mimeType = attachment?.mimeType ?: format.sampleMimeType,
                    selected = group.isTrackSelected(trackIndex),
                    supported = group.isTrackSupported(trackIndex),
                    external = external,
                    forced = forced,
                    default = isDefault,
                    externalAssociationId = associationId,
                )
            }
        }

        val selectedAssociationId = tracks.firstOrNull { it.selected && it.external }?.externalAssociationId
            ?: mediaId?.let(subtitleRepository::selectedExternalAttachmentId)
        return SubtitlePlaybackState(
            enabled = !disabled,
            tracks = tracks,
            selectedTrackKey = tracks.firstOrNull { it.selected }?.key,
            externalAttached = attachments.isNotEmpty(),
            externalLabel = attachments.firstOrNull { it.id == selectedAssociationId }?.label
                ?: attachments.firstOrNull { it.isPreferred }?.label,
            externalAssociations = attachments.map(::externalInfo),
            selectedExternalAssociationId = selectedAssociationId,
            delayMs = mediaId?.let(subtitleRepository::subtitleDelayFor) ?: 0L,
            recoverableError = recoverable,
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
        recoverableSubtitleError = null
        publish(player)
    }

    private fun buildVideoTracks(player: Player): List<VideoTrackInfo> {
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS)) return emptyList()
        return buildList {
            player.currentTracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type != C.TRACK_TYPE_VIDEO) return@forEachIndexed
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    add(
                        VideoTrackInfo(
                            key = trackKey(groupIndex, trackIndex),
                            width = format.width.takeIf { it > 0 },
                            height = format.height.takeIf { it > 0 },
                            bitrate = format.bitrate.takeIf { it > 0 },
                            codec = format.codecs ?: format.sampleMimeType,
                            selected = group.isTrackSelected(trackIndex),
                            supported = group.isTrackSupported(trackIndex),
                        ),
                    )
                }
            }
        }.distinctBy { listOf(it.width, it.height, it.bitrate, it.codec) }
            .sortedWith(compareByDescending<VideoTrackInfo> { it.height ?: 0 }.thenByDescending { it.bitrate ?: 0 })
    }

    private fun applyAutoPolicy(player: Player) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return
        val languages = subtitleRepository.subtitlePreferences.value.preferredLanguages
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setPreferredTextLanguages(*languages.toTypedArray())
            .setSelectUndeterminedTextLanguage(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }

    private fun rebuildCurrentMediaSource(player: MediaController) {
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) ||
            !player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)
        ) return
        val current = player.currentMediaItem ?: return
        val mediaId = current.mediaId
        val index = player.currentMediaItemIndex
        if (index !in 0 until player.mediaItemCount) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        val existingNonMax = current.localConfiguration?.subtitleConfigurations.orEmpty()
            .filterNot { it.id?.startsWith(EXTERNAL_SUBTITLE_ID_PREFIX) == true }
        val configured = existingNonMax + subtitleRepository.externalAttachmentsFor(mediaId)
            .filter { it.availability == SubtitleAvailability.AVAILABLE }
            .map { it.toMedia3Configuration() }
        val updated = current.buildUpon().setSubtitleConfigurations(configured).build()
        val rebuiltQueue = MutableList(player.mediaItemCount) { queueIndex -> player.getMediaItemAt(queueIndex) }
        rebuiltQueue[index] = updated
        player.setMediaItems(rebuiltQueue, index, position)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    private fun AppMedia.toMedia3Item(): MediaItem {
        val attachments = subtitleRepository.externalAttachmentsFor(stableId)
            .filter { it.availability == SubtitleAvailability.AVAILABLE }
        return MediaItem.Builder()
            .setMediaId(stableId)
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .apply {
                if (attachments.isNotEmpty()) setSubtitleConfigurations(attachments.map { it.toMedia3Configuration() })
            }
            .build()
    }

    private fun ExternalSubtitleAttachment.toMedia3Configuration(): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setId("$EXTERNAL_SUBTITLE_ID_PREFIX$id")
            .setLabel(label)
            .setLanguage(language)
            .setMimeType(mimeType)
            .setSelectionFlags(if (isPreferred) C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT else 0)
            .build()

    private fun externalInfo(attachment: ExternalSubtitleAttachment): ExternalSubtitleInfo = ExternalSubtitleInfo(
        id = attachment.id,
        label = attachment.label,
        language = attachment.language,
        mimeType = attachment.mimeType,
        format = attachment.format.name,
        encoding = attachment.encoding.name,
        preferred = attachment.isPreferred,
        availability = attachment.availability.name,
        delayMs = attachment.delayMs,
    )

    private fun resolveExternalAttachment(
        format: Format,
        attachments: List<ExternalSubtitleAttachment>,
    ): ExternalSubtitleAttachment? {
        val id = associationIdFromFormat(format)
        if (id != null) attachments.firstOrNull { it.id == id }?.let { return it }
        return attachments.firstOrNull { candidate ->
            val labelMatches = format.label?.toString() == candidate.label
            val mimeMatches = format.sampleMimeType == candidate.mimeType || format.codecs == candidate.mimeType
            val languageMatches = candidate.language == null || format.language == candidate.language
            labelMatches && mimeMatches && languageMatches
        }
    }

    private fun associationIdFromFormat(format: Format): String? = format.id
        ?.takeIf { it.startsWith(EXTERNAL_SUBTITLE_ID_PREFIX) }
        ?.removePrefix(EXTERNAL_SUBTITLE_ID_PREFIX)

    private fun currentExternalAssociationId(player: Player): String? {
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS)) return null
        val mediaId = player.currentMediaItem?.mediaId ?: return null
        val attachments = subtitleRepository.externalAttachmentsFor(mediaId)
        player.currentTracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEach
            for (trackIndex in 0 until group.length) {
                if (group.isTrackSelected(trackIndex)) {
                    return resolveExternalAttachment(group.getTrackFormat(trackIndex), attachments)?.id
                }
            }
        }
        return null
    }

    private fun trackKey(groupIndex: Int, trackIndex: Int): String = "g${groupIndex}t${trackIndex}"

    private fun parseTrackKey(key: String): Pair<Int, Int>? {
        val match = TRACK_KEY.matchEntire(key) ?: return null
        return match.groupValues[1].toIntOrNull()?.let { group ->
            match.groupValues[2].toIntOrNull()?.let { track -> group to track }
        }
    }

    private companion object {
        const val EXTERNAL_SUBTITLE_ID_PREFIX = "max.external."
        val TRACK_KEY = Regex("g(\\d+)t(\\d+)")
    }
}
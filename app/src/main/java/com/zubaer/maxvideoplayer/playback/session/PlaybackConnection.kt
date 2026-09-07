package com.zubaer.maxvideoplayer.playback.session

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.ExternalSubtitleInfo
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import com.zubaer.maxvideoplayer.core.model.SubtitlePlaybackState
import com.zubaer.maxvideoplayer.core.model.SubtitleTrackInfo
import com.zubaer.maxvideoplayer.feature.subtitle.ExternalSubtitleAttachment
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleAvailability
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleFileDescriptor
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleMatcher
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
import java.util.concurrent.ConcurrentHashMap
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
    private var pendingExternalSelectionId: String? = null
    private var lastAutoDiscoveryMediaId: String? = null
    private var recoverableSubtitleError: String? = null
    private val knownMediaById = ConcurrentHashMap<String, AppMedia>()

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
        controller?.let { current ->
            current.removeListener(listener)
            if (current.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) current.clearVideoSurface()
        }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        connectRequested = false
        pendingExternalSelectionMediaId = null
        pendingExternalSelectionId = null
        lastAutoDiscoveryMediaId = null
        recoverableSubtitleError = null
        _state.value = PlaybackUiState()
    }

    fun load(media: AppMedia, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        knownMediaById[media.stableId] = media
        withController { player ->
            applyAutoPolicy(player)
            player.setMediaItem(media.toMedia3Item(), startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = playWhenReady
        }
    }

    fun setQueue(media: List<AppMedia>, startIndex: Int = 0, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        if (media.isEmpty()) return
        media.forEach { knownMediaById[it.stableId] = it }
        withController { player ->
            applyAutoPolicy(player)
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
        val associationId = associationIdFromFormat(group.getTrackFormat(parsed.second))
        val mediaId = player.currentMediaItem?.mediaId
        if (associationId != null && mediaId != null) {
            val previousDelay = subtitleRepository.subtitleDelayFor(mediaId)
            subtitleRepository.selectExternalAttachment(mediaId, associationId)
            val restoredDelay = subtitleRepository.subtitleDelayFor(mediaId)
            if (restoredDelay != previousDelay) {
                pendingExternalSelectionMediaId = mediaId
                pendingExternalSelectionId = associationId
                rebuildCurrentMediaSource(player)
                return@withController
            }
        }
        selectSubtitleTrackInternal(player, trackKey)
    }

    fun attachExternalSubtitle(descriptor: SubtitleFileDescriptor) = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        val attachment = subtitleRepository.saveExternalAttachment(mediaId, descriptor, preferred = true)
        pendingExternalSelectionMediaId = mediaId
        pendingExternalSelectionId = attachment.id
        recoverableSubtitleError = null
        rebuildCurrentMediaSource(player)
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
        rebuildCurrentMediaSource(player)
    }

    fun clearExternalSubtitle() = withController { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withController
        subtitleRepository.clearExternalAttachment(mediaId)
        pendingExternalSelectionMediaId = null
        pendingExternalSelectionId = null
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
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val subtitleState = buildSubtitleState(player)
        val mediaId = player.currentMediaItem?.mediaId

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
            subtitles = subtitleState,
            error = player.playerError?.let(PlaybackErrorMapper::map),
        )

        maybeDiscoverSidecars(mediaId, player)
    }

    private fun maybeDiscoverSidecars(mediaId: String?, player: Player) {
        if (mediaId == null || mediaId == lastAutoDiscoveryMediaId) return
        lastAutoDiscoveryMediaId = mediaId
        val media = knownMediaById[mediaId] ?: return
        scope.launch {
            val created = subtitleRepository.discoverMatchingSidecars(media)
            if (created.isNotEmpty() && controller?.currentMediaItem?.mediaId == mediaId) {
                controller?.let { current ->
                    if (created.any { it.isPreferred }) {
                        val preferred = created.firstOrNull { it.isPreferred }
                        pendingExternalSelectionMediaId = mediaId
                        pendingExternalSelectionId = preferred?.id
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
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS)) {
            return SubtitlePlaybackState(
                enabled = !disabled,
                externalAttached = attachments.isNotEmpty(),
                externalAssociations = attachments.map(::externalInfo),
                selectedExternalAssociationId = mediaId?.let(subtitleRepository::selectedExternalAttachmentId),
                delayMs = mediaId?.let(subtitleRepository::subtitleDelayFor) ?: 0L,
                recoverableError = recoverableSubtitleError,
            )
        }

        val tracks = mutableListOf<SubtitleTrackInfo>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val key = trackKey(groupIndex, trackIndex)
                val associationId = associationIdFromFormat(format)
                val attachment = associationId?.let { id -> attachments.firstOrNull { it.id == id } }
                val external = associationId != null
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
            recoverableError = recoverableSubtitleError,
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
            .filter { it.availability !in BLOCKED_AVAILABILITY }
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
            .filter { it.availability !in BLOCKED_AVAILABILITY }
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
        preferred = attachment.isPreferred,
        availability = attachment.availability.name,
        delayMs = attachment.delayMs,
    )

    private fun associationIdFromFormat(format: Format): String? = format.id
        ?.takeIf { it.startsWith(EXTERNAL_SUBTITLE_ID_PREFIX) }
        ?.removePrefix(EXTERNAL_SUBTITLE_ID_PREFIX)

    private fun currentExternalAssociationId(player: Player): String? {
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS)) return null
        player.currentTracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEach
            for (trackIndex in 0 until group.length) {
                if (group.isTrackSelected(trackIndex)) return associationIdFromFormat(group.getTrackFormat(trackIndex))
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
        val BLOCKED_AVAILABILITY = setOf(
            SubtitleAvailability.MISSING,
            SubtitleAvailability.PERMISSION_LOST,
            SubtitleAvailability.UNSUPPORTED,
            SubtitleAvailability.MALFORMED,
        )
    }
}

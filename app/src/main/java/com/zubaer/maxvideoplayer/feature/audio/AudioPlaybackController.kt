package com.zubaer.maxvideoplayer.feature.audio

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import java.util.Locale

/**
 * Thin controller around the existing service-owned MediaController. It never creates or owns a
 * player; every operation is sent to the single PlaybackService/MediaSession timeline.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class AudioPlaybackController(
    private val repository: AudioRepository,
    private val playbackConnection: PlaybackConnection,
) {
    private var boundPlayer: Player? = null
    private var recoveringExternalFailure = false
    private var backgroundVideoDisabled = false

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val player = boundPlayer ?: return
            publishTracks(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            repository.activateMedia(mediaItem?.mediaId)
            mediaItem?.mediaId?.let(repository::refreshExternalAvailability)
            val player = boundPlayer ?: return
            applyPersistedPolicy(player)
            publishTracks(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            val player = boundPlayer ?: return
            val mediaId = player.currentMediaItem?.mediaId ?: return
            val external = repository.selectedExternalFor(mediaId) ?: return
            if (!recoveringExternalFailure && isAttributedExternalSourceFailure(error, external)) {
                recoveringExternalFailure = true
                repository.rememberAuto(mediaId)
                rebuildCurrentMediaSource(player)
                recoveringExternalFailure = false
            }
        }
    }

    fun bind() {
        val next = playbackConnection.playerOrNull() ?: return
        if (boundPlayer === next) {
            repository.activateMedia(next.currentMediaItem?.mediaId)
            applyVideoTrackPolicy(next)
            publishTracks(next)
            return
        }
        boundPlayer?.removeListener(listener)
        boundPlayer = next
        next.addListener(listener)
        repository.activateMedia(next.currentMediaItem?.mediaId)
        applyPersistedPolicy(next)
        publishTracks(next)
    }

    fun unbind() {
        boundPlayer?.removeListener(listener)
        boundPlayer = null
    }

    fun selectAuto() = withPlayer { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withPlayer
        val hadExternal = repository.selectedExternalFor(mediaId) != null
        repository.rememberAuto(mediaId)
        if (player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setPreferredAudioLanguages(*repository.preferredLanguages().toTypedArray())
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
        }
        if (hadExternal) rebuildCurrentMediaSource(player) else publishTracks(player)
    }

    /** Global preference used only by Auto selection; manual per-video choices remain authoritative. */
    fun setPreferredLanguage(language: String) {
        repository.setPreferredLanguages(listOf(language))
        val state = repository.state.value
        if (state.selectionMode == AudioSelectionMode.AUTO && state.selectedExternalId == null) {
            selectAuto()
        }
    }

    fun selectTrack(trackKey: String) = withPlayer { player ->
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS) ||
            !player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)
        ) return@withPlayer
        val parsed = parseTrackKey(trackKey) ?: return@withPlayer
        val group = player.currentTracks.groups.getOrNull(parsed.first) ?: return@withPlayer
        if (group.type != C.TRACK_TYPE_AUDIO || parsed.second !in 0 until group.length) return@withPlayer
        val mediaId = player.currentMediaItem?.mediaId ?: return@withPlayer
        val format = group.getTrackFormat(parsed.second)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, parsed.second))
            .build()
        repository.rememberManualTrack(mediaId, format.toDescriptor())
        publishTracks(player)
    }

    fun attachExternal(descriptor: ExternalAudioDescriptor) = withPlayer { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withPlayer
        val item = repository.saveExternal(mediaId, descriptor, preferred = true)
        if (item.availability == AudioAvailability.AVAILABLE) {
            rebuildCurrentMediaSource(player)
        }
    }

    fun relinkExternal(id: String, descriptor: ExternalAudioDescriptor) = withPlayer { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withPlayer
        val replacement = repository.relinkExternal(mediaId, id, descriptor) ?: return@withPlayer
        if (replacement.availability == AudioAvailability.AVAILABLE) {
            rebuildCurrentMediaSource(player)
        }
    }

    fun selectExternal(id: String) = withPlayer { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withPlayer
        repository.selectExternal(mediaId, id)
        rebuildCurrentMediaSource(player)
    }

    fun removeExternal(id: String) = withPlayer { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withPlayer
        repository.removeExternal(mediaId, id)
        rebuildCurrentMediaSource(player)
    }

    fun setAudioDelay(delayMs: Long) = withPlayer { player ->
        val mediaId = player.currentMediaItem?.mediaId ?: return@withPlayer
        repository.setAudioDelay(mediaId, delayMs)
        flushAudioPipelineAtCurrentPosition(player)
    }

    fun adjustAudioDelay(deltaMs: Long) {
        val current = repository.state.value.audioDelayMs
        setAudioDelay(current + deltaMs)
    }

    fun resetAudioDelay() = setAudioDelay(0L)
    fun setEqualizerEnabled(enabled: Boolean) = repository.setEqualizerEnabled(enabled)
    fun setPreset(preset: EqualizerPreset) = repository.setPreset(preset)
    fun setEqBand(index: Int, db: Float) = repository.setEqBand(index, db)
    fun setPreamp(db: Float) = repository.setPreamp(db)
    fun setBoost(db: Float) = repository.setBoost(db)
    fun setChannelMode(mode: AudioChannelMode) = repository.setChannelMode(mode)
    fun setBalance(value: Float) = repository.setBalance(value)
    fun setBackgroundMode(mode: BackgroundPlaybackMode) = repository.setBackgroundMode(mode)
    fun setDisableVideoInBackground(enabled: Boolean) = repository.setDisableVideoInBackground(enabled)

    fun setRouteCompensation(valueMs: Long) = withPlayer { player ->
        repository.setRouteCompensation(repository.state.value.currentRoute.type, valueMs)
        flushAudioPipelineAtCurrentPosition(player)
    }

    fun adjustRouteCompensation(deltaMs: Long) {
        val current = repository.state.value.routeCompensationMs
        setRouteCompensation(current + deltaMs)
    }

    fun resetRouteCompensation() = setRouteCompensation(0L)

    fun setPitch(value: Float) = withPlayer { player ->
        val safe = AudioPolicy.clampPitch(value)
        repository.setPitch(safe)
        player.playbackParameters = PlaybackParameters(player.playbackParameters.speed, safe)
    }

    fun resetPitch() = setPitch(1f)

    fun setAudioOnly(enabled: Boolean) = withPlayer { player ->
        repository.setAudioOnly(enabled)
        applyVideoTrackPolicy(player)
    }

    /**
     * Lifecycle-only video suppression. The policy flag is updated before attempting to bind so
     * foreground/background state remains authoritative even if the MediaController is briefly
     * unavailable. User-selected audio-only remains authoritative through applyVideoTrackPolicy().
     */
    fun setBackgroundVideoDisabled(enabled: Boolean) {
        backgroundVideoDisabled = enabled
        withPlayer { player -> applyVideoTrackPolicy(player) }
    }

    fun refreshExternalAvailability() = withPlayer { player ->
        player.currentMediaItem?.mediaId?.let(repository::refreshExternalAvailability)
    }

    private fun applyPersistedPolicy(player: Player) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val external = repository.selectedExternalFor(mediaId)
        if (external == null) {
            val descriptor = repository.selectedTrackDescriptor(mediaId)
            if (descriptor == null) {
                if (player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setPreferredAudioLanguages(*repository.preferredLanguages().toTypedArray())
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                }
            } else {
                val match = bestDescriptorMatch(player, descriptor)
                if (match != null && player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, match.second))
                        .build()
                }
            }
        }
        // External tracks belong to the service-owned MergingMediaSource. PlaybackService applies
        // their override directly to its local ExoPlayer after the merged TrackGroup is published,
        // avoiding MediaController TrackGroup translation and asynchronous command races.
        val currentParams = player.playbackParameters
        val pitch = repository.state.value.pitch
        if (kotlin.math.abs(currentParams.pitch - pitch) > 0.001f) {
            player.playbackParameters = PlaybackParameters(currentParams.speed, pitch)
        }
        applyVideoTrackPolicy(player)
    }

    private fun applyVideoTrackPolicy(player: Player) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return
        val disableVideo = repository.state.value.audioOnlyMode || backgroundVideoDisabled
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableVideo)
            .build()
    }

    private fun publishTracks(player: Player) {
        if (!player.isCommandAvailable(Player.COMMAND_GET_TRACKS)) return
        val tracks = mutableListOf<AudioTrackInfo>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
            val external = isExternalGroup(group)
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val language = canonicalLanguage(format.language)
                val readableLanguage = language?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
                    ?.takeIf { it.isNotBlank() }
                val commentary = AudioAccessibilityPolicy.isCommentary(format.roleFlags)
                val audioDescription = AudioAccessibilityPolicy.isAudioDescription(format.roleFlags)
                val base = format.label?.toString()?.takeIf { it.isNotBlank() }
                    ?: readableLanguage
                    ?: if (external) "External audio" else "Unknown"
                tracks += AudioTrackInfo(
                    key = trackKey(groupIndex, trackIndex),
                    label = AudioAccessibilityPolicy.readableLabel(base, commentary, audioDescription),
                    language = language,
                    mimeType = format.sampleMimeType,
                    codec = format.codecs,
                    channelCount = format.channelCount.takeIf { it > 0 },
                    sampleRate = format.sampleRate.takeIf { it > 0 },
                    bitrate = format.bitrate.takeIf { it > 0 },
                    commentary = commentary,
                    selected = group.isTrackSelected(trackIndex),
                    supported = group.isTrackSupported(trackIndex),
                    external = external,
                    audioDescription = audioDescription,
                )
            }
        }
        repository.updateTracks(tracks)
    }

    private fun isExternalGroup(group: androidx.media3.common.Tracks.Group): Boolean {
        if (group.mediaTrackGroup.id.startsWith("1:")) return true
        return (0 until group.length).any { index ->
            group.getTrackFormat(index).id?.startsWith("1:") == true
        }
    }

    private fun isAttributedExternalSourceFailure(error: PlaybackException, external: ExternalAudioInfo): Boolean {
        if (error.errorCode !in SOURCE_ERROR_CODE_RANGE) return false
        val uri = external.uri
        val path = runCatching { Uri.parse(uri).path }.getOrNull()
        val errorMessages = mutableListOf<String>()
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < MAX_ERROR_CAUSE_DEPTH) {
            cause.message?.let(errorMessages::add)
            cause = cause.cause
            depth++
        }
        val messages = errorMessages.joinToString("\n")
        return messages.contains(uri) || (!path.isNullOrBlank() && messages.contains(path))
    }

    private fun bestDescriptorMatch(player: Player, descriptor: AudioTrackDescriptor): Pair<androidx.media3.common.Tracks.Group, Int>? {
        var best: Pair<androidx.media3.common.Tracks.Group, Int>? = null
        var bestScore = Int.MIN_VALUE
        player.currentTracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_AUDIO || isExternalGroup(group)) return@forEach
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                var score = 0
                if (canonicalLanguage(format.language) == canonicalLanguage(descriptor.language)) score += 8
                if (!descriptor.label.isNullOrBlank() && format.label?.toString() == descriptor.label) score += 6
                if (!descriptor.mimeType.isNullOrBlank() && format.sampleMimeType == descriptor.mimeType) score += 4
                if (descriptor.channelCount != null && format.channelCount == descriptor.channelCount) score += 2
                if (score > bestScore) { bestScore = score; best = group to index }
            }
        }
        return best?.takeIf { bestScore >= 4 }
    }

    private fun rebuildCurrentMediaSource(player: Player) {
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) ||
            !player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)
        ) return
        val index = player.currentMediaItemIndex
        if (index !in 0 until player.mediaItemCount) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        val queue = MutableList(player.mediaItemCount) { player.getMediaItemAt(it) }
        player.setMediaItems(queue, index, position)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    private fun flushAudioPipelineAtCurrentPosition(player: Player) {
        player.seekTo(player.currentPosition.coerceAtLeast(0L))
    }

    private fun withPlayer(block: (Player) -> Unit) {
        bind()
        boundPlayer?.let(block)
    }

    private fun Format.toDescriptor() = AudioTrackDescriptor(
        language = canonicalLanguage(language),
        label = label?.toString(),
        mimeType = sampleMimeType,
        channelCount = channelCount.takeIf { it > 0 },
    )

    private fun canonicalLanguage(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return Locale.forLanguageTag(raw).language.takeIf { it.isNotBlank() && it != "und" } ?: raw.lowercase(Locale.ROOT)
    }

    private fun trackKey(groupIndex: Int, trackIndex: Int): String = "g${groupIndex}t${trackIndex}"
    private fun parseTrackKey(key: String): Pair<Int, Int>? {
        val match = TRACK_KEY.matchEntire(key) ?: return null
        return match.groupValues[1].toIntOrNull()?.let { group -> match.groupValues[2].toIntOrNull()?.let { group to it } }
    }

    private companion object {
        val TRACK_KEY = Regex("g(\\d+)t(\\d+)")
        val SOURCE_ERROR_CODE_RANGE = 2_000..3_999
        const val MAX_ERROR_CAUSE_DEPTH = 8
    }
}

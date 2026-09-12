package com.zubaer.maxvideoplayer.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.core.model.PlaybackTarget
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.feature.network.model.NetworkPlaybackPhase
import com.zubaer.maxvideoplayer.ui.MaxDesignTokens
import kotlin.math.roundToLong

private enum class PlayerPanelFocusReturn { SUBTITLE, AUDIO, DECODER, MORE }

private enum class ChromeIcon {
    BACK, SUBTITLES, AUDIO, MORE, ROTATE, PLAYBACK, ORIENTATION, PIP, FULLSCREEN,
    EXIT_FULLSCREEN, LOCK, PREVIOUS, PLAY, PAUSE, REPLAY, NEXT, DISPLAY, INFO,
    DECODER, SPEED, CAST, TOOLS,
}

/**
 * Step-10 release-hardened player chrome. Playback state and transport remain service-owned; this
 * composable only presents controls and forwards existing actions.
 */
@Composable
fun PlayerControlsOverlay(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    fallbackTitle: String,
    localVideoProcessingAvailable: Boolean = true,
    subtitlePanelVisible: Boolean = false,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoLive: () -> Unit = {},
    onSeekPreview: (Long, Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    onOpenMenu: (PlayerMenu) -> Unit,
    onRotate: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
    onSubtitles: () -> Unit = {},
) {
    val hostState = LocalPlayerChromeHostState.current
    val subtitleButtonFocusRequester = remember { FocusRequester() }
    val audioButtonFocusRequester = remember { FocusRequester() }
    val decoderButtonFocusRequester = remember { FocusRequester() }
    val moreButtonFocusRequester = remember { FocusRequester() }
    var pendingFocusReturn by remember { mutableStateOf<PlayerPanelFocusReturn?>(null) }
    val controlsFocusable = coordinator.controlsVisible && !coordinator.controlsLocked

    val openSubtitlesWithFocusReturn: () -> Unit = {
        pendingFocusReturn = PlayerPanelFocusReturn.SUBTITLE
        onSubtitles()
    }
    val openAudioWithFocusReturn: () -> Unit = {
        hostState.onAudio?.let {
            pendingFocusReturn = PlayerPanelFocusReturn.AUDIO
            it()
        }
    }
    val openMenuWithFocusReturn: (PlayerMenu) -> Unit = { menu ->
        when (menu) {
            PlayerMenu.DECODER -> pendingFocusReturn = PlayerPanelFocusReturn.DECODER
            PlayerMenu.SETTINGS -> pendingFocusReturn = PlayerPanelFocusReturn.MORE
            else -> Unit
        }
        onOpenMenu(menu)
    }

    LaunchedEffect(
        subtitlePanelVisible,
        hostState.audioPanelVisible,
        coordinator.activeMenu,
        controlsFocusable,
        pendingFocusReturn,
    ) {
        if (!controlsFocusable) return@LaunchedEffect
        val target = when (pendingFocusReturn) {
            PlayerPanelFocusReturn.SUBTITLE -> if (!subtitlePanelVisible) subtitleButtonFocusRequester else null
            PlayerPanelFocusReturn.AUDIO -> if (!hostState.audioPanelVisible) audioButtonFocusRequester else null
            PlayerPanelFocusReturn.DECODER -> if (coordinator.activeMenu != PlayerMenu.DECODER) decoderButtonFocusRequester else null
            PlayerPanelFocusReturn.MORE -> if (coordinator.activeMenu != PlayerMenu.SETTINGS) moreButtonFocusRequester else null
            null -> null
        }
        if (target != null) {
            repeat(3) {
                withFrameNanos { }
                if (target.requestFocus()) {
                    pendingFocusReturn = null
                    return@LaunchedEffect
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = controlsFocusable,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                PlayerTopBar(
                    coordinator = coordinator,
                    playback = playback,
                    fallbackTitle = fallbackTitle,
                    localVideoProcessingAvailable = localVideoProcessingAvailable,
                    audioAvailable = hostState.onAudio != null,
                    subtitleButtonFocusRequester = subtitleButtonFocusRequester,
                    audioButtonFocusRequester = audioButtonFocusRequester,
                    decoderButtonFocusRequester = decoderButtonFocusRequester,
                    moreButtonFocusRequester = moreButtonFocusRequester,
                    onBack = onBack,
                    onSubtitles = openSubtitlesWithFocusReturn,
                    onAudio = openAudioWithFocusReturn,
                    onDecoder = { openMenuWithFocusReturn(PlayerMenu.DECODER) },
                    onMore = { openMenuWithFocusReturn(PlayerMenu.SETTINGS) },
                )

                PlayerQuickRail(
                    coordinator = coordinator,
                    playback = playback,
                    localVideoProcessingAvailable = localVideoProcessingAvailable,
                    audioAvailable = hostState.onAudio != null,
                    onOpenMenu = openMenuWithFocusReturn,
                    onSubtitles = openSubtitlesWithFocusReturn,
                    onAudio = openAudioWithFocusReturn,
                    onRotate = onRotate,
                    onPip = onPip,
                    onFullscreen = onFullscreen,
                )

                Spacer(Modifier.weight(1f))

                PlayerBottomBar(
                    coordinator = coordinator,
                    playback = playback,
                    localVideoProcessingAvailable = localVideoProcessingAvailable,
                    audioAvailable = hostState.onAudio != null,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onGoLive = onGoLive,
                    onSeekPreview = onSeekPreview,
                    onSeekCommit = onSeekCommit,
                    onInteractionStart = onInteractionStart,
                    onInteractionEnd = onInteractionEnd,
                    onOpenMenu = openMenuWithFocusReturn,
                    onSubtitles = openSubtitlesWithFocusReturn,
                    onAudio = openAudioWithFocusReturn,
                    onRotate = onRotate,
                    onLock = onLock,
                    onPip = onPip,
                    onFullscreen = onFullscreen,
                )
            }
        }

        if (coordinator.controlsLocked && coordinator.unlockVisible) {
            PlayerCircleAction(
                icon = ChromeIcon.LOCK,
                description = "Unlock player controls",
                onClick = onUnlock,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .testTag("unlock_button"),
            )
        }

        if (playback.isBuffering || coordinator.preparing || playback.network.phase == NetworkPlaybackPhase.RECONNECTING) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("buffering_indicator"))
                val status = when (playback.network.phase) {
                    NetworkPlaybackPhase.RECONNECTING -> "Reconnecting…"
                    NetworkPlaybackPhase.INITIAL_LOADING -> "Loading network media…"
                    NetworkPlaybackPhase.BUFFERING -> "Buffering…"
                    else -> if (coordinator.preparing) "Loading…" else null
                }
                status?.let { Text(it, color = MaxDesignTokens.PlayerText) }
            }
        }

        PlayerHud(coordinator.hud, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun PlayerTopBar(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    fallbackTitle: String,
    localVideoProcessingAvailable: Boolean,
    audioAvailable: Boolean,
    subtitleButtonFocusRequester: FocusRequester,
    audioButtonFocusRequester: FocusRequester,
    decoderButtonFocusRequester: FocusRequester,
    moreButtonFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onDecoder: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaxDesignTokens.PlayerOverlaySoft)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlayerCircleAction(icon = ChromeIcon.BACK, description = "Back to media library", onClick = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = playback.title.ifBlank { fallbackTitle },
                color = MaxDesignTokens.PlayerText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            if (playback.mediaItemCount > 1) {
                Text(
                    text = "${playback.currentMediaItemIndex + 1} / ${playback.mediaItemCount}",
                    color = MaxDesignTokens.PlayerTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (playback.playbackTarget == PlaybackTarget.CAST_DEVICE) {
            PlayerStatusChip(icon = ChromeIcon.CAST, label = "Cast", description = "Playing on Cast receiver", tag = "cast_status_chip")
        }
        if (audioAvailable) {
            PlayerCircleAction(
                icon = ChromeIcon.AUDIO,
                description = if (localVideoProcessingAvailable) "Audio tracks" else "Audio tracks; phone processing unavailable during Cast",
                onClick = onAudio,
                modifier = Modifier.focusRequester(audioButtonFocusRequester).focusable().testTag("audio_button"),
            )
        }
        PlayerCircleAction(
            icon = ChromeIcon.SUBTITLES,
            label = if (playback.subtitles.enabled) "On" else null,
            description = "Subtitle tracks",
            onClick = onSubtitles,
            modifier = Modifier.focusRequester(subtitleButtonFocusRequester).focusable().testTag("subtitle_button"),
        )
        PlayerCircleAction(
            icon = ChromeIcon.DECODER,
            label = if (localVideoProcessingAvailable) decoderCompactLabel(coordinator.decoder.requestedMode) else null,
            description = if (localVideoProcessingAvailable) "Decoder: ${decoderFullLabel(coordinator.decoder.requestedMode)}" else "Decoder controlled by Cast receiver",
            onClick = onDecoder,
            enabled = localVideoProcessingAvailable,
            modifier = Modifier.focusRequester(decoderButtonFocusRequester).focusable().testTag("decoder_button"),
        )
        PlayerCircleAction(
            icon = ChromeIcon.MORE,
            description = "More playback tools",
            onClick = onMore,
            modifier = Modifier.focusRequester(moreButtonFocusRequester).focusable().testTag("more_button"),
        )
    }
}

@Composable
private fun PlayerQuickRail(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    localVideoProcessingAvailable: Boolean,
    audioAvailable: Boolean,
    onOpenMenu: (PlayerMenu) -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onRotate: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.20f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RailAction(ChromeIcon.SPEED, "${formatSpeed(playback.playbackSpeed)}×", "Playback speed", { onOpenMenu(PlayerMenu.SPEED) }, "speed_button", playback.playbackSpeed != 1f)
        if (audioAvailable) RailAction(ChromeIcon.AUDIO, "Audio", "Audio tracks", onAudio, "audio_rail_button")
        RailAction(ChromeIcon.SUBTITLES, "CC", "Subtitles", onSubtitles, modified = playback.subtitles.enabled)
        RailAction(ChromeIcon.DECODER, decoderCompactLabel(coordinator.decoder.requestedMode), "Decoder", { onOpenMenu(PlayerMenu.DECODER) }, enabled = localVideoProcessingAvailable)
        RailAction(ChromeIcon.DISPLAY, "Fit", "Display and aspect ratio", { onOpenMenu(PlayerMenu.DISPLAY) }, "display_button", coordinator.resizeMode != ResizeMode.FIT, localVideoProcessingAvailable)
        RailAction(ChromeIcon.ROTATE, "Rotate", "Rotate display", onRotate, "rotation_button", coordinator.displayRotationDegrees != 0, localVideoProcessingAvailable)
        RailAction(ChromeIcon.PLAYBACK, "Mode", "Repeat and shuffle", { onOpenMenu(PlayerMenu.PLAYBACK) }, "playback_mode_button", playback.shuffleEnabled)
        if (playback.videoTracks.size > 1) {
            RailAction(ChromeIcon.DISPLAY, "${playback.videoTracks.firstOrNull { it.selected }?.height ?: "Q"}p", "Video quality", { onOpenMenu(PlayerMenu.QUALITY) }, "video_quality_button")
        }
        RailAction(ChromeIcon.ORIENTATION, "Orient", "Player orientation", { onOpenMenu(PlayerMenu.ORIENTATION) }, "orientation_button")
        RailAction(ChromeIcon.PIP, "PiP", "Picture in picture", onPip, "pip_button")
        RailAction(if (coordinator.fullscreen) ChromeIcon.EXIT_FULLSCREEN else ChromeIcon.FULLSCREEN, if (coordinator.fullscreen) "Window" else "Full", "Fullscreen", onFullscreen, "fullscreen_button", coordinator.fullscreen)
        RailAction(ChromeIcon.INFO, "Info", "Media information", { onOpenMenu(PlayerMenu.INFO) }, "info_button")
        RailAction(ChromeIcon.MORE, "More", "More playback tools", { onOpenMenu(PlayerMenu.SETTINGS) })
    }
}

@Composable
private fun PlayerBottomBar(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    localVideoProcessingAvailable: Boolean,
    audioAvailable: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoLive: () -> Unit,
    onSeekPreview: (Long, Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    onOpenMenu: (PlayerMenu) -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onRotate: () -> Unit,
    onLock: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var extendedToolsVisible by remember(playback.mediaId) { mutableStateOf(false) }
    val duration = playback.durationMs.coerceAtLeast(0L)
    val fraction = if (duration > 0L) {
        (playback.currentPositionMs.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(playback.mediaId) {
        scrubbing = false
        scrubFraction = 0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaxDesignTokens.PlayerOverlay)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (playback.isLive) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("LIVE", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                val offset = playback.liveOffsetMs
                if (offset != null && offset > 3_000L) {
                    Text("${offset / 1_000L}s behind live", color = MaxDesignTokens.PlayerText)
                    TextButton(onClick = onGoLive, modifier = Modifier.testTag("go_live_button")) { Text("Go Live") }
                }
            }
        }

        val displayPosition = if (scrubbing && duration > 0L) {
            (duration.toDouble() * scrubFraction.toDouble()).roundToLong()
        } else playback.currentPositionMs
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatPlayerTime(displayPosition), color = MaxDesignTokens.PlayerText, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = if (scrubbing) scrubFraction else fraction,
                onValueChange = { value ->
                    if (duration <= 0L) return@Slider
                    if (!scrubbing) onInteractionStart()
                    scrubbing = true
                    scrubFraction = value
                    val target = (duration.toDouble() * value.toDouble()).roundToLong().coerceIn(0L, duration)
                    onSeekPreview(playback.currentPositionMs, target)
                },
                onValueChangeFinished = {
                    if (duration > 0L && scrubbing) {
                        val target = (duration.toDouble() * scrubFraction.toDouble()).roundToLong().coerceIn(0L, duration)
                        onSeekCommit(target)
                    }
                    scrubbing = false
                    onInteractionEnd()
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("seek_bar").semantics {
                    contentDescription = "Seek, ${formatPlayerTime(displayPosition)} of ${formatPlayerTime(duration)}"
                },
            )
            Text(formatPlayerTime(duration), color = MaxDesignTokens.PlayerText, style = MaterialTheme.typography.labelMedium)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCircleAction(icon = ChromeIcon.LOCK, description = "Lock player controls", onClick = onLock, modifier = Modifier.testTag("lock_button"))
            PlayerCircleAction(icon = ChromeIcon.PREVIOUS, description = "Previous video", onClick = onPrevious, modifier = Modifier.testTag("previous_button"), enabled = playback.hasPrevious)
            PlayerCircleAction(
                icon = if (playback.isPlaying) ChromeIcon.PAUSE else if (playback.playbackEnded) ChromeIcon.REPLAY else ChromeIcon.PLAY,
                description = if (playback.isPlaying) "Pause" else if (playback.playbackEnded) "Replay" else "Play",
                onClick = onPlayPause,
                modifier = Modifier.testTag("play_pause_button"),
                prominent = true,
            )
            PlayerCircleAction(icon = ChromeIcon.NEXT, description = "Next video", onClick = onNext, modifier = Modifier.testTag("next_button"), enabled = playback.hasNext)
            PlayerCircleAction(
                icon = ChromeIcon.DISPLAY,
                label = when (coordinator.resizeMode) {
                    ResizeMode.FIT -> "Fit"
                    ResizeMode.FILL -> "Fill"
                    ResizeMode.CROP -> "Crop"
                    ResizeMode.ORIGINAL -> "1:1"
                    else -> "AR"
                },
                description = "Display and aspect ratio",
                onClick = { onOpenMenu(PlayerMenu.DISPLAY) },
                enabled = localVideoProcessingAvailable,
            )
        }

        if (!localVideoProcessingAvailable) {
            Text(
                "Cast receiver controls decoding and video presentation; saved phone processing settings remain unchanged.",
                color = MaxDesignTokens.PlayerTextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("cast_video_processing_unavailable"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { extendedToolsVisible = !extendedToolsVisible },
                colors = ButtonDefaults.textButtonColors(contentColor = MaxDesignTokens.PlayerText),
                modifier = Modifier.sizeIn(minHeight = 48.dp).testTag("tools_toggle_button"),
            ) {
                PlayerGlyph(ChromeIcon.TOOLS, Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (extendedToolsVisible) "Hide tools" else "Tools")
            }
        }

        AnimatedVisibility(visible = extendedToolsVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("extended_tool_rail"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExtendedTool(ChromeIcon.SPEED, "Speed", "${formatSpeed(playback.playbackSpeed)}×", { onOpenMenu(PlayerMenu.SPEED) })
                if (audioAvailable) ExtendedTool(ChromeIcon.AUDIO, "Audio", "Tracks", onAudio)
                ExtendedTool(ChromeIcon.SUBTITLES, "Subtitle", if (playback.subtitles.enabled) "On" else "Off", onSubtitles)
                ExtendedTool(ChromeIcon.DECODER, "Decoder", decoderCompactLabel(coordinator.decoder.requestedMode), { onOpenMenu(PlayerMenu.DECODER) }, enabled = localVideoProcessingAvailable)
                ExtendedTool(ChromeIcon.DISPLAY, "Aspect", coordinator.resizeMode.name.lowercase().replaceFirstChar { it.uppercase() }, { onOpenMenu(PlayerMenu.DISPLAY) }, enabled = localVideoProcessingAvailable)
                ExtendedTool(ChromeIcon.ROTATE, "Rotate", "90°", onRotate, enabled = localVideoProcessingAvailable)
                ExtendedTool(ChromeIcon.PLAYBACK, "Playback", if (playback.shuffleEnabled) "Shuffle" else "Mode", { onOpenMenu(PlayerMenu.PLAYBACK) })
                if (playback.videoTracks.size > 1) {
                    ExtendedTool(ChromeIcon.DISPLAY, "Quality", "${playback.videoTracks.firstOrNull { it.selected }?.height ?: "Q"}p", { onOpenMenu(PlayerMenu.QUALITY) })
                }
                ExtendedTool(ChromeIcon.ORIENTATION, "Orientation", coordinator.orientationMode.name.lowercase().replace('_', ' '), { onOpenMenu(PlayerMenu.ORIENTATION) })
                ExtendedTool(ChromeIcon.PIP, "PiP", "Window", onPip)
                ExtendedTool(if (coordinator.fullscreen) ChromeIcon.EXIT_FULLSCREEN else ChromeIcon.FULLSCREEN, "Fullscreen", if (coordinator.fullscreen) "Exit" else "Enter", onFullscreen)
                ExtendedTool(ChromeIcon.INFO, "Info", "Media", { onOpenMenu(PlayerMenu.INFO) })
                ExtendedTool(ChromeIcon.MORE, "More", "Tools", { onOpenMenu(PlayerMenu.SETTINGS) })
            }
        }
    }
}

@Composable
private fun PlayerCircleAction(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
    icon: ChromeIcon? = null,
    label: String? = null,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (prominent) Color.White.copy(alpha = 0.22f) else MaxDesignTokens.PlayerControl,
            contentColor = MaxDesignTokens.PlayerText,
            disabledContentColor = MaxDesignTokens.PlayerText.copy(alpha = 0.35f),
            disabledContainerColor = MaxDesignTokens.PlayerControl.copy(alpha = 0.35f),
        ),
        contentPadding = PaddingValues(horizontal = if (prominent) 14.dp else 10.dp, vertical = 8.dp),
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = description },
    ) {
        if (icon != null) PlayerGlyph(icon, Modifier.size(if (prominent) 24.dp else 20.dp))
        if (label != null) {
            if (icon != null) Spacer(Modifier.size(5.dp))
            Text(label, maxLines = 1, fontWeight = if (prominent) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PlayerStatusChip(icon: ChromeIcon, label: String, description: String, tag: String) {
    Surface(
        color = MaxDesignTokens.PlayerControl,
        contentColor = MaxDesignTokens.PlayerText,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag(tag).semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerGlyph(icon, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RailAction(
    icon: ChromeIcon,
    label: String,
    description: String,
    onClick: () -> Unit,
    tag: String? = null,
    modified: Boolean = false,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PlayerCircleAction(
            icon = icon,
            label = label,
            description = description + if (modified) ", modified" else "",
            onClick = onClick,
            enabled = enabled,
            modifier = if (tag == null) Modifier else Modifier.testTag(tag),
        )
        if (modified) {
            Canvas(Modifier.size(5.dp)) { drawCircle(Color(0xFF9DC8EE)) }
        }
    }
}

@Composable
private fun ExtendedTool(
    icon: ChromeIcon,
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaxDesignTokens.PlayerText),
        modifier = Modifier.sizeIn(minWidth = 82.dp, minHeight = 56.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PlayerGlyph(icon, Modifier.size(20.dp))
            Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            Text(value, maxLines = 1, color = MaxDesignTokens.PlayerTextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlayerGlyph(icon: ChromeIcon, modifier: Modifier = Modifier) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.09f).coerceAtLeast(1.5f)
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), strokeWidth = stroke, cap = StrokeCap.Round)
        when (icon) {
            ChromeIcon.BACK -> { line(.70f, .18f, .34f, .50f); line(.34f, .50f, .70f, .82f) }
            ChromeIcon.SUBTITLES -> { drawRoundRect(color, Offset(w*.10f,h*.20f), androidx.compose.ui.geometry.Size(w*.80f,h*.60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*.10f), style = style); line(.28f,.58f,.42f,.58f); line(.58f,.58f,.72f,.58f) }
            ChromeIcon.AUDIO -> { line(.22f,.40f,.38f,.40f); line(.38f,.40f,.55f,.25f); line(.55f,.25f,.55f,.75f); line(.55f,.75f,.38f,.60f); line(.38f,.60f,.22f,.60f); drawArc(color,-55f,110f,false,Offset(w*.48f,h*.28f),androidx.compose.ui.geometry.Size(w*.36f,h*.44f),style=style) }
            ChromeIcon.MORE -> { drawCircle(color,w*.07f,Offset(w*.25f,h*.50f)); drawCircle(color,w*.07f,Offset(w*.50f,h*.50f)); drawCircle(color,w*.07f,Offset(w*.75f,h*.50f)) }
            ChromeIcon.ROTATE, ChromeIcon.REPLAY -> { drawArc(color,-55f,285f,false,Offset(w*.16f,h*.16f),androidx.compose.ui.geometry.Size(w*.68f,h*.68f),style=style); val p=Path().apply{moveTo(w*.72f,h*.10f);lineTo(w*.88f,h*.20f);lineTo(w*.70f,h*.29f);close()}; drawPath(p,color) }
            ChromeIcon.PLAYBACK -> { line(.20f,.30f,.72f,.30f); line(.72f,.30f,.60f,.20f); line(.72f,.30f,.60f,.40f); line(.80f,.70f,.28f,.70f); line(.28f,.70f,.40f,.60f); line(.28f,.70f,.40f,.80f) }
            ChromeIcon.ORIENTATION -> { drawRoundRect(color,Offset(w*.26f,h*.10f),androidx.compose.ui.geometry.Size(w*.48f,h*.80f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.06f),style=style); line(.43f,.78f,.57f,.78f) }
            ChromeIcon.PIP -> { drawRect(color,Offset(w*.10f,h*.18f),androidx.compose.ui.geometry.Size(w*.80f,h*.64f),style=style); drawRect(color,Offset(w*.48f,h*.48f),androidx.compose.ui.geometry.Size(w*.30f,h*.22f),style=style) }
            ChromeIcon.FULLSCREEN -> { line(.12f,.38f,.12f,.12f); line(.12f,.12f,.38f,.12f); line(.62f,.12f,.88f,.12f); line(.88f,.12f,.88f,.38f); line(.88f,.62f,.88f,.88f); line(.88f,.88f,.62f,.88f); line(.38f,.88f,.12f,.88f); line(.12f,.88f,.12f,.62f) }
            ChromeIcon.EXIT_FULLSCREEN -> { line(.12f,.38f,.38f,.38f); line(.38f,.38f,.38f,.12f); line(.62f,.12f,.62f,.38f); line(.62f,.38f,.88f,.38f); line(.88f,.62f,.62f,.62f); line(.62f,.62f,.62f,.88f); line(.38f,.88f,.38f,.62f); line(.38f,.62f,.12f,.62f) }
            ChromeIcon.LOCK -> { drawRoundRect(color,Offset(w*.24f,h*.42f),androidx.compose.ui.geometry.Size(w*.52f,h*.42f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.07f),style=style); drawArc(color,180f,180f,false,Offset(w*.32f,h*.16f),androidx.compose.ui.geometry.Size(w*.36f,h*.50f),style=style) }
            ChromeIcon.PREVIOUS -> { line(.25f,.20f,.25f,.80f); val p=Path().apply{moveTo(w*.72f,h*.20f);lineTo(w*.36f,h*.50f);lineTo(w*.72f,h*.80f);close()}; drawPath(p,color) }
            ChromeIcon.PLAY -> { val p=Path().apply{moveTo(w*.32f,h*.20f);lineTo(w*.78f,h*.50f);lineTo(w*.32f,h*.80f);close()}; drawPath(p,color) }
            ChromeIcon.PAUSE -> { drawRoundRect(color,Offset(w*.28f,h*.20f),androidx.compose.ui.geometry.Size(w*.14f,h*.60f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.04f)); drawRoundRect(color,Offset(w*.58f,h*.20f),androidx.compose.ui.geometry.Size(w*.14f,h*.60f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.04f)) }
            ChromeIcon.NEXT -> { line(.75f,.20f,.75f,.80f); val p=Path().apply{moveTo(w*.28f,h*.20f);lineTo(w*.64f,h*.50f);lineTo(w*.28f,h*.80f);close()}; drawPath(p,color) }
            ChromeIcon.DISPLAY -> { drawRoundRect(color,Offset(w*.10f,h*.20f),androidx.compose.ui.geometry.Size(w*.80f,h*.55f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.06f),style=style); line(.40f,.86f,.60f,.86f); line(.50f,.75f,.50f,.86f) }
            ChromeIcon.INFO -> { drawCircle(color,w*.38f,Offset(w*.50f,h*.50f),style=style); drawCircle(color,w*.055f,Offset(w*.50f,h*.32f)); line(.50f,.46f,.50f,.68f) }
            ChromeIcon.DECODER -> { drawRoundRect(color,Offset(w*.16f,h*.20f),androidx.compose.ui.geometry.Size(w*.68f,h*.60f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.08f),style=style); line(.28f,.10f,.28f,.20f); line(.50f,.10f,.50f,.20f); line(.72f,.10f,.72f,.20f); line(.28f,.80f,.28f,.90f); line(.50f,.80f,.50f,.90f); line(.72f,.80f,.72f,.90f) }
            ChromeIcon.SPEED -> { drawArc(color,180f,180f,false,Offset(w*.14f,h*.24f),androidx.compose.ui.geometry.Size(w*.72f,h*.72f),style=style); line(.50f,.60f,.72f,.38f) }
            ChromeIcon.CAST -> { drawRoundRect(color,Offset(w*.16f,h*.18f),androidx.compose.ui.geometry.Size(w*.70f,h*.56f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(w*.05f),style=style); drawArc(color,270f,90f,false,Offset(w*.08f,h*.58f),androidx.compose.ui.geometry.Size(w*.22f,h*.22f),style=style); drawArc(color,270f,90f,false,Offset(w*.08f,h*.46f),androidx.compose.ui.geometry.Size(w*.40f,h*.40f),style=style); drawCircle(color,w*.045f,Offset(w*.12f,h*.84f)) }
            ChromeIcon.TOOLS -> { line(.18f,.28f,.82f,.28f); line(.18f,.50f,.82f,.50f); line(.18f,.72f,.82f,.72f); drawCircle(color,w*.08f,Offset(w*.36f,h*.28f)); drawCircle(color,w*.08f,Offset(w*.66f,h*.50f)); drawCircle(color,w*.08f,Offset(w*.46f,h*.72f)) }
        }
    }
}

@Composable
fun PlayerHud(hud: PlayerHudState, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = hud,
        modifier = modifier,
        label = "player_hud_transition",
    ) { targetHud ->
        if (targetHud != PlayerHudState.Hidden) {
            val text = when (targetHud) {
                PlayerHudState.Hidden -> ""
                is PlayerHudState.Seek -> {
                    val delta = targetHud.targetMs - targetHud.fromMs
                    val sign = if (delta >= 0L) "+" else "−"
                    "$sign${formatPlayerTime(kotlin.math.abs(delta))}\n${formatPlayerTime(targetHud.fromMs)} → ${formatPlayerTime(targetHud.targetMs)}"
                }
                is PlayerHudState.Brightness -> "Brightness\n${(targetHud.fraction * 100f).toInt()}%"
                is PlayerHudState.Volume -> "Volume\n${(targetHud.fraction * 100f).toInt()}%"
                is PlayerHudState.Zoom -> "Zoom\n${(targetHud.scale * 100f).toInt()}%"
            }
            Surface(
                modifier = Modifier.testTag("player_hud"),
                color = MaxDesignTokens.PlayerOverlay,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = text,
                    color = MaxDesignTokens.PlayerText,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

fun formatPlayerTime(ms: Long): String {
    val secondsTotal = ms.coerceAtLeast(0L) / 1000L
    val h = secondsTotal / 3600L
    val m = (secondsTotal % 3600L) / 60L
    val s = secondsTotal % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSpeed(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.2f".format(value).trimEnd('0')

private fun decoderCompactLabel(mode: DecoderMode): String = when (mode) {
    DecoderMode.AUTO -> "Auto"
    DecoderMode.HARDWARE -> "HW"
    DecoderMode.ENHANCED_HARDWARE -> "EHW"
    DecoderMode.SOFTWARE -> "SW"
}

private fun decoderFullLabel(mode: DecoderMode): String = when (mode) {
    DecoderMode.AUTO -> "Auto"
    DecoderMode.HARDWARE -> "Hardware"
    DecoderMode.ENHANCED_HARDWARE -> "Enhanced Hardware"
    DecoderMode.SOFTWARE -> "Software"
}

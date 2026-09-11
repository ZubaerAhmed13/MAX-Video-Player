package com.zubaer.maxvideoplayer.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.feature.network.model.NetworkPlaybackPhase
import com.zubaer.maxvideoplayer.ui.MaxDesignTokens
import kotlin.math.roundToLong

private enum class PlayerPanelFocusReturn { SUBTITLE, DECODER, MORE }

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
    val subtitleButtonFocusRequester = remember { FocusRequester() }
    val decoderButtonFocusRequester = remember { FocusRequester() }
    val moreButtonFocusRequester = remember { FocusRequester() }
    var pendingFocusReturn by remember { mutableStateOf<PlayerPanelFocusReturn?>(null) }
    val controlsFocusable = coordinator.controlsVisible && !coordinator.controlsLocked

    val openSubtitlesWithFocusReturn: () -> Unit = {
        pendingFocusReturn = PlayerPanelFocusReturn.SUBTITLE
        onSubtitles()
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
        coordinator.activeMenu,
        controlsFocusable,
        pendingFocusReturn,
    ) {
        if (!controlsFocusable) return@LaunchedEffect
        val target = when (pendingFocusReturn) {
            PlayerPanelFocusReturn.SUBTITLE -> if (!subtitlePanelVisible) subtitleButtonFocusRequester else null
            PlayerPanelFocusReturn.DECODER -> if (coordinator.activeMenu != PlayerMenu.DECODER) decoderButtonFocusRequester else null
            PlayerPanelFocusReturn.MORE -> if (coordinator.activeMenu != PlayerMenu.SETTINGS) moreButtonFocusRequester else null
            null -> null
        }
        if (target != null) {
            runCatching { target.requestFocus() }
            pendingFocusReturn = null
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
                    subtitleButtonFocusRequester = subtitleButtonFocusRequester,
                    decoderButtonFocusRequester = decoderButtonFocusRequester,
                    moreButtonFocusRequester = moreButtonFocusRequester,
                    onBack = onBack,
                    onSubtitles = openSubtitlesWithFocusReturn,
                    onDecoder = { openMenuWithFocusReturn(PlayerMenu.DECODER) },
                    onMore = { openMenuWithFocusReturn(PlayerMenu.SETTINGS) },
                )

                PlayerQuickRail(
                    coordinator = coordinator,
                    playback = playback,
                    localVideoProcessingAvailable = localVideoProcessingAvailable,
                    onOpenMenu = openMenuWithFocusReturn,
                    onSubtitles = openSubtitlesWithFocusReturn,
                    onRotate = onRotate,
                    onPip = onPip,
                    onFullscreen = onFullscreen,
                )

                Spacer(Modifier.weight(1f))

                PlayerBottomBar(
                    coordinator = coordinator,
                    playback = playback,
                    localVideoProcessingAvailable = localVideoProcessingAvailable,
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
                    onRotate = onRotate,
                    onLock = onLock,
                    onPip = onPip,
                    onFullscreen = onFullscreen,
                )
            }
        }

        if (coordinator.controlsLocked && coordinator.unlockVisible) {
            PlayerCircleAction(
                label = "Unlock",
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
    subtitleButtonFocusRequester: FocusRequester,
    decoderButtonFocusRequester: FocusRequester,
    moreButtonFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onSubtitles: () -> Unit,
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
        PlayerCircleAction("‹", "Back to media library", onBack)
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
        PlayerCircleAction(
            label = if (!playback.subtitles.enabled) "CC" else "CC•",
            description = "Subtitle tracks",
            onClick = onSubtitles,
            modifier = Modifier.focusRequester(subtitleButtonFocusRequester).testTag("subtitle_button"),
        )
        PlayerCircleAction(
            label = if (localVideoProcessingAvailable) decoderCompactLabel(coordinator.decoder.requestedMode) else "Cast",
            description = if (localVideoProcessingAvailable) "Decoder: ${decoderFullLabel(coordinator.decoder.requestedMode)}" else "Decoder controlled by Cast receiver",
            onClick = onDecoder,
            enabled = localVideoProcessingAvailable,
            modifier = Modifier.focusRequester(decoderButtonFocusRequester).testTag("decoder_button"),
        )
        PlayerCircleAction(
            label = "⋮",
            description = "More playback tools",
            onClick = onMore,
            modifier = Modifier.focusRequester(moreButtonFocusRequester).testTag("more_button"),
        )
    }
}

@Composable
private fun PlayerQuickRail(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    localVideoProcessingAvailable: Boolean,
    onOpenMenu: (PlayerMenu) -> Unit,
    onSubtitles: () -> Unit,
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
        RailAction("${formatSpeed(playback.playbackSpeed)}×", "Playback speed", { onOpenMenu(PlayerMenu.SPEED) }, "speed_button", playback.playbackSpeed != 1f)
        RailAction("CC", "Subtitles", onSubtitles, modified = playback.subtitles.enabled)
        RailAction("Dec", "Decoder", { onOpenMenu(PlayerMenu.DECODER) }, enabled = localVideoProcessingAvailable)
        RailAction("Fit", "Display and aspect ratio", { onOpenMenu(PlayerMenu.DISPLAY) }, "display_button", coordinator.resizeMode != ResizeMode.FIT, localVideoProcessingAvailable)
        RailAction("↻", "Rotate display", onRotate, "rotation_button", coordinator.displayRotationDegrees != 0, localVideoProcessingAvailable)
        RailAction("Mode", "Repeat and shuffle", { onOpenMenu(PlayerMenu.PLAYBACK) }, "playback_mode_button", playback.shuffleEnabled)
        if (playback.videoTracks.size > 1) {
            RailAction("${playback.videoTracks.firstOrNull { it.selected }?.height ?: "Q"}p", "Video quality", { onOpenMenu(PlayerMenu.QUALITY) }, "video_quality_button")
        }
        RailAction("Orient", "Player orientation", { onOpenMenu(PlayerMenu.ORIENTATION) }, "orientation_button")
        RailAction("PiP", "Picture in picture", onPip, "pip_button")
        RailAction(if (coordinator.fullscreen) "Window" else "Full", "Fullscreen", onFullscreen, "fullscreen_button", coordinator.fullscreen)
        RailAction("More", "More playback tools", { onOpenMenu(PlayerMenu.SETTINGS) })
    }
}

@Composable
private fun PlayerBottomBar(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    localVideoProcessingAvailable: Boolean,
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
            PlayerCircleAction("Lock", "Lock player controls", onLock, Modifier.testTag("lock_button"))
            PlayerCircleAction("|‹", "Previous video", onPrevious, Modifier.testTag("previous_button"), playback.hasPrevious)
            PlayerCircleAction(
                label = if (playback.isPlaying) "❚❚" else if (playback.playbackEnded) "↺" else "▶",
                description = if (playback.isPlaying) "Pause" else if (playback.playbackEnded) "Replay" else "Play",
                onClick = onPlayPause,
                modifier = Modifier.testTag("play_pause_button"),
                prominent = true,
            )
            PlayerCircleAction("›|", "Next video", onNext, Modifier.testTag("next_button"), playback.hasNext)
            PlayerCircleAction(
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
                Text(if (extendedToolsVisible) "Hide tools" else "Tools")
            }
        }

        AnimatedVisibility(visible = extendedToolsVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("extended_tool_rail"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExtendedTool("Speed", "${formatSpeed(playback.playbackSpeed)}×", { onOpenMenu(PlayerMenu.SPEED) })
                ExtendedTool("Subtitle", if (playback.subtitles.enabled) "On" else "Off", onSubtitles)
                ExtendedTool("Decoder", decoderCompactLabel(coordinator.decoder.requestedMode), { onOpenMenu(PlayerMenu.DECODER) }, enabled = localVideoProcessingAvailable)
                ExtendedTool("Aspect", coordinator.resizeMode.name.lowercase().replaceFirstChar { it.uppercase() }, { onOpenMenu(PlayerMenu.DISPLAY) }, enabled = localVideoProcessingAvailable)
                ExtendedTool("Rotate", "90°", onRotate, enabled = localVideoProcessingAvailable)
                ExtendedTool("Orientation", coordinator.orientationMode.name.lowercase().replace('_', ' '), { onOpenMenu(PlayerMenu.ORIENTATION) })
                ExtendedTool("PiP", "Window", onPip)
                ExtendedTool("Fullscreen", if (coordinator.fullscreen) "Exit" else "Enter", onFullscreen)
            }
        }
    }
}

@Composable
private fun PlayerCircleAction(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (prominent) 18.dp else 11.dp, vertical = 8.dp),
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = description },
    ) {
        Text(label, maxLines = 1, fontWeight = if (prominent) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun RailAction(
    label: String,
    description: String,
    onClick: () -> Unit,
    tag: String? = null,
    modified: Boolean = false,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PlayerCircleAction(
            label = label,
            description = description + if (modified) ", modified" else "",
            onClick = onClick,
            enabled = enabled,
            modifier = if (tag == null) Modifier else Modifier.testTag(tag),
        )
        if (modified) Text("•", color = Color(0xFF9DC8EE), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ExtendedTool(
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaxDesignTokens.PlayerText),
        modifier = Modifier.sizeIn(minWidth = 76.dp, minHeight = 48.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            Text(value, maxLines = 1, color = MaxDesignTokens.PlayerTextSecondary, style = MaterialTheme.typography.labelSmall)
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
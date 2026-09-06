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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import kotlin.math.roundToLong

@Composable
fun PlayerControlsOverlay(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    fallbackTitle: String,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
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
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = coordinator.controlsVisible && !coordinator.controlsLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back to media library" },
                    ) { Text("Back") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playback.title.ifBlank { fallbackTitle },
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (playback.mediaItemCount > 1) {
                            Text(
                                text = "${playback.currentMediaItemIndex + 1} / ${playback.mediaItemCount}",
                                color = Color.White.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    TextButton(onClick = { onOpenMenu(PlayerMenu.INFO) }) { Text("Info") }
                    TextButton(onClick = { onOpenMenu(PlayerMenu.SETTINGS) }) { Text("More") }
                }

                Spacer(Modifier.weight(1f))

                PlayerBottomBar(
                    coordinator = coordinator,
                    playback = playback,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeekPreview = onSeekPreview,
                    onSeekCommit = onSeekCommit,
                    onInteractionStart = onInteractionStart,
                    onInteractionEnd = onInteractionEnd,
                    onOpenMenu = onOpenMenu,
                    onRotate = onRotate,
                    onLock = onLock,
                    onPip = onPip,
                    onFullscreen = onFullscreen,
                )
            }
        }

        if (coordinator.controlsLocked && coordinator.unlockVisible) {
            Button(
                onClick = onUnlock,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .testTag("unlock_button")
                    .semantics { contentDescription = "Unlock player controls" },
            ) { Text("Unlock") }
        }

        if (playback.isBuffering || coordinator.preparing) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).testTag("buffering_indicator"),
            )
        }

        PlayerHud(coordinator.hud, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun PlayerBottomBar(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekPreview: (Long, Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onInteractionStart: () -> Unit,
    onInteractionEnd: () -> Unit,
    onOpenMenu: (PlayerMenu) -> Unit,
    onRotate: () -> Unit,
    onLock: () -> Unit,
    onPip: () -> Unit,
    onFullscreen: () -> Unit,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
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
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
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
            modifier = Modifier.fillMaxWidth().testTag("seek_bar"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val displayPosition = if (scrubbing && duration > 0L) {
                (duration.toDouble() * scrubFraction.toDouble()).roundToLong()
            } else playback.currentPositionMs
            Text(formatPlayerTime(displayPosition), color = Color.White)
            Text(formatPlayerTime(duration), color = Color.White)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPrevious,
                enabled = playback.hasPrevious,
                modifier = Modifier.testTag("previous_button").semantics { contentDescription = "Previous video" },
            ) { Text("Prev") }
            Button(
                onClick = onPlayPause,
                modifier = Modifier.testTag("play_pause_button").semantics { contentDescription = if (playback.isPlaying) "Pause" else "Play" },
            ) { Text(if (playback.isPlaying) "Pause" else if (playback.playbackEnded) "Replay" else "Play") }
            Button(
                onClick = onNext,
                enabled = playback.hasNext,
                modifier = Modifier.testTag("next_button").semantics { contentDescription = "Next video" },
            ) { Text("Next") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onOpenMenu(PlayerMenu.SPEED) }, modifier = Modifier.testTag("speed_button")) {
                Text("${formatSpeed(playback.playbackSpeed)}×")
            }
            TextButton(onClick = { onOpenMenu(PlayerMenu.PLAYBACK) }, modifier = Modifier.testTag("playback_mode_button")) { Text("Mode") }
            TextButton(onClick = { onOpenMenu(PlayerMenu.DISPLAY) }, modifier = Modifier.testTag("display_button")) { Text("Display") }
            TextButton(onClick = onRotate, modifier = Modifier.testTag("rotation_button")) { Text("Rotate") }
            TextButton(onClick = { onOpenMenu(PlayerMenu.ORIENTATION) }, modifier = Modifier.testTag("orientation_button")) { Text("Orient") }
            TextButton(onClick = onLock, modifier = Modifier.testTag("lock_button")) { Text("Lock") }
            TextButton(onClick = onPip, modifier = Modifier.testTag("pip_button")) { Text("PiP") }
            TextButton(onClick = onFullscreen, modifier = Modifier.testTag("fullscreen_button")) { Text(if (coordinator.fullscreen) "Window" else "Full") }
            Spacer(Modifier.width(4.dp))
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
                color = Color.Black.copy(alpha = 0.74f),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = text,
                    color = Color.White,
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

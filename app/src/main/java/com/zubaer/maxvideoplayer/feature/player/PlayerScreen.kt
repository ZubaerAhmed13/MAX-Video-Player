package com.zubaer.maxvideoplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.PlaybackError
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import kotlin.math.roundToLong

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun PlayerScreen(
    media: AppMedia,
    viewModel: PlayerViewModel,
    playbackConnection: PlaybackConnection,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    val coordinator by viewModel.state.collectAsStateWithLifecycle()
    val playback by playbackConnection.state.collectAsStateWithLifecycle()
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var fullscreen by remember { mutableStateOf(false) }

    coordinator.resumePositionMs?.let { position ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Resume playback?") },
            text = { Text("Continue from ${formatTime(position)} or start from the beginning.") },
            confirmButton = { Button(onClick = viewModel::resume) { Text("Resume") } },
            dismissButton = { TextButton(onClick = viewModel::startOver) { Text("Start over") } },
        )
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                text = playback.title.ifBlank { media.title },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnterPip) { Text("PiP") }
            TextButton(onClick = {
                fullscreen = !fullscreen
                onFullscreenChanged(fullscreen)
            }) { Text(if (fullscreen) "Window" else "Full") }
        }

        Box(Modifier.weight(1f).fillMaxWidth().testTag("video_surface"), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        player = playbackConnection.playerOrNull()
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { it.player = playbackConnection.playerOrNull() },
                modifier = Modifier.fillMaxSize(),
            )
            if (coordinator.preparing || playback.isBuffering) CircularProgressIndicator()
            playback.error?.let { error ->
                Text(errorMessage(error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            }
        }

        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            val duration = playback.durationMs.coerceAtLeast(0L)
            val fraction = if (duration > 0L) (playback.currentPositionMs.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
            Slider(
                value = if (scrubbing) scrubFraction else fraction,
                onValueChange = {
                    scrubbing = true
                    scrubFraction = it
                },
                onValueChangeFinished = {
                    if (duration > 0L) playbackConnection.seekTo((duration.toDouble() * scrubFraction.toDouble()).roundToLong())
                    scrubbing = false
                },
                modifier = Modifier.testTag("seek_bar"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(playback.currentPositionMs), color = Color.White)
                Text(formatTime(duration), color = Color.White)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = playbackConnection::seekToPrevious, enabled = playback.hasPrevious) { Text("Prev") }
                Button(
                    onClick = { if (playback.isPlaying) playbackConnection.pause() else playbackConnection.play() },
                    modifier = Modifier.testTag("play_pause_button"),
                ) { Text(if (playback.isPlaying) "Pause" else "Play") }
                Button(onClick = playbackConnection::seekToNext, enabled = playback.hasNext) { Text("Next") }
                Button(onClick = {
                    val next = when {
                        playback.playbackSpeed < 1f -> 1f
                        playback.playbackSpeed < 1.5f -> 1.5f
                        playback.playbackSpeed < 2f -> 2f
                        else -> 0.75f
                    }
                    playbackConnection.setPlaybackSpeed(next)
                }) { Text("${playback.playbackSpeed}×") }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val secondsTotal = ms.coerceAtLeast(0L) / 1000L
    val h = secondsTotal / 3600L
    val m = (secondsTotal % 3600L) / 60L
    val s = secondsTotal % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun errorMessage(error: PlaybackError): String = when (error) {
    PlaybackError.SourceUnavailable -> "The media source is no longer available."
    PlaybackError.PermissionLost -> "Permission to this media source was lost. Re-open the file."
    PlaybackError.UnsupportedFormat -> "This media container or manifest is not supported by the current decoder path."
    PlaybackError.UnsupportedDecoder -> "This device does not expose a compatible decoder for this stream."
    PlaybackError.MalformedMedia -> "The media appears malformed or corrupted."
    PlaybackError.Network -> "Network playback failed. Check the connection and URL."
    PlaybackError.DecoderInitialization -> "The decoder could not be initialized on this device."
    is PlaybackError.Failure -> "Playback failed (diagnostic code ${error.diagnosticCode ?: -1})."
    PlaybackError.Unknown -> "Playback failed for an unknown reason."
}

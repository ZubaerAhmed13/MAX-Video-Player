package com.zubaer.maxvideoplayer.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState

/**
 * Lightweight queue control backed by the authoritative MediaSession timeline. It deliberately
 * does not maintain a second UI-side queue; previous/next act on the same Cast/local player.
 */
@Composable
fun PlayerQueueDialog(
    playback: PlaybackUiState,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Queue") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("player_queue_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (playback.mediaItemCount > 0) {
                        "Playing ${playback.currentMediaItemIndex + 1} of ${playback.mediaItemCount}"
                    } else {
                        "No queued media"
                    },
                )
                if (playback.title.isNotBlank()) Text(playback.title)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(
                        onClick = onPrevious,
                        enabled = playback.hasPrevious,
                        modifier = Modifier.testTag("queue_previous_button"),
                    ) { Text("Previous") }
                    TextButton(
                        onClick = onNext,
                        enabled = playback.hasNext,
                        modifier = Modifier.testTag("queue_next_button"),
                    ) { Text("Next") }
                }
                Text("Repeat: ${playback.repeatMode.name.lowercase()} · Shuffle: ${if (playback.shuffleEnabled) "on" else "off"}")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

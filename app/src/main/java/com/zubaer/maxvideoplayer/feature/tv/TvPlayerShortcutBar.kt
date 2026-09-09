package com.zubaer.maxvideoplayer.feature.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Deterministic TV-remote shortcut strip for player functions that would otherwise require
 * traversing touch-oriented overflow controls. Every action remains backed by the same player,
 * MediaSession and dialogs used on phones; this layer only makes them directly D-pad reachable.
 */
@Composable
fun TvPlayerShortcutBar(
    localVideoProcessingAvailable: Boolean,
    onSubtitles: () -> Unit,
    onAudio: (() -> Unit)?,
    onDecoder: () -> Unit,
    onQueue: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.78f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("tv_player_shortcuts"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            onClick = onSubtitles,
            modifier = Modifier
                .focusRequester(firstFocus)
                .testTag("tv_subtitle_button")
                .semantics { contentDescription = "TV subtitles" },
        ) { Text("Subtitles") }
        TextButton(
            onClick = { onAudio?.invoke() },
            enabled = onAudio != null,
            modifier = Modifier
                .testTag("tv_audio_button")
                .semantics { contentDescription = if (onAudio != null) "TV audio controls" else "TV audio controls unavailable" },
        ) { Text("Audio") }
        TextButton(
            onClick = onDecoder,
            enabled = localVideoProcessingAvailable,
            modifier = Modifier
                .testTag("tv_decoder_button")
                .semantics {
                    contentDescription = if (localVideoProcessingAvailable) "TV decoder controls" else "Decoder controlled by Cast receiver"
                },
        ) { Text(if (localVideoProcessingAvailable) "Decoder" else "Decoder (Cast)") }
        TextButton(
            onClick = onQueue,
            modifier = Modifier
                .testTag("tv_queue_button")
                .semantics { contentDescription = "TV playback queue" },
        ) { Text("Queue") }
        TextButton(
            onClick = onSettings,
            modifier = Modifier
                .testTag("tv_settings_button")
                .semantics { contentDescription = "TV player settings" },
        ) { Text("Settings") }
        if (!localVideoProcessingAvailable) {
            Text(
                "Cast receiver controls phone-only video processing",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(vertical = 12.dp).testTag("tv_cast_processing_notice"),
            )
        }
    }
}

package com.zubaer.maxvideoplayer.feature.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Deterministic TV-remote shortcut strip for player functions that would otherwise require
 * traversing touch-oriented overflow controls. Every action remains backed by the same player,
 * MediaSession and dialogs used on phones; this layer only makes them directly D-pad reachable.
 *
 * The controls intentionally use Compose for TV Material3 rather than phone Material3 buttons.
 * That gives the strip a real TV focus target, D-pad semantics and focused-state behavior instead
 * of trying to force phone controls into a remote-navigation contract.
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
    var firstShortcutFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // The generic animated player overlay and this TV-only shortcut strip are restored by the
        // same controls-visible transition. Reclaim the TV-native first shortcut until its actual
        // focus state remains stable across settled frames, then stop permanently for this bar
        // instance so subsequent user D-pad navigation is never pulled back to Subtitles.
        var stableFocusedFrames = 0
        repeat(60) {
            withFrameNanos { }
            if (firstShortcutFocused) {
                stableFocusedFrames += 1
                if (stableFocusedFrames >= 6) return@LaunchedEffect
            } else {
                stableFocusedFrames = 0
                firstFocus.requestFocus()
            }
        }
    }

    MaterialTheme {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.78f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("tv_player_shortcuts"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSubtitles,
                modifier = Modifier
                    .focusRequester(firstFocus)
                    .onFocusChanged { firstShortcutFocused = it.isFocused }
                    .testTag("tv_subtitle_button")
                    .semantics { contentDescription = "TV subtitles" },
            ) { Text("Subtitles") }
            Button(
                onClick = { onAudio?.invoke() },
                enabled = onAudio != null,
                modifier = Modifier
                    .testTag("tv_audio_button")
                    .semantics { contentDescription = if (onAudio != null) "TV audio controls" else "TV audio controls unavailable" },
            ) { Text("Audio") }
            Button(
                onClick = onDecoder,
                enabled = localVideoProcessingAvailable,
                modifier = Modifier
                    .testTag("tv_decoder_button")
                    .semantics {
                        contentDescription = if (localVideoProcessingAvailable) "TV decoder controls" else "Decoder controlled by Cast receiver"
                    },
            ) { Text(if (localVideoProcessingAvailable) "Decoder" else "Decoder (Cast)") }
            Button(
                onClick = onQueue,
                modifier = Modifier
                    .testTag("tv_queue_button")
                    .semantics { contentDescription = "TV playback queue" },
            ) { Text("Queue") }
            Button(
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
}

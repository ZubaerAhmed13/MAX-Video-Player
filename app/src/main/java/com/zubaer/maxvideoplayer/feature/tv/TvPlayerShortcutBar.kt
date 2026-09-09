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
    var firstShortcutFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // The generic animated player overlay and this TV-only shortcut strip are restored by the
        // same controls-visible transition. A request can be accepted and then be displaced by a
        // later overlay layout/focus pass, so "requestFocus() == true" alone is not a sufficient
        // hand-off guarantee. Keep reclaiming the first shortcut until it has actually remained
        // focused across several consecutive frames. Then stop permanently for this bar instance
        // so subsequent user D-pad navigation is never pulled back to Subtitles.
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
                .onFocusChanged { firstShortcutFocused = it.isFocused }
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

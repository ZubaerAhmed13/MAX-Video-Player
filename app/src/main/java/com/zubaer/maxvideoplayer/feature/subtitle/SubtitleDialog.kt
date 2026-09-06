package com.zubaer.maxvideoplayer.feature.subtitle

import android.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState

@Composable
fun SubtitleDialog(
    playback: PlaybackUiState,
    style: SubtitleStyleState,
    onDismiss: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onAuto: () -> Unit,
    onTrack: (String) -> Unit,
    onLoadExternal: () -> Unit,
    onRemoveExternal: () -> Unit,
    onTextScale: (Float) -> Unit,
    onBottomPadding: (Float) -> Unit,
    onEdgeStyle: (SubtitleEdgeStyle) -> Unit,
    onForegroundColor: (Int) -> Unit,
    onBackgroundColor: (Int) -> Unit,
    onApplyEmbeddedStyles: (Boolean) -> Unit,
    onApplyEmbeddedFontSizes: (Boolean) -> Unit,
    onResetStyle: () -> Unit,
) {
    var textScale by remember(style.textScale) { mutableFloatStateOf(style.textScale) }
    var bottomPadding by remember(style.bottomPaddingFraction) { mutableFloatStateOf(style.bottomPaddingFraction) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("subtitle_dialog"),
        title = { Text("Subtitles & captions") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Show subtitles")
                    Switch(
                        checked = playback.subtitles.enabled,
                        onCheckedChange = onEnabled,
                        modifier = Modifier.testTag("subtitle_enabled_switch"),
                    )
                }

                Text("Track")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onAuto, enabled = playback.subtitles.enabled) { Text("Auto") }
                    playback.subtitles.tracks.forEach { track ->
                        TextButton(
                            onClick = { onTrack(track.key) },
                            enabled = playback.subtitles.enabled && track.supported,
                            modifier = Modifier.testTag("subtitle_track_${track.key}"),
                        ) {
                            val language = track.language?.uppercase()?.let { " · $it" }.orEmpty()
                            val source = if (track.external) " · external" else ""
                            val selected = if (track.selected) "✓ " else ""
                            Text("$selected${track.label}$language$source")
                        }
                    }
                }
                if (playback.subtitles.tracks.isEmpty()) {
                    Text("No embedded or attached subtitle track is currently available.")
                }

                HorizontalDivider()
                Text("External subtitle")
                playback.subtitles.externalLabel?.let { Text("Attached: $it") }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onLoadExternal, modifier = Modifier.testTag("load_external_subtitle")) {
                        Text(if (playback.subtitles.externalAttached) "Replace file" else "Load file")
                    }
                    if (playback.subtitles.externalAttached) {
                        TextButton(onClick = onRemoveExternal, modifier = Modifier.testTag("remove_external_subtitle")) {
                            Text("Remove")
                        }
                    }
                }
                Text("Supported side-loaded text formats: SRT, WebVTT, SSA/ASS and TTML/DFXP. Files stay reference-based; the video is not copied or re-encoded.")

                HorizontalDivider()
                Text("Appearance")
                Text("Text size: ${(textScale * 100f).toInt()}%")
                Slider(
                    value = textScale,
                    onValueChange = { textScale = it },
                    onValueChangeFinished = { onTextScale(textScale) },
                    valueRange = 0.5f..2f,
                    modifier = Modifier.testTag("subtitle_text_size"),
                )

                Text("Bottom margin: ${(bottomPadding * 100f).toInt()}%")
                Slider(
                    value = bottomPadding,
                    onValueChange = { bottomPadding = it },
                    onValueChangeFinished = { onBottomPadding(bottomPadding) },
                    valueRange = 0f..0.35f,
                    modifier = Modifier.testTag("subtitle_bottom_margin"),
                )

                Text("Text colour")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(
                        Color.WHITE to "White",
                        Color.YELLOW to "Yellow",
                        Color.CYAN to "Cyan",
                    ).forEach { (color, label) ->
                        TextButton(onClick = { onForegroundColor(color) }) {
                            Text(if (style.foregroundColor == color) "✓ $label" else label)
                        }
                    }
                }

                Text("Background")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(
                        Color.TRANSPARENT to "None",
                        0x99000000.toInt() to "Dark",
                        Color.BLACK to "Black",
                    ).forEach { (color, label) ->
                        TextButton(onClick = { onBackgroundColor(color) }) {
                            Text(if (style.backgroundColor == color) "✓ $label" else label)
                        }
                    }
                }

                Text("Edge")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    SubtitleEdgeStyle.entries.forEach { edge ->
                        TextButton(onClick = { onEdgeStyle(edge) }) {
                            val label = edge.name.lowercase().replace('_', ' ')
                            Text(if (style.edgeStyle == edge) "✓ $label" else label)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Use embedded cue styling", modifier = Modifier.weight(1f))
                    Switch(checked = style.applyEmbeddedStyles, onCheckedChange = onApplyEmbeddedStyles)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Use embedded cue font sizes", modifier = Modifier.weight(1f))
                    Switch(checked = style.applyEmbeddedFontSizes, onCheckedChange = onApplyEmbeddedFontSizes)
                }

                TextButton(onClick = onResetStyle) { Text("Reset subtitle appearance") }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

package com.zubaer.maxvideoplayer.feature.subtitle

import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Professional subtitle control surface backed by the application's single service-owned playback
 * connection and subtitle repository. File probing is asynchronous and URI/reference based.
 */
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
    val context = LocalContext.current
    val application = context.applicationContext as? MaxVideoPlayerApplication
    val repository = application?.container?.subtitleRepository
    val connection = application?.container?.playbackConnection
    val fallbackPreferences = remember { MutableStateFlow(SubtitlePreferenceState()) }
    val preferences by (repository?.subtitlePreferences ?: fallbackPreferences).collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var textScale by remember(style.textScale) { mutableFloatStateOf(style.textScale) }
    var bottomPadding by remember(style.bottomPaddingFraction) { mutableFloatStateOf(style.bottomPaddingFraction) }
    var relinkAssociationId by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var networkSubtitleUrl by remember { mutableStateOf("") }
    var showNetworkSubtitle by remember { mutableStateOf(false) }

    val externalPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && repository != null && connection != null) {
            repository.persistReadPermission(uri)
            scope.launch {
                val descriptor = repository.describeAsync(uri)
                val availability = repository.probeDescriptor(uri)
                when {
                    descriptor == null -> localError = "That file is not a supported SRT, WebVTT, SSA/ASS or TTML subtitle."
                    availability != SubtitleAvailability.AVAILABLE -> localError =
                        "The subtitle file could not be opened. Check provider permission or choose another file."
                    else -> {
                        connection.attachExternalSubtitle(descriptor)
                        localError = null
                    }
                }
            }
        } else if (uri != null) {
            // Compatibility fallback for isolated previews/tests that do not run under the app container.
            onLoadExternal()
        }
    }

    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val associationId = relinkAssociationId
        relinkAssociationId = null
        if (uri != null && associationId != null && repository != null && connection != null) {
            repository.persistReadPermission(uri)
            scope.launch {
                val descriptor = repository.describeAsync(uri)
                if (descriptor == null || repository.probeDescriptor(uri) != SubtitleAvailability.AVAILABLE) {
                    localError = "That subtitle could not be opened or is not a supported format."
                } else {
                    connection.relinkExternalSubtitle(associationId, descriptor)
                    localError = null
                }
            }
        }
    }

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
                    TextButton(onClick = { onEnabled(false) }, modifier = Modifier.testTag("subtitle_off")) {
                        Text(if (!playback.subtitles.enabled) "✓ Off" else "Off")
                    }
                    TextButton(onClick = onAuto, modifier = Modifier.testTag("subtitle_auto")) { Text("Auto") }
                    playback.subtitles.tracks.forEach { track ->
                        TextButton(
                            onClick = { onTrack(track.key) },
                            enabled = track.supported,
                            modifier = Modifier.testTag("subtitle_track_${track.key}"),
                        ) {
                            val language = track.language?.uppercase(Locale.ROOT)?.let { " · $it" }.orEmpty()
                            val source = if (track.external) " · external" else ""
                            val selected = if (track.selected && playback.subtitles.enabled) "✓ " else ""
                            Text("$selected${track.label}$language$source")
                        }
                    }
                }
                if (playback.subtitles.tracks.isEmpty()) {
                    Text("No embedded or currently available external subtitle track is exposed by this media.")
                }

                playback.subtitles.recoverableError?.let { Text("Subtitle recovery: $it") }
                localError?.let { Text(it) }

                HorizontalDivider()
                Text("External subtitles")
                Button(
                    onClick = {
                        if (repository != null && connection != null) {
                            externalPicker.launch(SubtitleFormatPolicy.supportedPickerMimeTypes())
                        } else {
                            onLoadExternal()
                        }
                    },
                    modifier = Modifier.testTag("load_external_subtitle"),
                ) { Text("Open subtitle file") }
                TextButton(onClick = { showNetworkSubtitle = !showNetworkSubtitle }) { Text("Open subtitle from URL") }
                if (showNetworkSubtitle) {
                    OutlinedTextField(
                        value = networkSubtitleUrl,
                        onValueChange = { networkSubtitleUrl = it },
                        modifier = Modifier.fillMaxWidth().testTag("network_subtitle_url"),
                        label = { Text("HTTPS subtitle URL") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val descriptor = repository?.describeNetworkUrl(networkSubtitleUrl)
                            if (descriptor == null || connection == null) {
                                localError = "Enter a direct HTTP/HTTPS SRT, WebVTT, SSA/ASS, or TTML URL."
                            } else {
                                application?.container?.networkRequestRegistry?.registerUri(descriptor.uri, null, null)
                                connection.attachExternalSubtitle(descriptor)
                                localError = null
                                showNetworkSubtitle = false
                            }
                        },
                        enabled = networkSubtitleUrl.isNotBlank(),
                    ) { Text("Attach URL") }
                }
                Text("SRT, WebVTT, SSA/ASS and TTML/DFXP are side-loaded by URI reference. The video is never copied or re-encoded.")

                playback.subtitles.externalAssociations.forEach { external ->
                    val selected = external.id == playback.subtitles.selectedExternalAssociationId
                    val language = external.language?.let { SubtitleMatcher.humanLanguageName(it) } ?: "Unknown language"
                    Text(
                        buildString {
                            if (selected) append("✓ ")
                            append(external.label)
                            append(" · $language · ${external.format} · ${external.availability}")
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(
                            onClick = { connection?.selectExternalSubtitleAssociation(external.id) },
                            enabled = external.availability == SubtitleAvailability.AVAILABLE.name,
                        ) { Text("Select") }
                        if (external.availability != SubtitleAvailability.AVAILABLE.name) {
                            TextButton(onClick = {
                                relinkAssociationId = external.id
                                relinkPicker.launch(SubtitleFormatPolicy.supportedPickerMimeTypes())
                            }) { Text("Relink") }
                        }
                        TextButton(onClick = { connection?.removeExternalSubtitle(external.id) }) { Text("Remove") }
                    }

                    if (selected) {
                        Text("Text encoding: ${external.encoding.replace('_', '-')}")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            SubtitleEncoding.entries.forEach { encoding ->
                                TextButton(onClick = { connection?.setExternalSubtitleEncoding(external.id, encoding) }) {
                                    Text(if (external.encoding == encoding.name) "✓ ${encodingLabel(encoding)}" else encodingLabel(encoding))
                                }
                            }
                        }
                    }
                }

                if (playback.subtitles.externalAssociations.isNotEmpty()) {
                    TextButton(onClick = onRemoveExternal, modifier = Modifier.testTag("remove_external_subtitle")) {
                        Text("Remove all external subtitles")
                    }
                }

                HorizontalDivider()
                Text("Subtitle synchronization")
                Text("Current delay: ${formatDelay(playback.subtitles.delayMs)}")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(-500L, -100L, -50L, 50L, 100L, 500L).forEach { delta ->
                        TextButton(onClick = { connection?.adjustSubtitleDelay(delta) }) {
                            Text(if (delta > 0) "+${delta}ms" else "${delta}ms")
                        }
                    }
                    TextButton(onClick = { connection?.resetSubtitleDelay() }, modifier = Modifier.testTag("subtitle_delay_reset")) {
                        Text("Reset")
                    }
                }
                Text("Positive values show cues later; negative values show them earlier. Delay is stored per media/selected external association.")

                HorizontalDivider()
                Text("Discovery & language")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Auto-load matching sidecar", modifier = Modifier.weight(1f))
                    Switch(
                        checked = preferences.autoLoadMatching,
                        onCheckedChange = { repository?.setAutoLoadMatching(it) },
                        modifier = Modifier.testTag("subtitle_autoload"),
                    )
                }
                Text("Preferred languages (selection order is priority)")
                Text(
                    preferences.preferredLanguages
                        .mapIndexed { index, code -> "${index + 1}. ${SubtitleMatcher.humanLanguageName(code)}" }
                        .joinToString("  ")
                        .ifBlank { "No preference" },
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    COMMON_LANGUAGES.forEach { (code, label) ->
                        val active = code in preferences.preferredLanguages
                        TextButton(onClick = {
                            val next = if (active) {
                                preferences.preferredLanguages.filterNot { it == code }
                            } else {
                                preferences.preferredLanguages + code
                            }
                            repository?.setPreferredLanguages(next)
                        }) { Text(if (active) "✓ $label" else label) }
                    }
                }

                Text("Default external encoding")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    SubtitleEncoding.entries.forEach { encoding ->
                        TextButton(onClick = { repository?.setDefaultEncoding(encoding) }) {
                            Text(if (preferences.defaultEncoding == encoding) "✓ ${encodingLabel(encoding)}" else encodingLabel(encoding))
                        }
                    }
                }

                HorizontalDivider()
                Text("Appearance")
                Text("Live preview: English · বাংলা · العربية · 日本語")
                Text("Text size: ${(textScale * 100f).toInt()}%")
                Slider(
                    value = textScale,
                    onValueChange = {
                        textScale = it
                        onTextScale(it)
                    },
                    valueRange = 0.5f..2f,
                    modifier = Modifier.testTag("subtitle_text_size"),
                )

                Text("Vertical/bottom margin: ${(bottomPadding * 100f).toInt()}%")
                Slider(
                    value = bottomPadding,
                    onValueChange = {
                        bottomPadding = it
                        onBottomPadding(it)
                    },
                    valueRange = 0f..0.35f,
                    modifier = Modifier.testTag("subtitle_bottom_margin"),
                )

                Text("Text colour")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(
                        Color.WHITE to "White",
                        Color.YELLOW to "Yellow",
                        Color.CYAN to "Cyan",
                        Color.GREEN to "Green",
                    ).forEach { (color, label) ->
                        TextButton(onClick = { onForegroundColor(color) }) {
                            Text(if (style.foregroundColor == color) "✓ $label" else label)
                        }
                    }
                }

                Text("Background")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(
                        Color.TRANSPARENT to "Transparent",
                        0x66000000 to "Semi",
                        0x99000000.toInt() to "Dark",
                        Color.BLACK to "Solid",
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
                            val label = edge.name.lowercase(Locale.ROOT).replace('_', ' ')
                            Text(if (style.edgeStyle == edge) "✓ $label" else label)
                        }
                    }
                }
                Text("Edge colour")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(Color.BLACK to "Black", Color.WHITE to "White").forEach { (color, label) ->
                        TextButton(onClick = { repository?.setEdgeColor(color) }) {
                            Text(if (style.edgeColor == color) "✓ $label" else label)
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

private fun formatDelay(delayMs: Long): String {
    if (delayMs == 0L) return "0.00s"
    val sign = if (delayMs > 0L) "+" else "−"
    return "$sign${"%.2f".format(Locale.US, kotlin.math.abs(delayMs) / 1000.0)}s"
}

private fun encodingLabel(encoding: SubtitleEncoding): String = when (encoding) {
    SubtitleEncoding.AUTO -> "Auto"
    SubtitleEncoding.UTF_8 -> "UTF-8"
    SubtitleEncoding.UTF_16LE -> "UTF-16 LE"
    SubtitleEncoding.UTF_16BE -> "UTF-16 BE"
    SubtitleEncoding.WINDOWS_1252 -> "Windows-1252"
}

private val COMMON_LANGUAGES = listOf(
    "en" to "English",
    "bn" to "Bangla",
    "de" to "German",
    "fr" to "French",
    "es" to "Spanish",
    "ar" to "Arabic",
    "hi" to "Hindi",
    "ja" to "Japanese",
)

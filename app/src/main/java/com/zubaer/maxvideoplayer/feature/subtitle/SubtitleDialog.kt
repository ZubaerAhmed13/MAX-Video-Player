package com.zubaer.maxvideoplayer.feature.subtitle

import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zubaer.maxvideoplayer.MaxVideoPlayerApplication
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.ui.MaxDesignTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Release-hardened subtitle surface. All pre-existing subtitle behavior remains wired to the single
 * application repository and service-owned playback connection; only the presentation changes from
 * a generic modal to the Step-10 translucent right-side panel hierarchy.
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
    val configuration = LocalConfiguration.current
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
    var showStyle by remember { mutableStateOf(false) }
    var showDiscovery by remember { mutableStateOf(false) }
    var showExternal by remember { mutableStateOf(false) }

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

    val panelFraction = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 0.42f else 0.94f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.12f))
            .testTag("subtitle_dialog"),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(panelFraction)
                .fillMaxHeight()
                .safeDrawingPadding(),
            color = MaxDesignTokens.PlayerOverlay,
            contentColor = ComposeColor.White,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Subtitle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onDismiss,
                        colors = releaseTextButtonColors(),
                    ) { Text("Close") }
                }

                HorizontalDivider(color = ComposeColor.White.copy(alpha = 0.18f))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show subtitles", modifier = Modifier.weight(1f))
                    Switch(
                        checked = playback.subtitles.enabled,
                        onCheckedChange = onEnabled,
                        modifier = Modifier.testTag("subtitle_enabled_switch"),
                    )
                }

                Text("Tracks", fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = { onEnabled(false) },
                    modifier = Modifier.fillMaxWidth().testTag("subtitle_off"),
                    colors = releaseTextButtonColors(),
                ) {
                    Text(if (!playback.subtitles.enabled) "◉ Off" else "○ Off", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = onAuto,
                    modifier = Modifier.fillMaxWidth().testTag("subtitle_auto"),
                    colors = releaseTextButtonColors(),
                ) {
                    Text("Auto", modifier = Modifier.fillMaxWidth())
                }

                playback.subtitles.tracks.forEach { track ->
                    val language = track.language?.let { SubtitleMatcher.humanLanguageName(it) }
                    val source = if (track.external) "External" else "Embedded"
                    TextButton(
                        onClick = { onTrack(track.key) },
                        enabled = track.supported,
                        modifier = Modifier.fillMaxWidth().testTag("subtitle_track_${track.key}"),
                        colors = releaseTextButtonColors(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text((if (track.selected && playback.subtitles.enabled) "◉ " else "○ ") + track.label)
                            Text(
                                listOfNotNull(language, source).joinToString(" · "),
                                color = MaxDesignTokens.PlayerTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (playback.subtitles.tracks.isEmpty()) {
                    Text(
                        "No embedded or currently available external subtitle track is exposed by this media.",
                        color = MaxDesignTokens.PlayerTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                playback.subtitles.recoverableError?.let { Text("Subtitle recovery: $it", color = ComposeColor(0xFFFFC7C7)) }
                localError?.let { Text(it, color = ComposeColor(0xFFFFC7C7)) }

                HorizontalDivider(color = ComposeColor.White.copy(alpha = 0.18f))
                TextButton(
                    onClick = {
                        if (repository != null && connection != null) {
                            externalPicker.launch(SubtitleFormatPolicy.supportedPickerMimeTypes())
                        } else {
                            onLoadExternal()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("load_external_subtitle"),
                    colors = releaseTextButtonColors(),
                ) { Text("Open subtitle", modifier = Modifier.fillMaxWidth()) }

                TextButton(
                    onClick = { showNetworkSubtitle = !showNetworkSubtitle },
                    modifier = Modifier.fillMaxWidth(),
                    colors = releaseTextButtonColors(),
                ) { Text("Online / network subtitle", modifier = Modifier.fillMaxWidth()) }
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

                TextButton(
                    onClick = { showExternal = !showExternal },
                    modifier = Modifier.fillMaxWidth(),
                    colors = releaseTextButtonColors(),
                ) { Text(if (showExternal) "Hide external subtitle details" else "External subtitle details", modifier = Modifier.fillMaxWidth()) }
                if (showExternal) {
                    playback.subtitles.externalAssociations.forEach { external ->
                        val selected = external.id == playback.subtitles.selectedExternalAssociationId
                        val language = external.language?.let { SubtitleMatcher.humanLanguageName(it) } ?: "Unknown language"
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text((if (selected) "◉ " else "○ ") + external.label)
                            Text(
                                "$language · ${external.format} · ${external.availability}",
                                color = MaxDesignTokens.PlayerTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                TextButton(
                                    onClick = { connection?.selectExternalSubtitleAssociation(external.id) },
                                    enabled = external.availability == SubtitleAvailability.AVAILABLE.name,
                                    colors = releaseTextButtonColors(),
                                ) { Text("Select") }
                                if (external.availability != SubtitleAvailability.AVAILABLE.name) {
                                    TextButton(
                                        onClick = {
                                            relinkAssociationId = external.id
                                            relinkPicker.launch(SubtitleFormatPolicy.supportedPickerMimeTypes())
                                        },
                                        colors = releaseTextButtonColors(),
                                    ) { Text("Relink") }
                                }
                                TextButton(
                                    onClick = { connection?.removeExternalSubtitle(external.id) },
                                    colors = releaseTextButtonColors(),
                                ) { Text("Remove") }
                            }
                            if (selected) {
                                Text("Encoding: ${external.encoding.replace('_', '-')}", style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    SubtitleEncoding.entries.forEach { encoding ->
                                        TextButton(
                                            onClick = { connection?.setExternalSubtitleEncoding(external.id, encoding) },
                                            colors = releaseTextButtonColors(),
                                        ) {
                                            Text(if (external.encoding == encoding.name) "✓ ${encodingLabel(encoding)}" else encodingLabel(encoding))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (playback.subtitles.externalAssociations.isNotEmpty()) {
                        TextButton(
                            onClick = onRemoveExternal,
                            modifier = Modifier.testTag("remove_external_subtitle"),
                            colors = releaseTextButtonColors(),
                        ) { Text("Remove all external subtitles") }
                    }
                }

                HorizontalDivider(color = ComposeColor.White.copy(alpha = 0.18f))
                Text("Synchronization", fontWeight = FontWeight.SemiBold)
                Text("Current delay: ${formatDelay(playback.subtitles.delayMs)}", color = MaxDesignTokens.PlayerTextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    listOf(-500L, -100L, -50L, 50L, 100L, 500L).forEach { delta ->
                        TextButton(onClick = { connection?.adjustSubtitleDelay(delta) }, colors = releaseTextButtonColors()) {
                            Text(if (delta > 0) "+${delta}ms" else "${delta}ms")
                        }
                    }
                    TextButton(
                        onClick = { connection?.resetSubtitleDelay() },
                        modifier = Modifier.testTag("subtitle_delay_reset"),
                        colors = releaseTextButtonColors(),
                    ) { Text("Reset") }
                }

                HorizontalDivider(color = ComposeColor.White.copy(alpha = 0.18f))
                TextButton(
                    onClick = { showStyle = !showStyle },
                    modifier = Modifier.fillMaxWidth(),
                    colors = releaseTextButtonColors(),
                ) { Text(if (showStyle) "Hide subtitle style" else "Style", modifier = Modifier.fillMaxWidth()) }
                if (showStyle) {
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
                            TextButton(onClick = { onForegroundColor(color) }, colors = releaseTextButtonColors()) {
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
                            TextButton(onClick = { onBackgroundColor(color) }, colors = releaseTextButtonColors()) {
                                Text(if (style.backgroundColor == color) "✓ $label" else label)
                            }
                        }
                    }
                    Text("Edge")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        SubtitleEdgeStyle.entries.forEach { edge ->
                            TextButton(onClick = { onEdgeStyle(edge) }, colors = releaseTextButtonColors()) {
                                val label = edge.name.lowercase(Locale.ROOT).replace('_', ' ')
                                Text(if (style.edgeStyle == edge) "✓ $label" else label)
                            }
                        }
                    }
                    Text("Edge colour")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf(Color.BLACK to "Black", Color.WHITE to "White").forEach { (color, label) ->
                            TextButton(onClick = { repository?.setEdgeColor(color) }, colors = releaseTextButtonColors()) {
                                Text(if (style.edgeColor == color) "✓ $label" else label)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Use embedded cue styling", modifier = Modifier.weight(1f))
                        Switch(checked = style.applyEmbeddedStyles, onCheckedChange = onApplyEmbeddedStyles)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Use embedded cue font sizes", modifier = Modifier.weight(1f))
                        Switch(checked = style.applyEmbeddedFontSizes, onCheckedChange = onApplyEmbeddedFontSizes)
                    }
                    TextButton(onClick = onResetStyle, colors = releaseTextButtonColors()) { Text("Reset subtitle appearance") }
                }

                TextButton(
                    onClick = { showDiscovery = !showDiscovery },
                    modifier = Modifier.fillMaxWidth(),
                    colors = releaseTextButtonColors(),
                ) { Text(if (showDiscovery) "Hide language & encoding" else "Language & encoding", modifier = Modifier.fillMaxWidth()) }
                if (showDiscovery) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-load matching sidecar", modifier = Modifier.weight(1f))
                        Switch(
                            checked = preferences.autoLoadMatching,
                            onCheckedChange = { repository?.setAutoLoadMatching(it) },
                            modifier = Modifier.testTag("subtitle_autoload"),
                        )
                    }
                    Text("Preferred languages")
                    Text(
                        preferences.preferredLanguages
                            .mapIndexed { index, code -> "${index + 1}. ${SubtitleMatcher.humanLanguageName(code)}" }
                            .joinToString("  ")
                            .ifBlank { "No preference" },
                        color = MaxDesignTokens.PlayerTextSecondary,
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        COMMON_LANGUAGES.forEach { (code, label) ->
                            val active = code in preferences.preferredLanguages
                            TextButton(
                                onClick = {
                                    val next = if (active) preferences.preferredLanguages.filterNot { it == code }
                                    else preferences.preferredLanguages + code
                                    repository?.setPreferredLanguages(next)
                                },
                                colors = releaseTextButtonColors(),
                            ) { Text(if (active) "✓ $label" else label) }
                        }
                    }
                    Text("Default external encoding")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        SubtitleEncoding.entries.forEach { encoding ->
                            TextButton(
                                onClick = { repository?.setDefaultEncoding(encoding) },
                                colors = releaseTextButtonColors(),
                            ) {
                                Text(if (preferences.defaultEncoding == encoding) "✓ ${encodingLabel(encoding)}" else encodingLabel(encoding))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun releaseTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = ComposeColor.White,
    disabledContentColor = ComposeColor.White.copy(alpha = 0.35f),
)

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

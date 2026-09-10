package com.zubaer.maxvideoplayer.feature.player

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zubaer.maxvideoplayer.core.device.DeviceCapabilityProvider
import com.zubaer.maxvideoplayer.core.device.DeviceDecoderBackend
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderBackendType
import com.zubaer.maxvideoplayer.ui.MaxDesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PlayerDialogs(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    media: AppMedia,
    onDismissMenu: () -> Unit,
    onSpeed: (Float) -> Unit,
    onRepeatMode: (RepeatMode) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onVideoQualityAuto: () -> Unit,
    onVideoTrack: (String) -> Unit,
    onDecoderMode: (DecoderMode) -> Unit,
    onUseGlobalDecoder: () -> Unit,
    onDefaultDecoderMode: (DecoderMode) -> Unit,
    onRememberDecoderPerVideo: (Boolean) -> Unit,
    onShowDecoderDiagnostics: (Boolean) -> Unit,
    onResetDecoderPreferences: () -> Unit,
    onResize: (ResizeMode) -> Unit,
    onCustomAspect: (Float, Float) -> Boolean,
    onResetZoom: () -> Unit,
    onRotate: () -> Unit,
    onOrientation: (OrientationMode) -> Unit,
    onDoubleTapSeconds: (Int) -> Unit,
    onSensitivity: (GestureSensitivity) -> Unit,
    onHorizontalSeekEnabled: (Boolean) -> Unit,
    onBrightnessEnabled: (Boolean) -> Unit,
    onVolumeEnabled: (Boolean) -> Unit,
    onPinchEnabled: (Boolean) -> Unit,
    onAutoHideMillis: (Long) -> Unit,
    onRememberSpeed: (Boolean) -> Unit,
    onAutoPip: (Boolean) -> Unit,
    onShowTutorial: () -> Unit,
    onDismissTutorial: () -> Unit,
) {
    when (coordinator.activeMenu) {
        PlayerMenu.SPEED -> SpeedDialog(playback.playbackSpeed, onDismissMenu, onSpeed)
        PlayerMenu.PLAYBACK -> PlaybackModeDialog(playback, onDismissMenu, onRepeatMode, onShuffle)
        PlayerMenu.QUALITY -> VideoQualityDialog(playback, onDismissMenu, onVideoQualityAuto, onVideoTrack)
        PlayerMenu.DECODER -> DecoderDialog(
            state = coordinator,
            onDismiss = onDismissMenu,
            onMode = onDecoderMode,
            onUseGlobal = onUseGlobalDecoder,
        )
        PlayerMenu.DISPLAY -> DisplayDialog(coordinator, onDismissMenu, onResize, onCustomAspect, onResetZoom, onRotate)
        PlayerMenu.ORIENTATION -> OrientationDialog(coordinator.orientationMode, onDismissMenu, onOrientation)
        PlayerMenu.SETTINGS -> ReleaseMorePanel(
            coordinator = coordinator,
            playback = playback,
            media = media,
            onDismiss = onDismissMenu,
            onSpeed = onSpeed,
            onRepeatMode = onRepeatMode,
            onShuffle = onShuffle,
            onResize = onResize,
            onResetZoom = onResetZoom,
            onRotate = onRotate,
            onOrientation = onOrientation,
            onDoubleTapSeconds = onDoubleTapSeconds,
            onSensitivity = onSensitivity,
            onHorizontalSeekEnabled = onHorizontalSeekEnabled,
            onBrightnessEnabled = onBrightnessEnabled,
            onVolumeEnabled = onVolumeEnabled,
            onPinchEnabled = onPinchEnabled,
            onAutoHideMillis = onAutoHideMillis,
            onRememberSpeed = onRememberSpeed,
            onAutoPip = onAutoPip,
            onDefaultDecoderMode = onDefaultDecoderMode,
            onRememberDecoderPerVideo = onRememberDecoderPerVideo,
            onShowDecoderDiagnostics = onShowDecoderDiagnostics,
            onResetDecoderPreferences = onResetDecoderPreferences,
            onShowTutorial = onShowTutorial,
        )
        PlayerMenu.INFO -> MediaInfoDialog(media, playback, coordinator, onDismissMenu)
        PlayerMenu.NONE -> Unit
    }
    if (coordinator.tutorialVisible && coordinator.resumePositionMs == null && !coordinator.preparing) {
        GestureTutorialDialog(onDismissTutorial)
    }
}

@Composable
private fun VideoQualityDialog(
    playback: PlaybackUiState,
    onDismiss: () -> Unit,
    onAuto: () -> Unit,
    onTrack: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video quality") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onAuto, modifier = Modifier.fillMaxWidth()) {
                    Text(if (playback.videoQualityAuto) "✓ Auto (adaptive)" else "Auto (adaptive)")
                }
                playback.videoTracks.forEach { track ->
                    val details = listOfNotNull(
                        if (track.width != null && track.height != null) "${track.width} × ${track.height}" else null,
                        track.bitrate?.let { "${it / 1_000} kb/s" },
                        track.codec,
                    ).joinToString(" · ")
                    TextButton(
                        onClick = { onTrack(track.key) },
                        enabled = track.supported,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text((if (!playback.videoQualityAuto && track.selected) "✓ " else "") + details.ifBlank { "Video track" })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SpeedDialog(current: Float, onDismiss: () -> Unit, onSpeed: (Float) -> Unit) {
    var speed by remember(current) { mutableFloatStateOf(current.coerceIn(0.25f, 4f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${"%.2f".format(speed)}×")
                Slider(
                    value = speed,
                    onValueChange = { speed = (it * 20f).roundToInt() / 20f },
                    valueRange = 0.25f..4f,
                    steps = 74,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { preset ->
                        TextButton(onClick = { speed = preset; onSpeed(preset) }) { Text("${preset}×") }
                    }
                }
                Text("Fine adjustment remains available in 0.05× steps from 0.25× to 4.0×.")
            }
        },
        confirmButton = { Button(onClick = { onSpeed(speed); onDismiss() }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PlaybackModeDialog(
    playback: PlaybackUiState,
    onDismiss: () -> Unit,
    onRepeatMode: (RepeatMode) -> Unit,
    onShuffle: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Repeat")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RepeatMode.entries.forEach { mode ->
                        TextButton(onClick = { onRepeatMode(mode) }) {
                            Text(if (playback.repeatMode == mode) "✓ ${mode.name.lowercase()}" else mode.name.lowercase())
                        }
                    }
                }
                SettingSwitch("Shuffle queue", playback.shuffleEnabled, onShuffle)
                Text("Repeat and shuffle operate on the service-owned MediaSession queue.")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

/** Centered dark release modal. Full names are MAX terminology; HW+ is intentionally never used. */
@Composable
private fun DecoderDialog(
    state: PlayerCoordinatorState,
    onDismiss: () -> Unit,
    onMode: (DecoderMode) -> Unit,
    onUseGlobal: () -> Unit,
) {
    val session = state.decoder
    val diagnostics = session.diagnostics
    var detailsExpanded by remember { mutableStateOf(false) }
    var capabilitiesExpanded by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .safeDrawingPadding()
            .testTag("decoder_dialog"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .widthIn(max = 520.dp)
                .fillMaxHeight(0.88f),
            color = MaxDesignTokens.PlayerOverlay,
            contentColor = Color.White,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Select decoder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, colors = playerTextButtonColors()) { Text("Close") }
                }
                Text(
                    when {
                        diagnostics.switching -> "Switching decoder…"
                        diagnostics.activeDecoderName != null -> "Active: ${decoderBackendLabel(diagnostics.effectiveBackend)} — ${diagnostics.activeDecoderName}"
                        else -> diagnostics.statusMessage ?: "Active decoder appears after video initialization."
                    },
                    color = MaxDesignTokens.PlayerTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

                DecoderMode.entries.forEach { mode ->
                    TextButton(
                        onClick = { onMode(mode) },
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                        colors = playerTextButtonColors(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text((if (session.requestedMode == mode) "◉ " else "○ ") + decoderModeLabel(mode), fontWeight = FontWeight.Medium)
                            Text(decoderModeDescription(mode), color = MaxDesignTokens.PlayerTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (session.usingMediaOverride) {
                    TextButton(onClick = onUseGlobal, colors = playerTextButtonColors()) { Text("Use global default") }
                } else {
                    Text("Using global default", color = MaxDesignTokens.PlayerTextSecondary, style = MaterialTheme.typography.bodySmall)
                }

                diagnostics.lastFailure?.let { failure ->
                    Text("Decoder problem: ${failure.message}", color = Color(0xFFFFC7C7))
                    Text("Try Auto, another hardware policy, or Software if the selected mode cannot decode this video.", color = MaxDesignTokens.PlayerTextSecondary)
                }

                TextButton(onClick = { detailsExpanded = !detailsExpanded }, colors = playerTextButtonColors()) {
                    Text(if (detailsExpanded) "Hide decoder information" else "Decoder information")
                }
                if (detailsExpanded) {
                    InfoLine("Requested", decoderModeLabel(diagnostics.requestedMode))
                    InfoLine("Effective", diagnostics.effectiveMode?.let(::decoderModeLabel) ?: "Unknown / inactive")
                    InfoLine("Backend", decoderBackendLabel(diagnostics.effectiveBackend))
                    InfoLine("Decoder", diagnostics.activeDecoderName ?: "None")
                    InfoLine("Hardware accelerated", diagnostics.hardwareAccelerated?.toString() ?: "Unknown")
                    InfoLine("Software only", diagnostics.softwareOnly?.toString() ?: "Unknown")
                    InfoLine("Vendor", diagnostics.vendor?.toString() ?: "Unknown")
                    InfoLine("Secure", diagnostics.secure?.toString() ?: "Unknown")
                    InfoLine("MIME", diagnostics.inputFormat.mimeType ?: "Unknown")
                    InfoLine("Codec string", diagnostics.inputFormat.codecs ?: "Unknown")
                    InfoLine(
                        "Resolution",
                        if (diagnostics.inputFormat.width != null && diagnostics.inputFormat.height != null) {
                            "${diagnostics.inputFormat.width} × ${diagnostics.inputFormat.height}"
                        } else "Unknown",
                    )
                    InfoLine("Frame rate", diagnostics.inputFormat.frameRate?.let { "${"%.2f".format(it)} fps" } ?: "Unknown")
                    InfoLine("Decoder init", diagnostics.decoderInitializationDurationMs?.let { "${it} ms" } ?: "Unknown")
                    InfoLine("Dropped frames", diagnostics.droppedFrames.toString())
                    InfoLine("Fallback events", diagnostics.fallbackCount.toString())
                    if (diagnostics.fallbackHistory.isNotEmpty()) {
                        Text("Fallback history", fontWeight = FontWeight.SemiBold)
                        diagnostics.fallbackHistory.forEach { event ->
                            Text("• ${event.decoderName ?: "candidate"}: ${event.failureCode.name}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                TextButton(onClick = { capabilitiesExpanded = !capabilitiesExpanded }, colors = playerTextButtonColors()) {
                    Text(if (capabilitiesExpanded) "Hide device decoder capabilities" else "Device decoder capabilities")
                }
                if (capabilitiesExpanded) DeviceDecoderCapabilitiesPanel(state)
            }
        }
    }
}

@Composable
private fun DeviceDecoderCapabilitiesPanel(state: PlayerCoordinatorState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember(context.applicationContext) { DeviceCapabilityProvider(context.applicationContext) }
    var refreshedProfile by remember { mutableStateOf(state.decoderCapabilities) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    val profile = refreshedProfile ?: state.decoderCapabilities

    when {
        (state.decoderCapabilitiesLoading || refreshing) && profile == null -> Text("Scanning device decoder capabilities…")
        state.decoderCapabilitiesError != null && profile == null -> Text("Decoder capability scan failed: ${state.decoderCapabilitiesError}")
        profile == null -> Text("Decoder capability inventory is not available yet.")
        else -> {
            Text("${profile.manufacturer} ${profile.model} · API ${profile.apiLevel}")
            InfoLine("ABIs", profile.abis.joinToString().ifBlank { "Unknown" })
            InfoLine("Hardware decoder names", profile.hardwareDecoderNames.size.toString())
            InfoLine("Software decoder names", profile.softwareDecoderNames.size.toString())
            if (profile.unknownDecoderNames.isNotEmpty()) InfoLine("Unclassified decoder names", profile.unknownDecoderNames.size.toString())
            profile.availableVideoDecoders.groupBy { it.mimeType }.toSortedMap().forEach { (mimeType, decoders) ->
                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                Text(decoderMimeLabel(mimeType), fontWeight = FontWeight.SemiBold)
                Text(mimeType, color = MaxDesignTokens.PlayerTextSecondary, style = MaterialTheme.typography.bodySmall)
                decoders.forEach { decoder ->
                    Text("• ${decoder.name} — ${deviceDecoderBackendLabel(decoder.backend)}")
                    decoder.vendor?.let { InfoLine("Vendor codec", it.toString()) }
                    if (decoder.profileLevels.isNotEmpty()) InfoLine("Profiles/levels", decoder.profileLevels.joinToString(limit = 12, truncated = "…"))
                    if (decoder.colorFormats.isNotEmpty()) InfoLine("Color formats", decoder.colorFormats.joinToString(limit = 8, truncated = "…"))
                    val features = buildList {
                        if (decoder.adaptivePlayback) add("adaptive")
                        if (decoder.securePlayback) add("secure")
                        if (decoder.tunneledPlayback) add("tunneled")
                        if (decoder.lowLatency == true) add("low-latency")
                    }
                    if (features.isNotEmpty()) InfoLine("Features", features.joinToString())
                    val supportedTargets = decoder.resolutionTargets.entries.mapNotNull { (label, capability) ->
                        when {
                            capability.supportedAt60Fps == true -> "$label@60"
                            capability.supportedAt30Fps -> "$label@30"
                            else -> null
                        }
                    }
                    if (supportedTargets.isNotEmpty()) InfoLine("Size/rate targets", supportedTargets.joinToString())
                }
            }
            if (state.decoderCapabilitiesLoading || refreshing) Text("Refreshing decoder inventory…")
            (refreshError ?: state.decoderCapabilitiesError)?.let { Text("Last refresh failed: $it", color = Color(0xFFFFC7C7)) }
            TextButton(
                onClick = {
                    if (!refreshing) {
                        refreshing = true
                        refreshError = null
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.Default) { provider.collectDecoderProfile(forceRefresh = true) } }
                            result.onSuccess { refreshedProfile = it }.onFailure { refreshError = it.message ?: it.javaClass.simpleName }
                            refreshing = false
                        }
                    }
                },
                enabled = !refreshing,
                colors = playerTextButtonColors(),
            ) { Text("Refresh decoder inventory") }
            Text("This inventory describes this device only; it is not a universal Android codec-support list.", color = MaxDesignTokens.PlayerTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DisplayDialog(
    state: PlayerCoordinatorState,
    onDismiss: () -> Unit,
    onResize: (ResizeMode) -> Unit,
    onCustomAspect: (Float, Float) -> Boolean,
    onResetZoom: () -> Unit,
    onRotate: () -> Unit,
) {
    var customW by remember(state.customAspectRatio, state.resizeMode) {
        mutableStateOf(if (state.resizeMode == ResizeMode.CUSTOM) state.customAspectRatio.toString() else "16")
    }
    var customH by remember(state.customAspectRatio, state.resizeMode) {
        mutableStateOf(if (state.resizeMode == ResizeMode.CUSTOM) "1" else "9")
    }
    var customError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Display") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Resize / aspect. Fit/Crop preserve source geometry; Fill and forced aspect presets intentionally stretch the display only.")
                listOf(
                    ResizeMode.FIT to "Fit",
                    ResizeMode.FILL to "Fill (stretch)",
                    ResizeMode.CROP to "Crop",
                    ResizeMode.ORIGINAL to "Original / 100%",
                    ResizeMode.ASPECT_16_9 to "16:9",
                    ResizeMode.ASPECT_4_3 to "4:3",
                    ResizeMode.ASPECT_18_9 to "18:9",
                    ResizeMode.ASPECT_21_9 to "21:9",
                ).forEach { (mode, label) ->
                    TextButton(onClick = { onResize(mode) }) { Text(if (state.resizeMode == mode) "✓ $label" else label) }
                }
                HorizontalDivider()
                Text("Custom aspect${if (state.resizeMode == ResizeMode.CUSTOM) " — active ${"%.3f".format(state.customAspectRatio)}:1" else ""}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(customW, { customW = it }, label = { Text("W") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(customH, { customH = it }, label = { Text("H") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                if (customError) Text("Use positive values producing a ratio between 0.2:1 and 5:1, for example 16:9.")
                Button(onClick = { customError = !onCustomAspect(customW.toFloatOrNull() ?: 0f, customH.toFloatOrNull() ?: 0f) }) { Text("Apply custom ratio") }
                HorizontalDivider()
                TextButton(onClick = onResetZoom) { Text("Reset zoom and pan") }
                TextButton(onClick = onRotate) { Text("Rotate display 90°") }
                Text("Manual zoom: ${(state.zoom * 100f).toInt()}%")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun OrientationDialog(current: OrientationMode, onDismiss: () -> Unit, onOrientation: (OrientationMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player orientation") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(
                    OrientationMode.AUTO to "Auto / sensor",
                    OrientationMode.PORTRAIT to "Portrait",
                    OrientationMode.LANDSCAPE to "Landscape",
                    OrientationMode.REVERSE_PORTRAIT to "Reverse portrait",
                    OrientationMode.REVERSE_LANDSCAPE to "Reverse landscape",
                    OrientationMode.LOCK_CURRENT to "Lock current orientation",
                ).forEach { (mode, label) ->
                    TextButton(onClick = { onOrientation(mode); onDismiss() }) { Text(if (current == mode) "✓ $label" else label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private enum class MoreSection { NONE, DISPLAY, SPEED, PLAYBACK, GESTURES, CONTROLS, DECODER, INFO }

/** Right-anchored, vertically scrollable Step-10 More/Tools panel with only real actions. */
@Composable
private fun ReleaseMorePanel(
    coordinator: PlayerCoordinatorState,
    playback: PlaybackUiState,
    media: AppMedia,
    onDismiss: () -> Unit,
    onSpeed: (Float) -> Unit,
    onRepeatMode: (RepeatMode) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onResize: (ResizeMode) -> Unit,
    onResetZoom: () -> Unit,
    onRotate: () -> Unit,
    onOrientation: (OrientationMode) -> Unit,
    onDoubleTapSeconds: (Int) -> Unit,
    onSensitivity: (GestureSensitivity) -> Unit,
    onHorizontalSeekEnabled: (Boolean) -> Unit,
    onBrightnessEnabled: (Boolean) -> Unit,
    onVolumeEnabled: (Boolean) -> Unit,
    onPinchEnabled: (Boolean) -> Unit,
    onAutoHideMillis: (Long) -> Unit,
    onRememberSpeed: (Boolean) -> Unit,
    onAutoPip: (Boolean) -> Unit,
    onDefaultDecoderMode: (DecoderMode) -> Unit,
    onRememberDecoderPerVideo: (Boolean) -> Unit,
    onShowDecoderDiagnostics: (Boolean) -> Unit,
    onResetDecoderPreferences: () -> Unit,
    onShowTutorial: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var section by remember { mutableStateOf(MoreSection.NONE) }
    var customDoubleTapSeconds by remember(coordinator.preferences.doubleTapSeekSeconds) {
        mutableFloatStateOf(coordinator.preferences.doubleTapSeekSeconds.coerceIn(5, 60).toFloat())
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)).testTag("more_panel")) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(if (landscape) 0.44f else 0.94f)
                .fillMaxHeight()
                .safeDrawingPadding(),
            color = MaxDesignTokens.PlayerOverlay,
            contentColor = Color.White,
            tonalElevation = 0.dp,
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("More / Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, colors = playerTextButtonColors()) { Text("Close") }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MoreToolTile("Aspect", coordinator.resizeMode.name.shortLabel(), Modifier.weight(1f)) { section = MoreSection.DISPLAY }
                    MoreToolTile("Speed", "${formatSpeedLabel(playback.playbackSpeed)}×", Modifier.weight(1f)) { section = MoreSection.SPEED }
                    MoreToolTile("Playback", if (playback.shuffleEnabled) "Shuffle" else playback.repeatMode.name.shortLabel(), Modifier.weight(1f)) { section = MoreSection.PLAYBACK }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MoreToolTile("Gestures", "Customize", Modifier.weight(1f)) { section = MoreSection.GESTURES }
                    MoreToolTile("Controls", "Auto-hide", Modifier.weight(1f)) { section = MoreSection.CONTROLS }
                    MoreToolTile("Decoder", decoderModeLabel(coordinator.preferences.defaultDecoderMode), Modifier.weight(1f)) { section = MoreSection.DECODER }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MoreToolTile("Information", "Media", Modifier.weight(1f)) { section = MoreSection.INFO }
                    MoreToolTile("Rotate", "90°", Modifier.weight(1f), onRotate)
                    MoreToolTile("Orientation", coordinator.orientationMode.name.shortLabel(), Modifier.weight(1f)) { onOrientation(OrientationMode.AUTO) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MoreToolTile("PiP", if (coordinator.preferences.autoPip) "Auto on" else "Auto off", Modifier.weight(1f)) { onAutoPip(!coordinator.preferences.autoPip) }
                    MoreToolTile("Tutorial", "Gestures", Modifier.weight(1f)) { onDismiss(); onShowTutorial() }
                    MoreToolTile("Reset zoom", "100%", Modifier.weight(1f), onResetZoom)
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                when (section) {
                    MoreSection.NONE -> Text("Choose a tool above. Advanced settings stay here instead of covering the entire video.", color = MaxDesignTokens.PlayerTextSecondary)
                    MoreSection.DISPLAY -> {
                        Text("Aspect ratio", fontWeight = FontWeight.SemiBold)
                        listOf(
                            ResizeMode.FIT to "Fit",
                            ResizeMode.FILL to "Fill",
                            ResizeMode.CROP to "Crop",
                            ResizeMode.ORIGINAL to "Original",
                            ResizeMode.ASPECT_16_9 to "16:9",
                            ResizeMode.ASPECT_4_3 to "4:3",
                            ResizeMode.ASPECT_18_9 to "18:9",
                            ResizeMode.ASPECT_21_9 to "21:9",
                        ).forEach { (mode, label) ->
                            TextButton(onClick = { onResize(mode) }, colors = playerTextButtonColors()) {
                                Text((if (coordinator.resizeMode == mode) "◉ " else "○ ") + label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        TextButton(onClick = onResetZoom, colors = playerTextButtonColors()) { Text("Reset zoom and pan") }
                    }
                    MoreSection.SPEED -> {
                        Text("Playback speed", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                                TextButton(onClick = { onSpeed(speed) }, colors = playerTextButtonColors()) {
                                    Text(if (playback.playbackSpeed == speed) "◉ ${speed}×" else "${speed}×")
                                }
                            }
                        }
                        SettingSwitchPlayer("Remember playback speed", coordinator.preferences.rememberPlaybackSpeed, onRememberSpeed)
                    }
                    MoreSection.PLAYBACK -> {
                        Text("Repeat", fontWeight = FontWeight.SemiBold)
                        RepeatMode.entries.forEach { mode ->
                            TextButton(onClick = { onRepeatMode(mode) }, colors = playerTextButtonColors()) {
                                Text((if (playback.repeatMode == mode) "◉ " else "○ ") + mode.name.shortLabel(), modifier = Modifier.fillMaxWidth())
                            }
                        }
                        SettingSwitchPlayer("Shuffle queue", playback.shuffleEnabled, onShuffle)
                    }
                    MoreSection.GESTURES -> {
                        Text("Gestures", fontWeight = FontWeight.SemiBold)
                        Text("Sensitivity")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            GestureSensitivity.entries.forEach { value ->
                                TextButton(onClick = { onSensitivity(value) }, colors = playerTextButtonColors()) {
                                    Text(if (coordinator.preferences.gestureSensitivity == value) "✓ ${value.name.shortLabel()}" else value.name.shortLabel())
                                }
                            }
                        }
                        SettingSwitchPlayer("Horizontal swipe seek", coordinator.preferences.horizontalSeekEnabled, onHorizontalSeekEnabled)
                        SettingSwitchPlayer("Left-side brightness", coordinator.preferences.brightnessGestureEnabled, onBrightnessEnabled)
                        SettingSwitchPlayer("Right-side volume", coordinator.preferences.volumeGestureEnabled, onVolumeEnabled)
                        SettingSwitchPlayer("Pinch zoom / pan", coordinator.preferences.pinchZoomEnabled, onPinchEnabled)
                    }
                    MoreSection.CONTROLS -> {
                        Text("Control behavior", fontWeight = FontWeight.SemiBold)
                        Text("Double-tap seek: ${customDoubleTapSeconds.roundToInt()}s")
                        Slider(
                            value = customDoubleTapSeconds,
                            onValueChange = { customDoubleTapSeconds = it.roundToInt().toFloat() },
                            onValueChangeFinished = { onDoubleTapSeconds(customDoubleTapSeconds.roundToInt()) },
                            valueRange = 5f..60f,
                            steps = 54,
                        )
                        Text("Auto-hide")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            listOf(2_000L, 3_000L, 5_000L).forEach { ms ->
                                TextButton(onClick = { onAutoHideMillis(ms) }, colors = playerTextButtonColors()) {
                                    Text(if (coordinator.preferences.autoHideMillis == ms) "◉ ${ms / 1_000}s" else "${ms / 1_000}s")
                                }
                            }
                        }
                        SettingSwitchPlayer("Automatic PiP when leaving player", coordinator.preferences.autoPip, onAutoPip)
                    }
                    MoreSection.DECODER -> {
                        Text("Decoder preferences", fontWeight = FontWeight.SemiBold)
                        Text("Default: ${decoderModeLabel(coordinator.preferences.defaultDecoderMode)}", color = MaxDesignTokens.PlayerTextSecondary)
                        DecoderMode.entries.forEach { mode ->
                            TextButton(onClick = { onDefaultDecoderMode(mode) }, colors = playerTextButtonColors()) {
                                Text((if (coordinator.preferences.defaultDecoderMode == mode) "◉ " else "○ ") + decoderModeLabel(mode), modifier = Modifier.fillMaxWidth())
                            }
                        }
                        SettingSwitchPlayer("Remember decoder per video", coordinator.preferences.rememberDecoderPerVideo, onRememberDecoderPerVideo)
                        SettingSwitchPlayer("Show decoder diagnostics", coordinator.preferences.showDecoderDiagnostics, onShowDecoderDiagnostics)
                        TextButton(onClick = onResetDecoderPreferences, colors = playerTextButtonColors()) { Text("Reset decoder preferences") }
                    }
                    MoreSection.INFO -> {
                        Text("Information", fontWeight = FontWeight.SemiBold)
                        InfoLine("Title", playback.title.ifBlank { media.title })
                        InfoLine("Source", media.sourceType.name)
                        InfoLine("Duration", formatPlayerTime(playback.durationMs.takeIf { it > 0L } ?: media.durationMs ?: 0L))
                        InfoLine("Resolution", if (media.width != null && media.height != null) "${media.width} × ${media.height}" else "Unknown")
                        InfoLine("Frame rate", media.frameRate?.let { "${"%.2f".format(it)} fps" } ?: "Unknown")
                        InfoLine("Video codec", media.videoCodec ?: coordinator.decoder.diagnostics.inputFormat.mimeType ?: "Unknown")
                        InfoLine("Audio codec", media.audioCodec ?: "Unknown")
                        InfoLine("Requested decoder", decoderModeLabel(coordinator.decoder.requestedMode))
                        InfoLine("Active decoder", coordinator.decoder.diagnostics.activeDecoderName ?: "Inactive / not initialized")
                        if (media.sourceType == MediaSourceType.NETWORK) {
                            InfoLine("Protocol", playback.network.protocol?.name ?: "Unknown")
                            InfoLine("Server", playback.network.host ?: "Unknown")
                            InfoLine("Connection", playback.network.connectionState.name.replace('_', ' '))
                            InfoLine("URL", playback.network.sanitizedUri ?: "Unavailable")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreToolTile(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minHeight = 78.dp),
        colors = playerTextButtonColors(containerColor = MaxDesignTokens.PlayerControl),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.10f)) {
                Text("•", modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = Color.White)
            }
            Text(title, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(subtitle, color = MaxDesignTokens.PlayerTextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

@Composable
private fun SettingSwitchPlayer(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun MediaInfoDialog(media: AppMedia, playback: PlaybackUiState, coordinator: PlayerCoordinatorState, onDismiss: () -> Unit) {
    val diagnostics = coordinator.decoder.diagnostics
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media information") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoLine("Title", playback.title.ifBlank { media.title })
                InfoLine("Resolution", if (media.width != null && media.height != null) "${media.width} × ${media.height}" else "Unknown")
                InfoLine("Duration", formatPlayerTime(playback.durationMs.takeIf { it > 0L } ?: media.durationMs ?: 0L))
                InfoLine("Video codec", media.videoCodec ?: diagnostics.inputFormat.mimeType ?: "Unknown")
                InfoLine("Audio codec", media.audioCodec ?: "Unknown")
                InfoLine("Frame rate", media.frameRate?.let { "${"%.2f".format(it)} fps" } ?: diagnostics.inputFormat.frameRate?.let { "${"%.2f".format(it)} fps" } ?: "Unknown")
                InfoLine("Source", media.sourceType.name)
                if (media.sourceType == MediaSourceType.NETWORK) {
                    InfoLine("Protocol", playback.network.protocol?.name ?: "Unknown")
                    InfoLine("Server", playback.network.host ?: "Unknown")
                    InfoLine("Connection", playback.network.connectionState.name.replace('_', ' '))
                    InfoLine("Transport", playback.network.transport.name)
                    InfoLine("Seekable", playback.network.seekable?.toString() ?: "Stream-dependent")
                    InfoLine("Buffered", playback.network.bufferedDurationMs?.let(::formatPlayerTime) ?: "Unknown")
                    InfoLine("Estimated bandwidth", playback.network.estimatedBandwidthBitsPerSecond?.let { "${it / 1_000} kb/s" } ?: "Unknown")
                    InfoLine("Retry count", playback.network.retryCount.toString())
                    InfoLine("URL", playback.network.sanitizedUri ?: "Unavailable")
                }
                InfoLine("Requested decoder", decoderModeLabel(coordinator.decoder.requestedMode))
                InfoLine("Active decoder", diagnostics.activeDecoderName ?: "Inactive / not initialized")
                InfoLine("Hardware acceleration", diagnostics.hardwareAccelerated?.toString() ?: "Unknown")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text("$label: $value")
}

@Composable
private fun GestureTutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player gestures") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Swipe left/right — seek")
                Text("Swipe up/down on left — brightness")
                Text("Swipe up/down on right — media volume")
                Text("Double tap left/right — skip")
                Text("Double tap center — play/pause")
                Text("Pinch — zoom")
                Text("Two-finger drag while zoomed — pan")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Got it") } },
    )
}

@Composable
private fun playerTextButtonColors(containerColor: Color = Color.Transparent) = ButtonDefaults.textButtonColors(
    containerColor = containerColor,
    contentColor = Color.White,
    disabledContentColor = Color.White.copy(alpha = 0.35f),
)

private fun decoderModeLabel(mode: DecoderMode): String = when (mode) {
    DecoderMode.AUTO -> "Auto"
    DecoderMode.HARDWARE -> "Hardware"
    DecoderMode.ENHANCED_HARDWARE -> "Enhanced Hardware"
    DecoderMode.SOFTWARE -> "Software"
}

private fun decoderModeDescription(mode: DecoderMode): String = when (mode) {
    DecoderMode.AUTO -> "Automatically chooses the most suitable available decoder and can fall back across compatible backends."
    DecoderMode.HARDWARE -> "Uses the preferred hardware-accelerated decoder only."
    DecoderMode.ENHANCED_HARDWARE -> "Uses compatible hardware decoders with broader hardware-only fallback."
    DecoderMode.SOFTWARE -> "Uses a software decoder only. May use more CPU and battery."
}

private fun decoderBackendLabel(backend: DecoderBackendType?): String = when (backend) {
    DecoderBackendType.HARDWARE -> "Hardware"
    DecoderBackendType.SOFTWARE -> "Software"
    DecoderBackendType.UNKNOWN -> "Unknown"
    null -> "Inactive / unknown"
}

private fun deviceDecoderBackendLabel(backend: DeviceDecoderBackend): String = when (backend) {
    DeviceDecoderBackend.HARDWARE -> "Hardware"
    DeviceDecoderBackend.SOFTWARE -> "Software"
    DeviceDecoderBackend.UNKNOWN -> "Unknown"
}

private fun decoderMimeLabel(mimeType: String): String = when (mimeType.lowercase()) {
    "video/avc" -> "H.264 / AVC"
    "video/hevc" -> "H.265 / HEVC"
    "video/x-vnd.on2.vp8" -> "VP8"
    "video/x-vnd.on2.vp9" -> "VP9"
    "video/av01" -> "AV1"
    "video/mp4v-es" -> "MPEG-4 Part 2"
    "video/mpeg2" -> "MPEG-2 Video"
    "video/3gpp" -> "H.263"
    else -> mimeType
}

private fun String.shortLabel(): String = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun formatSpeedLabel(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.2f".format(value).trimEnd('0')

package com.zubaer.maxvideoplayer.feature.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.DecoderMode
import com.zubaer.maxvideoplayer.core.model.PlaybackUiState
import com.zubaer.maxvideoplayer.core.model.RepeatMode
import com.zubaer.maxvideoplayer.feature.decoder.model.DecoderBackendType
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
        PlayerMenu.DECODER -> DecoderDialog(
            state = coordinator,
            onDismiss = onDismissMenu,
            onMode = onDecoderMode,
            onUseGlobal = onUseGlobalDecoder,
        )
        PlayerMenu.DISPLAY -> DisplayDialog(coordinator, onDismissMenu, onResize, onCustomAspect, onResetZoom, onRotate)
        PlayerMenu.ORIENTATION -> OrientationDialog(coordinator.orientationMode, onDismissMenu, onOrientation)
        PlayerMenu.SETTINGS -> SettingsDialog(
            preferences = coordinator.preferences,
            onDismiss = onDismissMenu,
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
    if (coordinator.tutorialVisible && coordinator.resumePositionMs == null && !coordinator.preparing) GestureTutorialDialog(onDismissTutorial)
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
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { preset ->
                        TextButton(onClick = { speed = preset; onSpeed(preset) }) { Text("${preset}×") }
                    }
                }
                Text("Fine adjustment is available in 0.05× steps from 0.25× to 4.0×.")
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

@Composable
private fun DecoderDialog(
    state: PlayerCoordinatorState,
    onDismiss: () -> Unit,
    onMode: (DecoderMode) -> Unit,
    onUseGlobal: () -> Unit,
) {
    val session = state.decoder
    val diagnostics = session.diagnostics
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Decoder") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text("Requested: ${decoderModeLabel(session.requestedMode)}")
                Text(
                    when {
                        diagnostics.switching -> "Switching decoder…"
                        diagnostics.activeDecoderName != null -> "Active: ${decoderBackendLabel(diagnostics.effectiveBackend)} — ${diagnostics.activeDecoderName}"
                        else -> diagnostics.statusMessage ?: "Active decoder will appear after video initialization."
                    },
                )
                if (session.usingMediaOverride) {
                    TextButton(onClick = onUseGlobal) { Text("Use global default") }
                } else {
                    Text("Using global default")
                }
                HorizontalDivider()
                DecoderMode.entries.forEach { mode ->
                    TextButton(onClick = { onMode(mode) }) {
                        Text(if (session.requestedMode == mode) "✓ ${decoderModeLabel(mode)}" else decoderModeLabel(mode))
                    }
                    Text(decoderModeDescription(mode))
                }
                Text("Software decoding can use significantly more CPU and battery, especially for high-resolution video.")
                diagnostics.lastFailure?.let { failure ->
                    HorizontalDivider()
                    Text("Decoder problem: ${failure.message}")
                    Text("Try Auto, another hardware policy, or Software if the selected mode cannot decode this video.")
                }
                if (state.preferences.showDecoderDiagnostics) {
                    HorizontalDivider()
                    Text("Decoder information")
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
                        Text("Fallback history")
                        diagnostics.fallbackHistory.forEach { event ->
                            Text("• ${event.decoderName ?: "candidate"}: ${event.failureCode.name}")
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
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
                    TextButton(onClick = { onResize(mode) }) {
                        Text(if (state.resizeMode == mode) "✓ $label" else label)
                    }
                }
                HorizontalDivider()
                Text("Custom aspect${if (state.resizeMode == ResizeMode.CUSTOM) " — active ${"%.3f".format(state.customAspectRatio)}:1" else ""}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(customW, { customW = it }, label = { Text("W") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(customH, { customH = it }, label = { Text("H") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                if (customError) Text("Use positive values producing a ratio between 0.2:1 and 5:1, for example 16:9.")
                Button(onClick = {
                    customError = !onCustomAspect(customW.toFloatOrNull() ?: 0f, customH.toFloatOrNull() ?: 0f)
                }) { Text("Apply custom ratio") }
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
                    TextButton(onClick = { onOrientation(mode); onDismiss() }) {
                        Text(if (current == mode) "✓ $label" else label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SettingsDialog(
    preferences: PlayerPreferencesState,
    onDismiss: () -> Unit,
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
    var customDoubleTapSeconds by remember(preferences.doubleTapSeekSeconds) {
        mutableFloatStateOf(preferences.doubleTapSeekSeconds.coerceIn(5, 60).toFloat())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player controls") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Double-tap seek")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(5, 10, 15, 30).forEach { seconds ->
                        TextButton(onClick = {
                            customDoubleTapSeconds = seconds.toFloat()
                            onDoubleTapSeconds(seconds)
                        }) {
                            Text(if (preferences.doubleTapSeekSeconds == seconds) "✓ ${seconds}s" else "${seconds}s")
                        }
                    }
                }
                Text("Custom seek: ${customDoubleTapSeconds.roundToInt()}s")
                Slider(
                    value = customDoubleTapSeconds,
                    onValueChange = { customDoubleTapSeconds = it.roundToInt().toFloat() },
                    onValueChangeFinished = { onDoubleTapSeconds(customDoubleTapSeconds.roundToInt()) },
                    valueRange = 5f..60f,
                    steps = 54,
                )
                Text("Gesture sensitivity")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GestureSensitivity.entries.forEach { value ->
                        TextButton(onClick = { onSensitivity(value) }) {
                            Text(if (preferences.gestureSensitivity == value) "✓ ${value.name.lowercase()}" else value.name.lowercase())
                        }
                    }
                }
                SettingSwitch("Horizontal swipe seek", preferences.horizontalSeekEnabled, onHorizontalSeekEnabled)
                SettingSwitch("Left-side brightness", preferences.brightnessGestureEnabled, onBrightnessEnabled)
                SettingSwitch("Right-side volume", preferences.volumeGestureEnabled, onVolumeEnabled)
                SettingSwitch("Pinch zoom / pan", preferences.pinchZoomEnabled, onPinchEnabled)
                SettingSwitch("Remember playback speed", preferences.rememberPlaybackSpeed, onRememberSpeed)
                SettingSwitch("Automatic PiP when leaving player", preferences.autoPip, onAutoPip)
                Text("Control auto-hide")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(2_000L, 3_000L, 5_000L).forEach { ms ->
                        TextButton(onClick = { onAutoHideMillis(ms) }) {
                            Text(if (preferences.autoHideMillis == ms) "✓ ${ms / 1_000}s" else "${ms / 1_000}s")
                        }
                    }
                }
                HorizontalDivider()
                Text("Decoder")
                Text("Default decoder: ${decoderModeLabel(preferences.defaultDecoderMode)}")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DecoderMode.entries.forEach { mode ->
                        TextButton(onClick = { onDefaultDecoderMode(mode) }) {
                            Text(if (preferences.defaultDecoderMode == mode) "✓ ${decoderModeLabel(mode)}" else decoderModeLabel(mode))
                        }
                    }
                }
                SettingSwitch("Remember decoder per video", preferences.rememberDecoderPerVideo, onRememberDecoderPerVideo)
                SettingSwitch("Show decoder diagnostics", preferences.showDecoderDiagnostics, onShowDecoderDiagnostics)
                TextButton(onClick = onResetDecoderPreferences) { Text("Reset Decoder Preferences") }
                HorizontalDivider()
                TextButton(onClick = { onDismiss(); onShowTutorial() }) { Text("Show gesture tutorial") }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
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
                InfoLine("Requested decoder", decoderModeLabel(coordinator.decoder.requestedMode))
                InfoLine("Active decoder", diagnostics.activeDecoderName ?: "Inactive / not initialized")
                InfoLine("Hardware acceleration", diagnostics.hardwareAccelerated?.toString() ?: "Unknown")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
    )
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
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

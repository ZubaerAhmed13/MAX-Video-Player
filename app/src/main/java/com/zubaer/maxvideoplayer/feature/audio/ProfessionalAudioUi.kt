package com.zubaer.maxvideoplayer.feature.audio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.feature.player.OrientationMode
import com.zubaer.maxvideoplayer.feature.player.PlayerScreen
import com.zubaer.maxvideoplayer.feature.player.PlayerViewModel
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import kotlin.math.roundToInt

@Composable
fun ProfessionalAudioPlayerHost(
    media: AppMedia,
    viewModel: PlayerViewModel,
    playbackConnection: PlaybackConnection,
    subtitleRepository: SubtitleRepository,
    audioRepository: AudioRepository,
    audioController: AudioPlaybackController,
    onBack: () -> Unit,
    onEnterPip: (AppMedia) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    onOrientationModeChanged: (OrientationMode) -> Unit,
    onPlayerHostStateChanged: (AppMedia?, Boolean) -> Unit,
    onAudioBackgroundPolicyChanged: (BackgroundPlaybackMode, Boolean) -> Unit,
) {
    val coordinator by viewModel.state.collectAsStateWithLifecycle()
    val playback by playbackConnection.state.collectAsStateWithLifecycle()
    val audio by audioRepository.state.collectAsStateWithLifecycle()
    var audioDialogVisible by remember { mutableStateOf(false) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    var relinkAssociationId by remember { mutableStateOf<String?>(null) }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetRelink = relinkAssociationId
        relinkAssociationId = null
        if (uri != null) {
            audioRepository.persistReadPermission(uri)
            val descriptor = audioRepository.describe(uri)
            if (descriptor == null) {
                pickerError = "This file is not recognized as a supported external audio source. Choose AAC, M4A, MP3, FLAC, WAV, OGG, Opus, or another audio format supported by Media3 on this device."
            } else {
                if (targetRelink != null) audioController.relinkExternal(targetRelink, descriptor)
                else audioController.attachExternal(descriptor)
                pickerError = null
            }
        }
    }

    LaunchedEffect(playback.connected, playback.mediaId, playback.currentMediaItemIndex) {
        if (playback.connected) audioController.bind()
    }
    LaunchedEffect(audio.backgroundMode, audio.disableVideoInBackground) {
        onAudioBackgroundPolicyChanged(audio.backgroundMode, audio.disableVideoInBackground)
    }
    DisposableEffect(audioController) { onDispose { audioController.unbind() } }

    Box(Modifier.fillMaxSize()) {
        PlayerScreen(
            media = media,
            viewModel = viewModel,
            playbackConnection = playbackConnection,
            subtitleRepository = subtitleRepository,
            onBack = onBack,
            onEnterPip = onEnterPip,
            onFullscreenChanged = onFullscreenChanged,
            onOrientationModeChanged = onOrientationModeChanged,
            onPlayerHostStateChanged = onPlayerHostStateChanged,
        )

        if (coordinator.controlsVisible && !coordinator.controlsLocked && !coordinator.tutorialVisible && coordinator.resumePositionMs == null) {
            Button(
                onClick = { audioDialogVisible = true; audioController.bind() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 118.dp)
                    .testTag("audio_button")
                    .semantics { contentDescription = "Open professional audio controls" },
            ) { Text("Audio") }
        }
    }

    if (audioDialogVisible) {
        ProfessionalAudioDialog(
            state = audio,
            onDismiss = { audioDialogVisible = false },
            onAuto = audioController::selectAuto,
            onPreferredLanguage = audioController::setPreferredLanguage,
            onTrack = audioController::selectTrack,
            onExternal = audioController::selectExternal,
            onLoadExternal = { audioPicker.launch(arrayOf("audio/*", "application/ogg")) },
            onRelinkExternal = { id -> relinkAssociationId = id; audioPicker.launch(arrayOf("audio/*", "application/ogg")) },
            onRemoveExternal = audioController::removeExternal,
            onEqEnabled = audioController::setEqualizerEnabled,
            onPreset = audioController::setPreset,
            onBand = audioController::setEqBand,
            onPreamp = audioController::setPreamp,
            onBoost = audioController::setBoost,
            onDelayDelta = audioController::adjustAudioDelay,
            onDelayReset = audioController::resetAudioDelay,
            onChannel = audioController::setChannelMode,
            onBalance = audioController::setBalance,
            onPitch = audioController::setPitch,
            onPitchReset = audioController::resetPitch,
            onAudioOnly = audioController::setAudioOnly,
            onBackgroundMode = audioController::setBackgroundMode,
            onDisableVideoBackground = audioController::setDisableVideoInBackground,
            onRouteCompensationDelta = audioController::adjustRouteCompensation,
            onRouteCompensationReset = audioController::resetRouteCompensation,
            onRefreshExternal = audioController::refreshExternalAvailability,
        )
    }

    pickerError?.let { message ->
        AlertDialog(
            onDismissRequest = { pickerError = null },
            title = { Text("External audio") },
            text = { Text(message) },
            confirmButton = { Button(onClick = { pickerError = null }) { Text("OK") } },
        )
    }
}

@Composable
fun ProfessionalAudioDialog(
    state: AudioEngineState,
    onDismiss: () -> Unit,
    onAuto: () -> Unit,
    onPreferredLanguage: (String) -> Unit,
    onTrack: (String) -> Unit,
    onExternal: (String) -> Unit,
    onLoadExternal: () -> Unit,
    onRelinkExternal: (String) -> Unit,
    onRemoveExternal: (String) -> Unit,
    onEqEnabled: (Boolean) -> Unit,
    onPreset: (EqualizerPreset) -> Unit,
    onBand: (Int, Float) -> Unit,
    onPreamp: (Float) -> Unit,
    onBoost: (Float) -> Unit,
    onDelayDelta: (Long) -> Unit,
    onDelayReset: () -> Unit,
    onChannel: (AudioChannelMode) -> Unit,
    onBalance: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onPitchReset: () -> Unit,
    onAudioOnly: (Boolean) -> Unit,
    onBackgroundMode: (BackgroundPlaybackMode) -> Unit,
    onDisableVideoBackground: (Boolean) -> Unit,
    onRouteCompensationDelta: (Long) -> Unit,
    onRouteCompensationReset: () -> Unit,
    onRefreshExternal: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio") },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("professional_audio_panel"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle("Audio track")
                TextButton(onClick = onAuto, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.selectionMode == AudioSelectionMode.AUTO && state.selectedExternalId == null) "✓ Auto" else "Auto")
                }
                Text("Preferred language for Auto", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    AUDIO_LANGUAGE_OPTIONS.forEach { (code, label) ->
                        TextButton(onClick = { onPreferredLanguage(code) }) {
                            Text((if (state.preferredLanguages.firstOrNull() == code) "✓ " else "") + label)
                        }
                    }
                }
                state.tracks.filterNot { it.external }.forEach { track ->
                    TextButton(
                        onClick = { onTrack(track.key) },
                        enabled = track.supported,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val meta = listOfNotNull(
                            track.codec ?: track.mimeType?.substringAfter('/'),
                            track.channelCount?.let(::channelLabel),
                            track.sampleRate?.let { "${it / 1000f} kHz" },
                            track.bitrate?.let { "${it / 1000} kb/s" },
                        ).joinToString(" · ")
                        Text((if (track.selected && state.selectedExternalId == null) "✓ " else "") + track.label + if (meta.isBlank()) "" else "\n$meta")
                    }
                }

                if (state.externalAudio.isNotEmpty()) {
                    SectionTitle("External audio")
                    state.externalAudio.forEach { item ->
                        Column(Modifier.fillMaxWidth()) {
                            TextButton(
                                onClick = { onExternal(item.id) },
                                enabled = item.availability == AudioAvailability.AVAILABLE,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text((if (state.selectedExternalId == item.id) "✓ " else "") + item.displayName + "\n" + item.availability.name.replace('_', ' ').lowercase())
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (item.availability != AudioAvailability.AVAILABLE) TextButton(onClick = { onRelinkExternal(item.id) }) { Text("Relink") }
                                TextButton(onClick = { onRemoveExternal(item.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLoadExternal) { Text("Open external audio") }
                    TextButton(onClick = onRefreshExternal) { Text("Refresh") }
                }
                state.recoverableError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                SectionTitle("Equalizer")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Equalizer", Modifier.weight(1f))
                    Switch(checked = state.equalizerEnabled, onCheckedChange = onEqEnabled)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    EqualizerPreset.entries.filter { it != EqualizerPreset.CUSTOM }.forEach { preset ->
                        TextButton(onClick = { onPreset(preset) }) {
                            Text((if (state.equalizerPreset == preset) "✓ " else "") + preset.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                EQ_FREQUENCIES_HZ.forEachIndexed { index, frequency ->
                    val value = state.equalizerBandsDb.getOrElse(index) { 0f }
                    Column(Modifier.fillMaxWidth()) {
                        Text("${frequencyLabel(frequency)}  ${signedDb(value)}")
                        Slider(
                            value = value,
                            onValueChange = { onBand(index, it) },
                            valueRange = AudioPolicy.MIN_EQ_DB..AudioPolicy.MAX_EQ_DB,
                            modifier = Modifier.semantics {
                                contentDescription = "$frequency hertz equalizer"
                                stateDescription = "${signedDb(value)} decibels"
                            },
                        )
                    }
                }

                SectionTitle("Gain")
                AudioSlider("Preamp", state.preampDb, AudioPolicy.MIN_PREAMP_DB..AudioPolicy.MAX_PREAMP_DB, onPreamp)
                AudioSlider("Digital boost", state.boostDb, AudioPolicy.MIN_BOOST_DB..AudioPolicy.MAX_BOOST_DB, onBoost)
                val dspStatus = when {
                    !state.dspPipelineInstalled -> "DSP unavailable — playback service audio pipeline is not active"
                    state.dspAvailable -> "DSP active"
                    else -> state.dspBypassReason ?: "DSP bypassed for this output format"
                }
                Text(dspStatus, style = MaterialTheme.typography.bodySmall, color = if (state.dspPipelineInstalled && state.dspAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)

                SectionTitle("Audio synchronization")
                Text("Media: ${signedMs(state.audioDelayMs)}")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(-500L, -250L, -100L, -50L, -10L, 10L, 50L, 100L, 250L, 500L).forEach { delta ->
                        TextButton(onClick = { onDelayDelta(delta) }) { Text(if (delta > 0) "+$delta" else "$delta") }
                    }
                    TextButton(onClick = onDelayReset) { Text("Reset") }
                }
                Text("Positive delay = audio plays later. Negative delay = audio plays earlier.", style = MaterialTheme.typography.bodySmall)

                val stereoControlsAvailable = state.stereoChannelControlsAvailable
                SectionTitle("Channel / balance — stereo only")
                Text(
                    when (val count = state.selectedChannelCount) {
                        2 -> "Selected track is 2.0 stereo. Channel mode and left/right balance are active."
                        null -> "Channel mode and left/right balance stay disabled until the selected track is confirmed as 2.0 stereo."
                        else -> "Selected track is ${channelLabel(count)}. Multichannel layout is preserved; stereo-only channel mode and balance are not applied."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    AudioChannelMode.entries.forEach { mode ->
                        TextButton(
                            onClick = { onChannel(mode) },
                            enabled = stereoControlsAvailable,
                        ) {
                            Text((if (state.channelMode == mode) "✓ " else "") + mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                Text(balanceLabel(state.balance))
                Slider(
                    value = state.balance,
                    onValueChange = onBalance,
                    enabled = stereoControlsAvailable,
                    valueRange = -1f..1f,
                    modifier = Modifier.semantics {
                        contentDescription = "Left right audio balance, stereo tracks only"
                        stateDescription = if (stereoControlsAvailable) balanceLabel(state.balance) else "Unavailable for non-stereo track"
                    },
                )

                SectionTitle("Pitch")
                Text("${"%.2f".format(state.pitch)}×")
                Slider(
                    value = state.pitch,
                    onValueChange = onPitch,
                    valueRange = AudioPolicy.MIN_PITCH..AudioPolicy.MAX_PITCH,
                    modifier = Modifier.semantics {
                        contentDescription = "Playback pitch"
                        stateDescription = "${"%.2f".format(state.pitch)} times"
                    },
                )
                TextButton(onClick = onPitchReset) { Text("Reset pitch") }

                SectionTitle("Playback mode")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Play as audio")
                        Text("Disables the video track while keeping the same playback session and position.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.audioOnlyMode, onCheckedChange = onAudioOnly)
                }

                SectionTitle("When leaving player")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    BackgroundPlaybackMode.entries.forEach { mode ->
                        TextButton(onClick = { onBackgroundMode(mode) }) {
                            val label = when (mode) {
                                BackgroundPlaybackMode.PAUSE -> "Pause"
                                BackgroundPlaybackMode.CONTINUE_AUDIO -> "Continue audio"
                                BackgroundPlaybackMode.PIP_WHEN_POSSIBLE -> "PiP when possible"
                            }
                            Text((if (state.backgroundMode == mode) "✓ " else "") + label)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Disable video in background")
                        Text("Keeps the same MediaSession timeline and restores video on return. PiP remains video.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.disableVideoInBackground, onCheckedChange = onDisableVideoBackground)
                }

                SectionTitle("Audio output")
                Text(state.currentRoute.label)
                Text("Route compensation: ${signedMs(state.routeCompensationMs)}")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf(-250L, -100L, -50L, 50L, 100L, 250L).forEach { delta ->
                        TextButton(onClick = { onRouteCompensationDelta(delta) }) { Text(if (delta > 0) "+$delta" else "$delta") }
                    }
                    TextButton(onClick = onRouteCompensationReset) { Text("Reset route") }
                }
                Text("Effective sync: ${signedMs(state.audioDelayMs)} media + ${signedMs(state.routeCompensationMs)} route = ${signedMs(state.effectiveAudioDelayMs)}", style = MaterialTheme.typography.bodySmall)
                Text("Route profiles are separate from per-video sync. Use Android's media output control to switch connected outputs.", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.width(1.dp))
    Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun AudioSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("$label  ${signedDb(value)}")
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = "${signedDb(value)} decibels"
            },
        )
    }
}

private val AUDIO_LANGUAGE_OPTIONS = listOf(
    "en" to "English",
    "bn" to "Bangla",
    "hi" to "Hindi",
    "de" to "German",
    "es" to "Spanish",
    "fr" to "French",
    "ja" to "Japanese",
    "ar" to "Arabic",
)

private fun signedDb(value: Float): String = when {
    value > 0.005f -> "+${"%.1f".format(value)} dB"
    value < -0.005f -> "${"%.1f".format(value)} dB"
    else -> "0.0 dB"
}

private fun signedMs(value: Long): String = when {
    value > 0L -> "+$value ms"
    value < 0L -> "$value ms"
    else -> "0 ms"
}

private fun frequencyLabel(value: Int): String = if (value >= 1000) "${value / 1000} kHz" else "$value Hz"
private fun channelLabel(value: Int): String = when (value) { 1 -> "Mono"; 2 -> "2.0"; 6 -> "5.1"; 8 -> "7.1"; else -> "$value ch" }
private fun balanceLabel(value: Float): String {
    val amount = (kotlin.math.abs(value) * 100f).roundToInt()
    return when {
        amount == 0 -> "Center"
        value < 0f -> "L $amount"
        else -> "R $amount"
    }
}

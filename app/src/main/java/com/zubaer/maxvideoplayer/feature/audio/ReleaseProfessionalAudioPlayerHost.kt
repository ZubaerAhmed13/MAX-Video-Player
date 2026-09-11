package com.zubaer.maxvideoplayer.feature.audio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.PlaybackTarget
import com.zubaer.maxvideoplayer.feature.player.OrientationMode
import com.zubaer.maxvideoplayer.feature.player.PlayerScreen
import com.zubaer.maxvideoplayer.feature.player.PlayerViewModel
import com.zubaer.maxvideoplayer.feature.subtitle.SubtitleRepository
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import com.zubaer.maxvideoplayer.ui.MaxDesignTokens

/**
 * Step-10 release host. It keeps the existing production audio controller and advanced dialog, but
 * adds the approved compact Audio entry point and a translucent right-side track/sync panel.
 */
@Composable
fun ReleaseProfessionalAudioPlayerHost(
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
    val configuration = LocalConfiguration.current
    val isTelevision =
        (configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val localProcessingAvailable = playback.playbackTarget != PlaybackTarget.CAST_DEVICE
    val audioButtonFocusRequester = remember { FocusRequester() }
    val audioPanelFocusRequester = remember { FocusRequester() }
    var sidePanelVisible by remember { mutableStateOf(false) }
    var advancedVisible by remember { mutableStateOf(false) }
    var restoreAudioButtonFocus by remember { mutableStateOf(false) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    var relinkAssociationId by remember { mutableStateOf<String?>(null) }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetRelink = relinkAssociationId
        relinkAssociationId = null
        if (uri != null) {
            audioRepository.persistReadPermission(uri)
            val descriptor = audioRepository.describe(uri)
            if (descriptor == null) {
                pickerError = "This file is not recognized as a supported external audio source. Choose a format supported by Media3 on this device."
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
    LaunchedEffect(sidePanelVisible, isTelevision) {
        if (sidePanelVisible && !isTelevision) runCatching { audioPanelFocusRequester.requestFocus() }
    }
    LaunchedEffect(
        sidePanelVisible,
        advancedVisible,
        restoreAudioButtonFocus,
        coordinator.controlsVisible,
        coordinator.controlsLocked,
        isTelevision,
    ) {
        if (!sidePanelVisible && !advancedVisible && restoreAudioButtonFocus) {
            if (!isTelevision && coordinator.controlsVisible && !coordinator.controlsLocked) {
                runCatching { audioButtonFocusRequester.requestFocus() }
            }
            restoreAudioButtonFocus = false
        }
    }
    DisposableEffect(audioController) { onDispose { audioController.unbind() } }

    fun showAudioPanel(restoreToHostButton: Boolean) {
        sidePanelVisible = true
        advancedVisible = false
        restoreAudioButtonFocus = restoreToHostButton
        audioController.bind()
    }

    BackHandler(enabled = sidePanelVisible || advancedVisible) {
        if (advancedVisible) advancedVisible = false else sidePanelVisible = false
    }

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
            onAudioControls = { showAudioPanel(restoreToHostButton = false) },
        )

        if (coordinator.controlsVisible && !coordinator.controlsLocked && !coordinator.tutorialVisible && coordinator.resumePositionMs == null && !sidePanelVisible && !advancedVisible) {
            TextButton(
                onClick = { showAudioPanel(restoreToHostButton = !isTelevision) },
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaxDesignTokens.PlayerControl,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(top = 62.dp, end = 12.dp)
                    .focusRequester(audioButtonFocusRequester)
                    .testTag("audio_button")
                    .semantics {
                        contentDescription = if (localProcessingAvailable) "Audio tracks" else "Audio tracks; phone processing unavailable during Cast"
                    },
            ) { Text("Audio") }
        }

        if (sidePanelVisible) {
            val audioPanelHostModifier = if (!isTelevision) {
                Modifier
                    .fillMaxSize()
                    .focusRequester(audioPanelFocusRequester)
                    .focusable()
                    .testTag("audio_panel_focus_host")
                    .semantics { contentDescription = "Audio tracks panel" }
            } else {
                Modifier.fillMaxSize()
            }
            Box(audioPanelHostModifier) {
                ReleaseAudioSidePanel(
                    state = audio,
                    localProcessingAvailable = localProcessingAvailable,
                    onDismiss = { sidePanelVisible = false },
                    onAuto = audioController::selectAuto,
                    onTrack = audioController::selectTrack,
                    onExternal = audioController::selectExternal,
                    onLoadExternal = { audioPicker.launch(arrayOf("audio/*", "application/ogg")) },
                    onDelayDelta = audioController::adjustAudioDelay,
                    onDelayReset = audioController::resetAudioDelay,
                    onAdvanced = {
                        sidePanelVisible = false
                        advancedVisible = true
                    },
                )
            }
        }
    }

    if (advancedVisible) {
        ProfessionalAudioDialog(
            state = audio,
            localProcessingAvailable = localProcessingAvailable,
            onDismiss = { advancedVisible = false },
            onAuto = audioController::selectAuto,
            onPreferredLanguage = audioController::setPreferredLanguage,
            onTrack = audioController::selectTrack,
            onExternal = audioController::selectExternal,
            onLoadExternal = { audioPicker.launch(arrayOf("audio/*", "application/ogg")) },
            onLoadExternalUrl = { url ->
                val descriptor = audioRepository.describeNetworkUrl(url)
                if (descriptor == null) pickerError = "Enter a direct HTTP/HTTPS audio URL supported by Media3."
                else {
                    audioController.attachExternal(descriptor)
                    pickerError = null
                }
            },
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
private fun ReleaseAudioSidePanel(
    state: AudioEngineState,
    localProcessingAvailable: Boolean,
    onDismiss: () -> Unit,
    onAuto: () -> Unit,
    onTrack: (String) -> Unit,
    onExternal: (String) -> Unit,
    onLoadExternal: () -> Unit,
    onDelayDelta: (Long) -> Unit,
    onDelayReset: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f))) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(if (landscape) 0.42f else 0.94f)
                .fillMaxHeight()
                .safeDrawingPadding()
                .testTag("audio_side_panel"),
            color = MaxDesignTokens.PlayerOverlay,
            contentColor = Color.White,
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
                    Text("Audio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Close") }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

                Text("Tracks", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onAuto, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text(if (state.selectionMode == AudioSelectionMode.AUTO && state.selectedExternalId == null) "◉ Auto" else "○ Auto")
                }
                state.tracks.filterNot { it.external }.forEach { track ->
                    val details = listOfNotNull(track.codec ?: track.mimeType?.substringAfter('/'), track.channelCount?.let { if (it == 2) "Stereo" else "$it ch" }).joinToString(" · ")
                    TextButton(
                        onClick = { onTrack(track.key) },
                        enabled = track.supported,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text((if (track.selected && state.selectedExternalId == null) "◉ " else "○ ") + track.label)
                            if (details.isNotBlank()) Text(details, color = MaxDesignTokens.PlayerTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                state.externalAudio.forEach { item ->
                    TextButton(
                        onClick = { onExternal(item.id) },
                        enabled = item.availability == AudioAvailability.AVAILABLE,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text((if (state.selectedExternalId == item.id) "◉ " else "○ ") + item.displayName)
                    }
                }

                TextButton(onClick = onLoadExternal, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Open external audio")
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                Text("Audio synchronization", fontWeight = FontWeight.SemiBold)
                Text("${signedAudioDelay(state.audioDelayMs)}", color = MaxDesignTokens.PlayerTextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-500L, -100L, 100L, 500L).forEach { delta ->
                        TextButton(onClick = { onDelayDelta(delta) }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                            Text(if (delta > 0) "+${delta}ms" else "${delta}ms")
                        }
                    }
                }
                TextButton(onClick = onDelayReset, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Reset sync") }

                if (!localProcessingAvailable) {
                    Text(
                        "Local EQ, decoder-linked processing and phone transforms are unavailable while Cast is active. Saved settings are preserved.",
                        color = Color(0xFFFFC7C7),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                TextButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text("Equalizer & advanced audio")
                }
            }
        }
    }
}

private fun signedAudioDelay(value: Long): String = when {
    value > 0L -> "+$value ms"
    value < 0L -> "$value ms"
    else -> "0 ms"
}

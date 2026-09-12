package com.zubaer.maxvideoplayer.feature.output

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.cast.MediaRouteButton
import androidx.media3.common.util.UnstableApi

/**
 * Unified output affordance. Cast discovery/selection is intentionally delegated to Media3's real
 * MediaRouteButton so this app never invents a parallel device list or fake route state.
 */
@OptIn(UnstableApi::class)
@Composable
fun OutputDeviceButton(
    controller: ExternalDisplayController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var dialogVisible by remember { mutableStateOf(false) }
    val activeLabel = outputDeviceLabel(state)
    Button(
        onClick = { controller.refreshPlayerBinding(); dialogVisible = true },
        modifier = modifier.testTag("output_device_button"),
    ) { Text(activeLabel) }

    if (dialogVisible) {
        OutputDeviceDialog(controller = controller, onDismiss = { dialogVisible = false })
    }
}

/**
 * Dialog-only production output surface used by the unified player chrome. The launcher may live
 * in PlayerControls while Cast discovery still uses Media3's real MediaRouteButton here.
 */
@OptIn(UnstableApi::class)
@Composable
fun OutputDeviceDialog(
    controller: ExternalDisplayController,
    onDismiss: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback output") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    when (val active = state.active) {
                        OutputDeviceState.Local -> "Video is on this device."
                        is OutputDeviceState.External -> "Video is on ${active.display.name}. This device remains the remote control."
                        is OutputDeviceState.Cast -> "Playback is on a Cast receiver. Phone-only video and audio processing is unavailable until playback returns here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Google Cast")
                        Text(
                            "Choose or disconnect a Cast-enabled receiver.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    MediaRouteButton(modifier = Modifier.testTag("cast_route_button"))
                }

                TextButton(
                    onClick = controller::returnToPhone,
                    enabled = state.active is OutputDeviceState.External,
                    modifier = Modifier.fillMaxWidth().testTag("output_phone"),
                ) { Text("Play video on this device") }
                state.availableExternalDisplays.forEach { display ->
                    TextButton(
                        onClick = { controller.playOnExternalDisplay(display.displayId) },
                        enabled = state.active !is OutputDeviceState.Cast,
                        modifier = Modifier.fillMaxWidth().testTag("output_external_${display.displayId}"),
                    ) { Text("Play video on ${display.name}") }
                }
                if (state.availableExternalDisplays.isEmpty()) {
                    Text("No Presentation-capable external display is connected.", style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { error ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = controller::clearError) { Text("Dismiss") }
                    }
                }
            }
        },
    )
}

private fun outputDeviceLabel(state: OutputDeviceUiState): String = when (val active = state.active) {
    OutputDeviceState.Local -> "Output"
    is OutputDeviceState.External -> active.display.name
    is OutputDeviceState.Cast -> active.name ?: "Cast"
}

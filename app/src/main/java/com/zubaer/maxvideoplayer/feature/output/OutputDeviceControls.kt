package com.zubaer.maxvideoplayer.feature.output

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OutputDeviceButton(
    controller: ExternalDisplayController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var dialogVisible by remember { mutableStateOf(false) }
    val activeLabel = when (val active = state.active) {
        OutputDeviceState.Local -> "Output"
        is OutputDeviceState.External -> active.display.name
        is OutputDeviceState.Cast -> active.name ?: "Cast"
    }
    Button(
        onClick = { controller.refreshPlayerBinding(); dialogVisible = true },
        modifier = modifier.testTag("output_device_button"),
    ) { Text(activeLabel) }

    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text("Playback output") },
            confirmButton = { TextButton(onClick = { dialogVisible = false }) { Text("Done") } },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        when (val active = state.active) {
                            OutputDeviceState.Local -> "Video is on this device."
                            is OutputDeviceState.External -> "Video is on ${active.display.name}. This device remains the remote control."
                            is OutputDeviceState.Cast -> "Playback is on a Cast receiver. Disconnect Cast before using an external display."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = controller::returnToPhone,
                        enabled = state.active !is OutputDeviceState.Local,
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
}

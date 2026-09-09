package com.zubaer.maxvideoplayer.feature.sleeptimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.ceil

@Composable
fun SleepTimerButton(
    repository: SleepTimerRepository,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val label = when (val mode = state.mode) {
        is SleepTimerMode.Duration -> {
            val minutes = ceil((state.remainingMs ?: mode.durationMs).coerceAtLeast(0L) / 60_000.0).toInt()
            "Sleep timer · $minutes min"
        }
        SleepTimerMode.EndOfCurrentMedia -> "Sleep timer · End of video"
        SleepTimerMode.EndOfQueue -> "Sleep timer · End of queue"
        null -> "Sleep timer"
    }
    OutlinedButton(
        onClick = onOpen,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { stateDescription = if (state.active) "Active" else "Off" }
            .testTag("sleep_timer_button"),
    ) {
        Text(label)
    }
}

@Composable
fun SleepTimerDialog(
    repository: SleepTimerRepository,
    onDismiss: () -> Unit,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    var customMinutes by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.active) {
                    Text(activeDescription(state), modifier = Modifier.testTag("sleep_timer_active_state"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { repository.cancel(); onDismiss() },
                            modifier = Modifier.sizeIn(minHeight = 48.dp),
                        ) { Text("Cancel timer") }
                    }
                }

                Text("Duration")
                listOf(15, 30, 45, 60, 90).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            OutlinedButton(
                                onClick = {
                                    repository.startDuration(minutes * 60_000L)
                                    onDismiss()
                                },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            ) { Text("$minutes min") }
                        }
                    }
                }
                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { customMinutes = it.filter(Char::isDigit).take(4); validation = null },
                    label = { Text("Custom minutes") },
                    supportingText = { validation?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sleep_timer_custom_minutes"),
                )
                OutlinedButton(
                    onClick = {
                        val minutes = customMinutes.toLongOrNull()
                        if (minutes == null || minutes < 1L || minutes > 1440L) {
                            validation = "Enter 1–1440 minutes."
                        } else if (repository.startDuration(minutes * 60_000L)) {
                            onDismiss()
                        } else {
                            validation = "That duration is not available."
                        }
                    },
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) { Text("Start custom timer") }

                Text("Playback ending")
                OutlinedButton(
                    onClick = { repository.endOfCurrentMedia(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                ) { Text("End of current media") }
                OutlinedButton(
                    onClick = { repository.endOfQueue(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                ) { Text("End of queue") }
                Text("When the timer expires, playback pauses. Optional fade changes only the player output level and never Android system media volume.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun activeDescription(state: SleepTimerState): String = when (val mode = state.mode) {
    is SleepTimerMode.Duration -> {
        val remainingSeconds = ((state.remainingMs ?: mode.durationMs).coerceAtLeast(0L) + 999L) / 1000L
        "Active · ${remainingSeconds / 60L} min ${remainingSeconds % 60L} sec remaining${if (state.fading) " · fading" else ""}"
    }
    SleepTimerMode.EndOfCurrentMedia -> "Active · pauses at the end of the current media"
    SleepTimerMode.EndOfQueue -> "Active · pauses at the end of the queue"
    null -> "Off"
}

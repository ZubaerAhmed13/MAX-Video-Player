package com.zubaer.maxvideoplayer.feature.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

enum class TvDestination {
    LIBRARY,
    NETWORK,
    CLOUD,
    USB,
}

@Composable
fun TvHomeScreen(
    lastFocused: TvDestination,
    mountedUsbCount: Int,
    onFocused: (TvDestination) -> Unit,
    onDestination: (TvDestination) -> Unit,
) {
    val libraryFocus = remember { FocusRequester() }
    val networkFocus = remember { FocusRequester() }
    val cloudFocus = remember { FocusRequester() }
    val usbFocus = remember { FocusRequester() }

    fun requester(destination: TvDestination): FocusRequester = when (destination) {
        TvDestination.LIBRARY -> libraryFocus
        TvDestination.NETWORK -> networkFocus
        TvDestination.CLOUD -> cloudFocus
        TvDestination.USB -> usbFocus
    }

    LaunchedEffect(lastFocused) {
        requester(lastFocused).requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 42.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("MAX Video Player", style = MaterialTheme.typography.displaySmall)
        Text(
            "Choose a source with the D-pad. Playback continues through the same service-owned MediaSession.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().focusRestorer(requester(lastFocused)),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvDestination.entries.forEach { destination ->
                Button(
                    onClick = { onDestination(destination) },
                    modifier = Modifier
                        .focusRequester(requester(destination))
                        .weight(1f)
                        .testTag("tv_destination_${destination.name.lowercase()}")
                        .onFocusChanged { state -> if (state.isFocused) onFocused(destination) },
                ) {
                    Text(
                        when (destination) {
                            TvDestination.LIBRARY -> "Local library"
                            TvDestination.NETWORK -> "Network"
                            TvDestination.CLOUD -> "Cloud"
                            TvDestination.USB -> if (mountedUsbCount > 0) "USB / OTG ($mountedUsbCount)" else "USB / OTG"
                        },
                    )
                }
            }
        }
        Text(
            "Back returns here from Network or Cloud. Local and USB sources use Android's Storage Access Framework; no broad filesystem access is required.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

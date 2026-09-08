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

/**
 * TV-first entry surface. It uses Compose-for-TV controls and an explicit focus-restoration group
 * so D-pad navigation never depends on touch-only phone chrome.
 */
@Composable
fun TvHomeScreen(
    lastFocused: TvDestination,
    mountedUsbCount: Int,
    onFocused: (TvDestination) -> Unit,
    onDestination: (TvDestination) -> Unit,
) {
    val libraryFocus = remember { FocusRequester() }
    val groupFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (lastFocused == TvDestination.LIBRARY) libraryFocus.requestFocus()
        else groupFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 42.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("MAX Video Player", style = MaterialTheme.typography.displaySmall)
        Text(
            "Choose a source with the D-pad. Playback continues through the same service-owned MediaSession.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(libraryFocus),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvDestination.entries.forEach { destination ->
                val focusModifier = if (destination == TvDestination.LIBRARY) {
                    Modifier.focusRequester(libraryFocus)
                } else Modifier
                Button(
                    onClick = { onDestination(destination) },
                    modifier = focusModifier
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

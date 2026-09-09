package com.zubaer.maxvideoplayer.feature.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.zubaer.maxvideoplayer.core.model.SourceAvailability
import com.zubaer.maxvideoplayer.feature.library.LibraryPlaybackRequest
import com.zubaer.maxvideoplayer.feature.library.LibraryUiState

/**
 * TV-first local library surface. It consumes the same LibraryUiState and playback request as the
 * phone library, but gives the remote an explicit first-playable focus target and avoids touch-only
 * affordances. No second media repository or queue is introduced.
 */
@Composable
fun TvLibraryScreen(
    state: LibraryUiState,
    playbackRequest: (com.zubaer.maxvideoplayer.core.model.AppMedia) -> LibraryPlaybackRequest,
    onPlay: (LibraryPlaybackRequest) -> Unit,
    onBack: () -> Unit,
) {
    val firstPlayableIndex = state.media.indexOfFirst { it.availability == SourceAvailability.AVAILABLE }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(firstPlayableIndex, state.loading) {
        if (!state.loading && firstPlayableIndex >= 0) firstFocus.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp).testTag("tv_library_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Local library", style = MaterialTheme.typography.headlineLarge)
                Text("Choose a video with the D-pad and press Center / Enter.")
            }
            Button(onClick = onBack, modifier = Modifier.testTag("tv_library_back")) { Text("TV Home") }
        }

        when {
            state.loading -> Text("Loading videos…")
            state.error != null -> Text(state.error)
            state.media.isEmpty() -> Text("No videos found. Add videos or folders from the phone/tablet library setup, then return here.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("tv_library_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.media, key = { _, media -> media.stableId }) { index, media ->
                    val available = media.availability == SourceAvailability.AVAILABLE
                    Button(
                        onClick = { if (available) onPlay(playbackRequest(media)) },
                        enabled = available,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == firstPlayableIndex) Modifier.focusRequester(firstFocus) else Modifier)
                            .testTag("tv_media_${media.stableId}"),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(media.title)
                            val details = buildList {
                                media.durationMs?.let { add(formatTvDuration(it)) }
                                if (media.width != null && media.height != null) add("${media.width}×${media.height}")
                                media.folderName?.let { add(it) }
                            }.joinToString(" · ")
                            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
                            if (!available) Text("Source ${media.availability.name.lowercase().replace('_', ' ')}")
                        }
                    }
                }
            }
        }
    }
}

private fun formatTvDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1_000L
    val hours = total / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

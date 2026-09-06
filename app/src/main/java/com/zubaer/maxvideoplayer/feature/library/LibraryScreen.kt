package com.zubaer.maxvideoplayer.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zubaer.maxvideoplayer.core.model.AppMedia

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onRefresh: () -> Unit,
    onOpenDocument: (Uri) -> Unit,
    onPlay: (AppMedia) -> Unit,
    onOpenNetworkUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val mediaPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) onRefresh()
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onOpenDocument(uri)
    }
    var networkUrl by remember { mutableStateOf("") }

    LaunchedEffect(hasPermission) {
        if (hasPermission && state.videos.isEmpty() && !state.loading) onRefresh()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("MAX Video Player", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Step 1 professional playback foundation", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { fileLauncher.launch(arrayOf("video/*", "audio/*")) },
                    modifier = Modifier.testTag("open_file_button"),
                ) { Text("Open file") }
                OutlinedButton(onClick = {
                    if (hasPermission) onRefresh() else permissionLauncher.launch(mediaPermission)
                }) { Text(if (hasPermission) "Refresh library" else "Allow library access") }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = networkUrl,
                onValueChange = { networkUrl = it },
                modifier = Modifier.fillMaxWidth().testTag("network_url_input"),
                singleLine = true,
                label = { Text("HTTPS / HLS / DASH / RTSP URL") },
                supportingText = { Text("Cleartext HTTP is intentionally disabled.") },
            )
            Button(
                onClick = { if (networkUrl.isNotBlank()) onOpenNetworkUrl(networkUrl.trim()) },
                enabled = networkUrl.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) { Text("Play URL") }

            Spacer(Modifier.height(12.dp))
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                !hasPermission -> Text("Library permission is optional. Open file uses Android's Storage Access Framework and does not require broad storage access.")
                state.videos.isEmpty() -> Text("No indexed videos found.")
                else -> LazyColumn(Modifier.fillMaxSize().testTag("library_list")) {
                    items(state.videos, key = { it.stableId }) { media ->
                        MediaRow(media = media, onClick = { onPlay(media) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaRow(media: AppMedia, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(media.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            val details = buildList {
                media.durationMs?.let { add(formatDuration(it)) }
                if (media.width != null && media.height != null) add("${media.width}×${media.height}")
                media.sizeBytes?.let { add(formatBytes(it)) }
            }.joinToString(" • ")
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onClick) { Text("Play") }
    }
}

private fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1L shl 30 -> "%.1f GB".format(safe.toDouble() / (1L shl 30))
        safe >= 1L shl 20 -> "%.1f MB".format(safe.toDouble() / (1L shl 20))
        else -> "%.1f KB".format(safe.toDouble() / (1L shl 10))
    }
}

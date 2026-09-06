package com.zubaer.maxvideoplayer.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zubaer.maxvideoplayer.core.database.MediaHistoryEntity
import com.zubaer.maxvideoplayer.core.database.PlaylistEntity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.SourceAvailability

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    thumbnailRepository: ThumbnailRepository,
    onRefresh: () -> Unit,
    onOpenDocument: (Uri) -> Unit,
    onAddFolder: (Uri) -> Unit,
    onPlay: (LibraryPlaybackRequest) -> Unit,
    onOpenNetworkUrl: (String) -> Unit,
    onSection: (LibrarySection) -> Unit,
    onQuery: (String) -> Unit,
    onSort: (VideoSort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onOpenFolder: (String) -> Unit,
    onCloseFolder: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onClosePlaylist: () -> Unit,
    onToggleFavourite: (AppMedia) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddToPlaylist: (Long, AppMedia) -> Unit,
    onRemoveFromPlaylist: (Long, AppMedia) -> Unit,
    onMovePlaylistItem: (Long, AppMedia, Int) -> Unit,
    onExcludeFolder: (FolderItem) -> Unit,
    onRestoreFolder: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    playbackRequest: (AppMedia) -> LibraryPlaybackRequest,
) {
    val context = LocalContext.current
    val mediaPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) onRefresh()
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onOpenDocument(uri) }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onAddFolder(uri) }
    var networkUrl by remember { mutableStateOf("") }
    var clearHistoryConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission) { if (hasPermission) onRefresh() }

    if (clearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { clearHistoryConfirm = false },
            title = { Text("Clear playback history?") },
            text = { Text("This removes resume/history records only. It does not delete any video files.") },
            confirmButton = { Button(onClick = { clearHistoryConfirm = false; onClearHistory() }) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { clearHistoryConfirm = false }) { Text("Cancel") } },
        )
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("MAX Video Player", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Professional media library", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Button(onClick = { fileLauncher.launch(arrayOf("video/*", "audio/*")) }, modifier = Modifier.testTag("open_file_button")) { Text("Open file") }
                }
                item { OutlinedButton(onClick = { treeLauncher.launch(null) }) { Text("Add folder") } }
                item {
                    OutlinedButton(onClick = { if (hasPermission) onRefresh() else permissionLauncher.launch(mediaPermission) }) {
                        Text(if (hasPermission) "Refresh" else "Allow videos")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(LibrarySection.entries, key = { it.name }) { section ->
                    FilterChip(
                        selected = state.section == section,
                        onClick = { onSection(section) },
                        label = { Text(section.label()) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            if (state.section != LibrarySection.FOLDERS || state.selectedFolderKey != null) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth().testTag("library_search_input"),
                    singleLine = true,
                    label = { Text("Search title, filename or folder") },
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item { TextButton(onClick = { onSort(state.sort.next()) }) { Text("Sort: ${state.sort.label()}") } }
                    item { TextButton(onClick = onToggleSortDirection) { Text(if (state.sortDirection == SortDirection.ASCENDING) "↑ Asc" else "↓ Desc") } }
                    item { TextButton(onClick = { onFilter(state.filter.next()) }) { Text("Filter: ${state.filter.label()}") } }
                    item { TextButton(onClick = { onViewMode(if (state.viewMode == LibraryViewMode.LIST) LibraryViewMode.GRID else LibraryViewMode.LIST) }) { Text(if (state.viewMode == LibraryViewMode.LIST) "Grid" else "List") } }
                    if (state.section == LibrarySection.HISTORY) item { TextButton(onClick = { clearHistoryConfirm = true }) { Text("Clear history") } }
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp)) }
            if (state.loading) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            when {
                state.section == LibrarySection.FOLDERS && state.selectedFolderKey == null -> FolderBrowser(
                    state = state,
                    onOpenFolder = onOpenFolder,
                    onExcludeFolder = onExcludeFolder,
                    onRestoreFolder = onRestoreFolder,
                    onRemoveSource = onRemoveSource,
                )
                state.section == LibrarySection.PLAYLISTS && state.selectedPlaylistId == null -> PlaylistsRoot(
                    playlists = state.playlists,
                    onCreatePlaylist = onCreatePlaylist,
                    onOpenPlaylist = onOpenPlaylist,
                )
                else -> {
                    if (state.section == LibrarySection.FOLDERS && state.selectedFolderKey != null) {
                        TextButton(onClick = onCloseFolder) { Text("← All folders") }
                    }
                    if (state.section == LibrarySection.PLAYLISTS && state.selectedPlaylistId != null) {
                        PlaylistHeader(
                            playlist = state.playlists.firstOrNull { it.id == state.selectedPlaylistId },
                            onBack = onClosePlaylist,
                            onRename = onRenamePlaylist,
                            onDelete = onDeletePlaylist,
                        )
                    }
                    MediaCollection(
                        state = state,
                        thumbnailRepository = thumbnailRepository,
                        onPlay = { onPlay(playbackRequest(it)) },
                        onToggleFavourite = onToggleFavourite,
                        onAddToPlaylist = onAddToPlaylist,
                        onRemoveFromPlaylist = onRemoveFromPlaylist,
                        onMovePlaylistItem = onMovePlaylistItem,
                        onDeleteHistory = onDeleteHistory,
                    )
                }
            }

            if (!hasPermission && state.media.isEmpty() && state.section == LibrarySection.VIDEOS) {
                Text(
                    "Library permission is optional. Open file and Add folder use Android's Storage Access Framework without unrestricted storage access.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = networkUrl,
                onValueChange = { networkUrl = it },
                modifier = Modifier.fillMaxWidth().testTag("network_url_input"),
                singleLine = true,
                label = { Text("HTTPS / HLS / DASH / RTSP URL") },
            )
            Button(
                onClick = { if (networkUrl.isNotBlank()) onOpenNetworkUrl(networkUrl.trim()) },
                enabled = networkUrl.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) { Text("Play URL") }
        }
    }
}

@Composable
private fun FolderBrowser(
    state: LibraryUiState,
    onOpenFolder: (String) -> Unit,
    onExcludeFolder: (FolderItem) -> Unit,
    onRestoreFolder: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    LazyColumn(Modifier.weightSafe().testTag("folder_list")) {
        if (state.folders.isEmpty()) item { Text("No video folders found.", modifier = Modifier.padding(16.dp)) }
        items(state.folders, key = { it.key }) { folder ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onOpenFolder(folder.key) }) {
                    Text(folder.name, fontWeight = FontWeight.SemiBold)
                    Text("${folder.videos.size} videos • ${formatBytes(folder.totalSizeBytes)}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onExcludeFolder(folder) }) { Text("Exclude") }
            }
            HorizontalDivider()
        }
        if (state.sources.isNotEmpty()) {
            item { Text("Added folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) }
            items(state.sources, key = { it.id }) { source ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.displayName)
                        Text(source.status.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onRemoveSource(source.id) }) { Text("Remove") }
                }
            }
        }
        if (state.excludedFolders.isNotEmpty()) {
            item { Text("Excluded folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) }
            items(state.excludedFolders, key = { it.folderKey }) { excluded ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(excluded.displayName, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRestoreFolder(excluded.folderKey) }) { Text("Restore") }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsRoot(
    playlists: List<PlaylistEntity>,
    onCreatePlaylist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New playlist") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (name.isNotBlank()) { onCreatePlaylist(name); name = "" } }, enabled = name.isNotBlank()) { Text("Create") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize().testTag("playlist_list")) {
            if (playlists.isEmpty()) item { Text("No playlists yet.", modifier = Modifier.padding(16.dp)) }
            items(playlists, key = { it.id }) { playlist ->
                Row(Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist.id) }.padding(vertical = 12.dp)) {
                    Text(playlist.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Open")
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: PlaylistEntity?,
    onBack: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (playlist == null) return
    var rename by remember(playlist.id, playlist.name) { mutableStateOf(playlist.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("The playlist will be removed. Video files are not deleted.") },
            confirmButton = { Button(onClick = { confirmDelete = false; onDelete(playlist.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("← Playlists") }
        OutlinedTextField(value = rename, onValueChange = { rename = it }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (rename.isNotBlank()) onRename(playlist.id, rename) }) { Text("Rename") }
        TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
    }
}

@Composable
private fun MediaCollection(
    state: LibraryUiState,
    thumbnailRepository: ThumbnailRepository,
    onPlay: (AppMedia) -> Unit,
    onToggleFavourite: (AppMedia) -> Unit,
    onAddToPlaylist: (Long, AppMedia) -> Unit,
    onRemoveFromPlaylist: (Long, AppMedia) -> Unit,
    onMovePlaylistItem: (Long, AppMedia, Int) -> Unit,
    onDeleteHistory: (String) -> Unit,
) {
    if (state.media.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(emptyMessage(state.section)) }
        return
    }
    val historyById = state.history.associateBy { it.stableMediaId }
    if (state.viewMode == LibraryViewMode.GRID) {
        LazyVerticalGrid(columns = GridCells.Adaptive(170.dp), modifier = Modifier.fillMaxSize().testTag("library_grid")) {
            gridItems(state.media, key = { it.stableId }) { media ->
                MediaCard(
                    media = media,
                    history = historyById[media.stableId],
                    favourite = media.stableId in state.favouriteIds,
                    playlists = state.playlists,
                    selectedPlaylistId = state.selectedPlaylistId,
                    thumbnailRepository = thumbnailRepository,
                    onPlay = onPlay,
                    onToggleFavourite = onToggleFavourite,
                    onAddToPlaylist = onAddToPlaylist,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                    onMovePlaylistItem = onMovePlaylistItem,
                    onDeleteHistory = onDeleteHistory,
                    compact = true,
                )
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().testTag("library_list")) {
            items(state.media, key = { it.stableId }) { media ->
                MediaCard(
                    media = media,
                    history = historyById[media.stableId],
                    favourite = media.stableId in state.favouriteIds,
                    playlists = state.playlists,
                    selectedPlaylistId = state.selectedPlaylistId,
                    thumbnailRepository = thumbnailRepository,
                    onPlay = onPlay,
                    onToggleFavourite = onToggleFavourite,
                    onAddToPlaylist = onAddToPlaylist,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                    onMovePlaylistItem = onMovePlaylistItem,
                    onDeleteHistory = onDeleteHistory,
                    compact = false,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MediaCard(
    media: AppMedia,
    history: MediaHistoryEntity?,
    favourite: Boolean,
    playlists: List<PlaylistEntity>,
    selectedPlaylistId: Long?,
    thumbnailRepository: ThumbnailRepository,
    onPlay: (AppMedia) -> Unit,
    onToggleFavourite: (AppMedia) -> Unit,
    onAddToPlaylist: (Long, AppMedia) -> Unit,
    onRemoveFromPlaylist: (Long, AppMedia) -> Unit,
    onMovePlaylistItem: (Long, AppMedia, Int) -> Unit,
    onDeleteHistory: (String) -> Unit,
    compact: Boolean,
) {
    var playlistMenu by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            MediaThumbnail(media, thumbnailRepository, if (compact) 360 else 240, if (compact) 203 else 135)
            if (!compact) Spacer(Modifier.height(6.dp))
            Text(media.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            val details = buildList {
                media.durationMs?.let { add(formatDuration(it)) }
                if (media.width != null && media.height != null) add("${media.width}×${media.height}")
                media.sizeBytes?.let { add(formatBytes(it)) }
                media.folderName?.let { add(it) }
            }.joinToString(" • ")
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            history?.takeIf { it.durationMs > 0L && it.lastPositionMs > 0L }?.let {
                val percent = ((it.lastPositionMs.toDouble() / it.durationMs.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                Text("Progress $percent%", style = MaterialTheme.typography.labelSmall)
            }
            if (media.availability != SourceAvailability.AVAILABLE) {
                Text("Source unavailable", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Button(onClick = { onPlay(media) }, enabled = media.availability == SourceAvailability.AVAILABLE) { Text("Play") } }
                item { TextButton(onClick = { onToggleFavourite(media) }) { Text(if (favourite) "★" else "☆") } }
                if (playlists.isNotEmpty()) item {
                    Box {
                        TextButton(onClick = { playlistMenu = true }) { Text("Playlist") }
                        DropdownMenu(expanded = playlistMenu, onDismissRequest = { playlistMenu = false }) {
                            playlists.forEach { playlist ->
                                DropdownMenuItem(
                                    text = { Text(playlist.name) },
                                    onClick = { playlistMenu = false; onAddToPlaylist(playlist.id, media) },
                                )
                            }
                        }
                    }
                }
                if (selectedPlaylistId != null) {
                    item { TextButton(onClick = { onMovePlaylistItem(selectedPlaylistId, media, -1) }) { Text("↑") } }
                    item { TextButton(onClick = { onMovePlaylistItem(selectedPlaylistId, media, 1) }) { Text("↓") } }
                    item { TextButton(onClick = { onRemoveFromPlaylist(selectedPlaylistId, media) }) { Text("Remove") } }
                }
                if (history != null) item { TextButton(onClick = { onDeleteHistory(media.stableId) }) { Text("History ×") } }
            }
        }
    }
    if (compact) Card(Modifier.padding(4.dp)) { content() } else content()
}

@Composable
private fun MediaThumbnail(media: AppMedia, repository: ThumbnailRepository, width: Int, height: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, media.stableId, width, height) {
        value = repository.load(media, width, height)
    }
    val modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    if (bitmap != null) Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("Video") }
}

private fun LibrarySection.label() = when (this) {
    LibrarySection.VIDEOS -> "Videos"
    LibrarySection.FOLDERS -> "Folders"
    LibrarySection.CONTINUE_WATCHING -> "Continue"
    LibrarySection.RECENT -> "Recent"
    LibrarySection.FAVOURITES -> "Favourites"
    LibrarySection.PLAYLISTS -> "Playlists"
    LibrarySection.HISTORY -> "History"
}

private fun VideoSort.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun LibraryFilter.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun VideoSort.next(): VideoSort = VideoSort.entries[(ordinal + 1) % VideoSort.entries.size]
private fun LibraryFilter.next(): LibraryFilter = LibraryFilter.entries[(ordinal + 1) % LibraryFilter.entries.size]
private fun emptyMessage(section: LibrarySection) = when (section) {
    LibrarySection.VIDEOS -> "No videos found."
    LibrarySection.CONTINUE_WATCHING -> "Nothing to continue watching."
    LibrarySection.RECENT -> "No recently played videos."
    LibrarySection.FAVOURITES -> "No favourites yet."
    LibrarySection.HISTORY -> "Playback history is empty."
    LibrarySection.PLAYLISTS -> "This playlist is empty."
    LibrarySection.FOLDERS -> "This folder is empty."
}

private fun formatDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val gib = 1L shl 30
    val mib = 1L shl 20
    val kib = 1L shl 10
    return when {
        safe >= gib -> "%.1f GB".format(safe.toDouble() / gib.toDouble())
        safe >= mib -> "%.1f MB".format(safe.toDouble() / mib.toDouble())
        safe >= kib -> "%.1f KB".format(safe.toDouble() / kib.toDouble())
        else -> "$safe B"
    }
}

private fun Modifier.weightSafe(): Modifier = this.fillMaxSize()

package com.zubaer.maxvideoplayer.feature.library

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zubaer.maxvideoplayer.core.database.MediaHistoryEntity
import com.zubaer.maxvideoplayer.core.database.PlaylistEntity
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.core.model.SourceAvailability
import kotlinx.coroutines.flow.Flow

/**
 * Step-10 clean-room library presentation. It consumes the existing LibraryUiState and forwards the
 * existing actions; scanning, thumbnails, persistence, file operations and playback remain unchanged.
 */
@Composable
fun ReleaseLibraryScreen(
    state: LibraryUiState,
    events: Flow<LibraryEvent>,
    thumbnailRepository: ThumbnailRepository,
    onRefresh: () -> Unit,
    onOpenDocument: (Uri) -> Unit,
    onAddFolder: (Uri) -> Unit,
    onPlay: (LibraryPlaybackRequest) -> Unit,
    onOpenNetworkUrl: (String) -> Unit,
    onOpenNetworkCenter: () -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUsb: () -> Unit,
    mountedUsbCount: Int,
    onSection: (LibrarySection) -> Unit,
    onQuery: (String) -> Unit,
    onSort: (VideoSort) -> Unit,
    onFolderSort: (FolderSort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onOpenFolder: (String) -> Unit,
    onCloseFolder: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onClosePlaylist: () -> Unit,
    onToggleFavourite: (AppMedia) -> Unit,
    onSetFavourite: (AppMedia, Boolean) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddToPlaylist: (Long, AppMedia) -> Unit,
    onAddManyToPlaylist: (Long, Collection<AppMedia>) -> Unit,
    onRemoveFromPlaylist: (Long, AppMedia) -> Unit,
    onMovePlaylistItem: (Long, AppMedia, Int) -> Unit,
    onExcludeFolder: (FolderItem) -> Unit,
    onRestoreFolder: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRequestDelete: (AppMedia) -> Unit,
    onRequestRename: (AppMedia, String) -> Unit,
    onFileActionApproval: (FileActionConfirmation, Boolean) -> Unit,
    onRelinkSelected: (AppMedia, Uri) -> Unit,
    playbackRequest: (AppMedia) -> LibraryPlaybackRequest,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val mediaPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED) }
    var pendingFileConfirmation by remember { mutableStateOf<FileActionConfirmation?>(null) }
    var pendingRelink by remember { mutableStateOf<AppMedia?>(null) }
    var searchVisible by remember { mutableStateOf(state.query.isNotEmpty()) }
    var moreExpanded by remember { mutableStateOf(false) }
    var networkDialogVisible by remember { mutableStateOf(false) }
    var networkUrl by remember { mutableStateOf("") }
    var clearHistoryConfirm by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) onRefresh()
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onOpenDocument(uri) }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onAddFolder(uri) }
    val relinkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val original = pendingRelink
        pendingRelink = null
        if (uri != null && original != null) onRelinkSelected(original, uri)
    }
    val fileConfirmationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val pending = pendingFileConfirmation
        pendingFileConfirmation = null
        if (pending != null) onFileActionApproval(pending, result.resultCode == Activity.RESULT_OK)
    }

    val selectedFolderName = state.selectedFolderKey?.let { key -> state.folders.firstOrNull { it.key == key }?.name }
    val selectedPlaylist = state.selectedPlaylistId?.let { id -> state.playlists.firstOrNull { it.id == id } }
    val pageTitle = selectedFolderName ?: selectedPlaylist?.name ?: when (state.section) {
        LibrarySection.FOLDERS -> "Folders"
        LibrarySection.VIDEOS -> "Videos"
        LibrarySection.CONTINUE_WATCHING -> "Continue watching"
        LibrarySection.RECENT -> "Recent"
        LibrarySection.FAVOURITES -> "Favourites"
        LibrarySection.PLAYLISTS -> "Playlists"
        LibrarySection.HISTORY -> "History"
    }
    val canNavigateBack = state.selectedFolderKey != null || state.selectedPlaylistId != null

    BackHandler(enabled = searchVisible || canNavigateBack) {
        when {
            searchVisible -> {
                searchVisible = false
                onQuery("")
                focusManager.clearFocus()
            }
            state.selectedFolderKey != null -> onCloseFolder()
            state.selectedPlaylistId != null -> onClosePlaylist()
        }
    }

    LaunchedEffect(hasPermission) { if (hasPermission) onRefresh() }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is LibraryEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                is LibraryEvent.FileConfirmationRequired -> {
                    pendingFileConfirmation = event.request
                    fileConfirmationLauncher.launch(IntentSenderRequest.Builder(event.request.intentSender).build())
                }
            }
        }
    }

    if (clearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { clearHistoryConfirm = false },
            title = { Text("Clear playback history?") },
            text = { Text("This removes resume/history records only. It does not delete video files.") },
            confirmButton = { Button(onClick = { clearHistoryConfirm = false; onClearHistory() }) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { clearHistoryConfirm = false }) { Text("Cancel") } },
        )
    }

    if (networkDialogVisible) {
        AlertDialog(
            onDismissRequest = { networkDialogVisible = false },
            title = { Text("Open network stream") },
            text = {
                OutlinedTextField(
                    value = networkUrl,
                    onValueChange = { networkUrl = it },
                    modifier = Modifier.fillMaxWidth().testTag("network_url_input"),
                    singleLine = true,
                    label = { Text("HTTPS / HLS / DASH / RTSP URL") },
                )
            },
            confirmButton = {
                Button(
                    onClick = { networkDialogVisible = false; onOpenNetworkUrl(networkUrl.trim()) },
                    enabled = networkUrl.isNotBlank(),
                ) { Text("Play") }
            },
            dismissButton = { TextButton(onClick = { networkDialogVisible = false }) { Text("Cancel") } },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canNavigateBack) {
                        TextButton(onClick = { if (state.selectedFolderKey != null) onCloseFolder() else onClosePlaylist() }) { Text("‹", fontSize = 28.sp) }
                    }
                    Text(
                        pageTitle,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) onQuery("")
                        },
                        modifier = Modifier.testTag("library_search_button"),
                    ) { Text("⌕", fontSize = 23.sp) }
                    TextButton(
                        onClick = { onViewMode(if (state.viewMode == LibraryViewMode.LIST) LibraryViewMode.GRID else LibraryViewMode.LIST) },
                        modifier = Modifier.testTag("library_view_button"),
                    ) { Text(if (state.viewMode == LibraryViewMode.LIST) "▦" else "☷", fontSize = 19.sp) }
                    Box {
                        TextButton(onClick = { moreExpanded = true }, modifier = Modifier.testTag("library_more_button")) { Text("⋮", fontSize = 24.sp) }
                        DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                            DropdownMenuItem(text = { Text("Open file") }, onClick = { moreExpanded = false; fileLauncher.launch(arrayOf("video/*", "audio/*")) })
                            DropdownMenuItem(text = { Text("Add folder") }, onClick = { moreExpanded = false; treeLauncher.launch(null) })
                            DropdownMenuItem(
                                text = { Text(if (hasPermission) "Refresh library" else "Allow videos") },
                                onClick = {
                                    moreExpanded = false
                                    if (hasPermission) onRefresh() else permissionLauncher.launch(mediaPermission)
                                },
                            )
                            HorizontalDivider()
                            if (state.section == LibrarySection.FOLDERS && state.selectedFolderKey == null) {
                                DropdownMenuItem(text = { Text("Folder sort: ${state.folderSort.label()}") }, onClick = { moreExpanded = false; onFolderSort(state.folderSort.next()) })
                            } else {
                                DropdownMenuItem(text = { Text("Sort: ${state.sort.label()}") }, onClick = { moreExpanded = false; onSort(state.sort.next()) })
                                DropdownMenuItem(text = { Text("Filter: ${state.filter.label()}") }, onClick = { moreExpanded = false; onFilter(state.filter.next()) })
                            }
                            DropdownMenuItem(text = { Text(if (state.sortDirection == SortDirection.ASCENDING) "Direction: Ascending" else "Direction: Descending") }, onClick = { moreExpanded = false; onToggleSortDirection() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Continue watching") }, onClick = { moreExpanded = false; onSection(LibrarySection.CONTINUE_WATCHING) })
                            DropdownMenuItem(text = { Text("Recent") }, onClick = { moreExpanded = false; onSection(LibrarySection.RECENT) })
                            DropdownMenuItem(text = { Text("Favourites") }, onClick = { moreExpanded = false; onSection(LibrarySection.FAVOURITES) })
                            DropdownMenuItem(text = { Text("History") }, onClick = { moreExpanded = false; onSection(LibrarySection.HISTORY) })
                            if (state.section == LibrarySection.HISTORY) {
                                DropdownMenuItem(text = { Text("Clear history") }, onClick = { moreExpanded = false; clearHistoryConfirm = true })
                            }
                            DropdownMenuItem(text = { Text("Open network URL") }, onClick = { moreExpanded = false; networkDialogVisible = true })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { moreExpanded = false; onOpenSettings() })
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth().testTag("library_section_row"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    item { SourceChip("Folders", state.section == LibrarySection.FOLDERS, "section_folders") { onSection(LibrarySection.FOLDERS) } }
                    item { SourceChip("Videos", state.section == LibrarySection.VIDEOS, "section_videos") { onSection(LibrarySection.VIDEOS) } }
                    item { SourceChip("Private", false, "section_private", onOpenPrivate) }
                    item { SourceChip("Network", false, "section_network", onOpenNetworkCenter) }
                    item { SourceChip("Cloud", false, "section_cloud", onOpenCloud) }
                    item { SourceChip(if (mountedUsbCount > 0) "USB ($mountedUsbCount)" else "USB", false, "section_usb", onOpenUsb) }
                    item { SourceChip("Playlists", state.section == LibrarySection.PLAYLISTS, "section_playlists") { onSection(LibrarySection.PLAYLISTS) } }
                }

                if (searchVisible) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).testTag("library_search_input"),
                        singleLine = true,
                        placeholder = { Text("Search videos, filenames or folders") },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) TextButton(onClick = { onQuery("") }, modifier = Modifier.testTag("clear_search_button")) { Text("×") }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    )
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                if (state.loading) Box(Modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                when {
                    state.section == LibrarySection.FOLDERS && state.selectedFolderKey == null -> ReleaseFolderBrowser(
                        state = state,
                        onOpenFolder = onOpenFolder,
                        onExcludeFolder = onExcludeFolder,
                        onRestoreFolder = onRestoreFolder,
                        onRemoveSource = onRemoveSource,
                        onOpenFile = { fileLauncher.launch(arrayOf("video/*", "audio/*")) },
                        onAddFolder = { treeLauncher.launch(null) },
                    )
                    state.section == LibrarySection.PLAYLISTS && state.selectedPlaylistId == null -> ReleasePlaylistsRoot(
                        playlists = state.playlists,
                        onCreatePlaylist = onCreatePlaylist,
                        onOpenPlaylist = onOpenPlaylist,
                    )
                    else -> {
                        if (selectedPlaylist != null) ReleasePlaylistActions(selectedPlaylist, onRenamePlaylist, onDeletePlaylist)
                        ReleaseMediaCollection(
                            state = state,
                            thumbnailRepository = thumbnailRepository,
                            onPlay = { onPlay(playbackRequest(it)) },
                            onToggleFavourite = onToggleFavourite,
                            onSetFavourite = onSetFavourite,
                            onAddToPlaylist = onAddToPlaylist,
                            onAddManyToPlaylist = onAddManyToPlaylist,
                            onRemoveFromPlaylist = onRemoveFromPlaylist,
                            onMovePlaylistItem = onMovePlaylistItem,
                            onDeleteHistory = onDeleteHistory,
                            onRequestDelete = onRequestDelete,
                            onRequestRename = onRequestRename,
                            onRequestRelink = { media -> pendingRelink = media; relinkLauncher.launch(arrayOf("video/*")) },
                            onOpenFile = { fileLauncher.launch(arrayOf("video/*", "audio/*")) },
                            onAddFolder = { treeLauncher.launch(null) },
                        )
                    }
                }
            }

            val firstPlayable = state.media.firstOrNull { it.availability == SourceAvailability.AVAILABLE }
            if (firstPlayable != null) {
                FloatingActionButton(
                    onClick = { onPlay(playbackRequest(firstPlayable)) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp).testTag("library_floating_play"),
                ) { Text("▶", fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, modifier = Modifier.testTag(tag), label = { Text(label, maxLines = 1) })
}

@Composable
private fun ReleaseFolderBrowser(
    state: LibraryUiState,
    onOpenFolder: (String) -> Unit,
    onExcludeFolder: (FolderItem) -> Unit,
    onRestoreFolder: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onOpenFile: () -> Unit,
    onAddFolder: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("folder_list"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp),
    ) {
        if (state.folders.isEmpty()) item { ReleaseEmptyState("No video folders found", "Add a folder or open a video to get started.", onOpenFile, onAddFolder) }
        items(state.folders, key = { it.key }) { folder ->
            var menu by remember(folder.key) { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenFolder(folder.key) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(116.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text("▰", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(folder.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${folder.videos.size} videos", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 22.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpenFolder(folder.key) })
                        DropdownMenuItem(text = { Text("Exclude folder") }, onClick = { menu = false; onExcludeFolder(folder) })
                    }
                }
            }
            HorizontalDivider()
        }
        if (state.sources.isNotEmpty()) {
            item { Text("Added folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)) }
            items(state.sources, key = { it.id }) { source ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(source.status.replace('_', ' '), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onRemoveSource(source.id) }) { Text("Remove") }
                }
            }
        }
        if (state.excludedFolders.isNotEmpty()) {
            item { Text("Excluded folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)) }
            items(state.excludedFolders, key = { it.folderKey }) { excluded ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(excluded.displayName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { onRestoreFolder(excluded.folderKey) }) { Text("Restore") }
                }
            }
        }
    }
}

@Composable
private fun ReleasePlaylistsRoot(playlists: List<PlaylistEntity>, onCreatePlaylist: (String) -> Unit, onOpenPlaylist: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New playlist") }, singleLine = true, modifier = Modifier.weight(1f).testTag("new_playlist_input"))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (name.isNotBlank()) { onCreatePlaylist(name); name = "" } }, enabled = name.isNotBlank(), modifier = Modifier.testTag("create_playlist_button")) { Text("Create") }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("playlist_list"), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (playlists.isEmpty()) item { Text("No playlists yet.", modifier = Modifier.padding(18.dp)) }
            items(playlists, key = { it.id }) { playlist ->
                Row(Modifier.fillMaxWidth().clickable { onOpenPlaylist(playlist.id) }.padding(vertical = 14.dp)) {
                    Text(playlist.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("›")
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ReleasePlaylistActions(playlist: PlaylistEntity, onRename: (Long, String) -> Unit, onDelete: (Long) -> Unit) {
    var menu by remember(playlist.id) { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var rename by remember(playlist.id, playlist.name) { mutableStateOf(playlist.name) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
        Box {
            TextButton(onClick = { menu = true }) { Text("Playlist options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameOpen = true })
                DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; deleteOpen = true })
            }
        }
    }
    if (renameOpen) AlertDialog(
        onDismissRequest = { renameOpen = false },
        title = { Text("Rename playlist") },
        text = { OutlinedTextField(rename, { rename = it }, singleLine = true) },
        confirmButton = { Button(onClick = { renameOpen = false; if (rename.isNotBlank()) onRename(playlist.id, rename) }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
    )
    if (deleteOpen) AlertDialog(
        onDismissRequest = { deleteOpen = false },
        title = { Text("Delete playlist?") },
        text = { Text("Video files are not deleted.") },
        confirmButton = { Button(onClick = { deleteOpen = false; onDelete(playlist.id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ReleaseMediaCollection(
    state: LibraryUiState,
    thumbnailRepository: ThumbnailRepository,
    onPlay: (AppMedia) -> Unit,
    onToggleFavourite: (AppMedia) -> Unit,
    onSetFavourite: (AppMedia, Boolean) -> Unit,
    onAddToPlaylist: (Long, AppMedia) -> Unit,
    onAddManyToPlaylist: (Long, Collection<AppMedia>) -> Unit,
    onRemoveFromPlaylist: (Long, AppMedia) -> Unit,
    onMovePlaylistItem: (Long, AppMedia, Int) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onRequestDelete: (AppMedia) -> Unit,
    onRequestRename: (AppMedia, String) -> Unit,
    onRequestRelink: (AppMedia) -> Unit,
    onOpenFile: () -> Unit,
    onAddFolder: () -> Unit,
) {
    var selectedIds by remember(state.section, state.selectedFolderKey, state.selectedPlaylistId) { mutableStateOf(emptySet<String>()) }
    var batchPlaylistMenu by remember { mutableStateOf(false) }
    val selectedMedia = state.media.filter { it.stableId in selectedIds }

    if (state.media.isEmpty()) {
        ReleaseEmptyState(
            if (state.query.isNotBlank()) "No matching videos" else emptyMessage(state.section),
            if (state.query.isNotBlank()) "Try another title, filename or folder." else "Add a folder or open a video to get started.",
            onOpenFile,
            onAddFolder,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (selectedIds.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("multi_select_bar").padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { Text("${selectedIds.size} selected", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp)) }
                item { TextButton(onClick = { selectedMedia.forEach { onSetFavourite(it, true) } }) { Text("Favourite") } }
                item { TextButton(onClick = { selectedMedia.forEach { onSetFavourite(it, false) } }) { Text("Unfavourite") } }
                if (state.playlists.isNotEmpty()) item {
                    Box {
                        TextButton(onClick = { batchPlaylistMenu = true }) { Text("Add to playlist") }
                        DropdownMenu(expanded = batchPlaylistMenu, onDismissRequest = { batchPlaylistMenu = false }) {
                            state.playlists.forEach { playlist ->
                                DropdownMenuItem(text = { Text(playlist.name) }, onClick = {
                                    batchPlaylistMenu = false
                                    onAddManyToPlaylist(playlist.id, selectedMedia)
                                    selectedIds = emptySet()
                                })
                            }
                        }
                    }
                }
                item { TextButton(onClick = { selectedIds = emptySet() }) { Text("Cancel") } }
            }
            HorizontalDivider()
        }

        val historyById = state.history.associateBy { it.stableMediaId }
        if (state.viewMode == LibraryViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(172.dp),
                modifier = Modifier.fillMaxSize().testTag("library_grid"),
                contentPadding = PaddingValues(8.dp, 6.dp, 8.dp, 88.dp),
            ) {
                gridItems(state.media, key = { it.stableId }) { media ->
                    ReleaseMediaGridItem(
                        media = media,
                        history = historyById[media.stableId],
                        favourite = media.stableId in state.favouriteIds,
                        selected = media.stableId in selectedIds,
                        state = state,
                        thumbnailRepository = thumbnailRepository,
                        onPlay = onPlay,
                        onToggleFavourite = onToggleFavourite,
                        onToggleSelection = { selectedIds = selectedIds.toggle(media.stableId) },
                        onAddToPlaylist = onAddToPlaylist,
                        onRemoveFromPlaylist = onRemoveFromPlaylist,
                        onMovePlaylistItem = onMovePlaylistItem,
                        onDeleteHistory = onDeleteHistory,
                        onRequestDelete = onRequestDelete,
                        onRequestRename = onRequestRename,
                        onRequestRelink = onRequestRelink,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("library_list"),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp),
            ) {
                items(state.media, key = { it.stableId }) { media ->
                    ReleaseMediaRow(
                        media = media,
                        history = historyById[media.stableId],
                        favourite = media.stableId in state.favouriteIds,
                        selected = media.stableId in selectedIds,
                        state = state,
                        thumbnailRepository = thumbnailRepository,
                        onPlay = onPlay,
                        onToggleFavourite = onToggleFavourite,
                        onToggleSelection = { selectedIds = selectedIds.toggle(media.stableId) },
                        onAddToPlaylist = onAddToPlaylist,
                        onRemoveFromPlaylist = onRemoveFromPlaylist,
                        onMovePlaylistItem = onMovePlaylistItem,
                        onDeleteHistory = onDeleteHistory,
                        onRequestDelete = onRequestDelete,
                        onRequestRename = onRequestRename,
                        onRequestRelink = onRequestRelink,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ReleaseMediaRow(
    media: AppMedia,
    history: MediaHistoryEntity?,
    favourite: Boolean,
    selected: Boolean,
    state: LibraryUiState,
    thumbnailRepository: ThumbnailRepository,
    onPlay: (AppMedia) -> Unit,
    onToggleFavourite: (AppMedia) -> Unit,
    onToggleSelection: () -> Unit,
    onAddToPlaylist: (Long, AppMedia) -> Unit,
    onRemoveFromPlaylist: (Long, AppMedia) -> Unit,
    onMovePlaylistItem: (Long, AppMedia, Int) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onRequestDelete: (AppMedia) -> Unit,
    onRequestRename: (AppMedia, String) -> Unit,
    onRequestRelink: (AppMedia) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("media_${media.stableId}").clickable(enabled = media.availability == SourceAvailability.AVAILABLE) { onPlay(media) }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReleaseThumbnail(media, thumbnailRepository, Modifier.width(136.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(media.title, maxLines = 3, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            media.dateModifiedMs?.let { Text(formatDate(it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            val meta = buildList {
                if (media.width != null && media.height != null) add("${media.width}×${media.height}")
                media.sizeBytes?.let { add(formatBytes(it)) }
            }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            history?.takeIf { it.durationMs > 0L && it.lastPositionMs > 0L }?.let { Text("${progressPercent(it)}% watched", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
            if (media.availability != SourceAvailability.AVAILABLE) Text("Source ${media.availability.name.lowercase().replace('_', ' ')}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        ReleaseMediaMenu(media, history, favourite, selected, state, onPlay, onToggleFavourite, onToggleSelection, onAddToPlaylist, onRemoveFromPlaylist, onMovePlaylistItem, onDeleteHistory, onRequestDelete, onRequestRename, onRequestRelink)
    }
}

@Composable
private fun ReleaseMediaGridItem(
    media: AppMedia,
    history: MediaHistoryEntity?,
    favourite: Boolean,
    selected: Boolean,
    state: LibraryUiState,
    thumbnailRepository: ThumbnailRepository,
    onPlay: (AppMedia) -> Unit,
    onToggleFavourite: (AppMedia) -> Unit,
    onToggleSelection: () -> Unit,
    onAddToPlaylist: (Long, AppMedia) -> Unit,
    onRemoveFromPlaylist: (Long, AppMedia) -> Unit,
    onMovePlaylistItem: (Long, AppMedia, Int) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onRequestDelete: (AppMedia) -> Unit,
    onRequestRename: (AppMedia, String) -> Unit,
    onRequestRelink: (AppMedia) -> Unit,
) {
    Surface(modifier = Modifier.padding(5.dp).testTag("media_${media.stableId}"), shape = RoundedCornerShape(12.dp), tonalElevation = if (selected) 2.dp else 0.dp) {
        Column(Modifier.clickable(enabled = media.availability == SourceAvailability.AVAILABLE) { onPlay(media) }.padding(6.dp)) {
            ReleaseThumbnail(media, thumbnailRepository, Modifier.fillMaxWidth())
            Text(media.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(media.dateModifiedMs?.let(::formatDate).orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReleaseMediaMenu(media, history, favourite, selected, state, onPlay, onToggleFavourite, onToggleSelection, onAddToPlaylist, onRemoveFromPlaylist, onMovePlaylistItem, onDeleteHistory, onRequestDelete, onRequestRename, onRequestRelink)
            }
        }
    }
}

@Composable
private fun ReleaseMediaMenu(
    media: AppMedia,
    history: MediaHistoryEntity?,
    favourite: Boolean,
    selected: Boolean,
    state: LibraryUiState,
    onPlay: (AppMedia) -> Unit,
    onToggleFavourite: (AppMedia) -> Unit,
    onToggleSelection: () -> Unit,
    onAddToPlaylist: (Long, AppMedia) -> Unit,
    onRemoveFromPlaylist: (Long, AppMedia) -> Unit,
    onMovePlaylistItem: (Long, AppMedia, Int) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onRequestDelete: (AppMedia) -> Unit,
    onRequestRename: (AppMedia, String) -> Unit,
    onRequestRelink: (AppMedia) -> Unit,
) {
    var menu by remember(media.stableId) { mutableStateOf(false) }
    var playlistMenu by remember(media.stableId) { mutableStateOf(false) }
    var detailsOpen by remember(media.stableId) { mutableStateOf(false) }
    var renameOpen by remember(media.stableId) { mutableStateOf(false) }
    var deleteOpen by remember(media.stableId) { mutableStateOf(false) }
    var renameText by remember(media.stableId, media.fileName) { mutableStateOf(media.fileName ?: media.title) }
    val selectedPlaylistId = state.selectedPlaylistId

    Box {
        TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 22.sp) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Play") }, enabled = media.availability == SourceAvailability.AVAILABLE, onClick = { menu = false; onPlay(media) })
            DropdownMenuItem(text = { Text(if (selected) "Unselect" else "Select") }, onClick = { menu = false; onToggleSelection() })
            DropdownMenuItem(text = { Text(if (favourite) "Remove favourite" else "Favourite") }, onClick = { menu = false; onToggleFavourite(media) })
            DropdownMenuItem(text = { Text("Information") }, onClick = { menu = false; detailsOpen = true })
            if (state.playlists.isNotEmpty()) DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; playlistMenu = true })
            if (selectedPlaylistId != null) {
                DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMovePlaylistItem(selectedPlaylistId, media, -1) })
                DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMovePlaylistItem(selectedPlaylistId, media, 1) })
                DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { menu = false; onRemoveFromPlaylist(selectedPlaylistId, media) })
            }
            if (history != null) DropdownMenuItem(text = { Text("Remove history") }, onClick = { menu = false; onDeleteHistory(media.stableId) })
            if (media.sourceType != MediaSourceType.NETWORK && media.availability == SourceAvailability.AVAILABLE) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameOpen = true })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleteOpen = true })
            }
            if (media.sourceType != MediaSourceType.NETWORK && media.availability != SourceAvailability.AVAILABLE) {
                DropdownMenuItem(text = { Text("Locate original") }, onClick = { menu = false; onRequestRelink(media) })
            }
        }
        DropdownMenu(expanded = playlistMenu, onDismissRequest = { playlistMenu = false }) {
            state.playlists.forEach { playlist -> DropdownMenuItem(text = { Text(playlist.name) }, onClick = { playlistMenu = false; onAddToPlaylist(playlist.id, media) }) }
        }
    }

    if (detailsOpen) AlertDialog(onDismissRequest = { detailsOpen = false }, title = { Text("Media information") }, text = { ReleaseMediaDetails(media, history) }, confirmButton = { TextButton(onClick = { detailsOpen = false }) { Text("Close") } })
    if (renameOpen) AlertDialog(
        onDismissRequest = { renameOpen = false },
        title = { Text("Rename video") },
        text = { OutlinedTextField(renameText, { renameText = it }, label = { Text("File name") }, singleLine = true) },
        confirmButton = { Button(onClick = { renameOpen = false; onRequestRename(media, renameText) }, enabled = renameText.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
    )
    if (deleteOpen) AlertDialog(
        onDismissRequest = { deleteOpen = false },
        title = { Text("Delete actual video file?") },
        text = { Text("Android may show an additional system confirmation. Playback history is separate.") },
        confirmButton = { Button(onClick = { deleteOpen = false; onRequestDelete(media) }) { Text("Delete file") } },
        dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ReleaseThumbnail(media: AppMedia, repository: ThumbnailRepository, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, media.stableId) { value = repository.load(media, 360, 203) }
    Box(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("Video", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        media.durationMs?.let { duration ->
            Text(
                formatDuration(duration),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ReleaseMediaDetails(media: AppMedia, history: MediaHistoryEntity?) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        DetailRow("Title", media.title)
        DetailRow("Filename", media.fileName)
        DetailRow("Folder", media.folderName ?: media.relativePath)
        DetailRow("Source", media.sourceType.name)
        DetailRow("Duration", media.durationMs?.let(::formatDuration))
        DetailRow("Resolution", if (media.width != null && media.height != null) "${media.width}×${media.height}" else null)
        DetailRow("Frame rate", media.frameRate?.let { "%.2f fps".format(it) })
        DetailRow("Video codec", media.videoCodec)
        DetailRow("Audio codec", media.audioCodec)
        DetailRow("Size", media.sizeBytes?.let(::formatBytes))
        DetailRow("Availability", media.availability.name)
        history?.let { DetailRow("Progress", "${formatDuration(it.lastPositionMs)} / ${formatDuration(it.durationMs)}") }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (!value.isNullOrBlank()) Text("$label: $value", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ReleaseEmptyState(title: String, subtitle: String, onOpenFile: () -> Unit, onAddFolder: () -> Unit) {
    Box(Modifier.fillMaxSize().testTag("library_empty_state"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenFile, modifier = Modifier.testTag("open_file_button")) { Text("Open file") }
                TextButton(onClick = onAddFolder, modifier = Modifier.testTag("add_folder_button")) { Text("Add folder") }
            }
        }
    }
}

private fun VideoSort.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun FolderSort.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun LibraryFilter.label() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun VideoSort.next(): VideoSort = VideoSort.entries[(ordinal + 1) % VideoSort.entries.size]
private fun FolderSort.next(): FolderSort = FolderSort.entries[(ordinal + 1) % FolderSort.entries.size]
private fun LibraryFilter.next(): LibraryFilter = LibraryFilter.entries[(ordinal + 1) % LibraryFilter.entries.size]
private fun emptyMessage(section: LibrarySection) = when (section) {
    LibrarySection.VIDEOS -> "No videos found"
    LibrarySection.CONTINUE_WATCHING -> "Nothing to continue watching"
    LibrarySection.RECENT -> "No recently played videos"
    LibrarySection.FAVOURITES -> "No favourites yet"
    LibrarySection.HISTORY -> "Playback history is empty"
    LibrarySection.PLAYLISTS -> "This playlist is empty"
    LibrarySection.FOLDERS -> "This folder is empty"
}

private fun formatDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
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

private fun formatDate(epochMs: Long): String = runCatching { java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(epochMs)) }.getOrDefault(epochMs.toString())

private fun progressPercent(history: MediaHistoryEntity): Int {
    if (history.durationMs <= 0L) return 0
    return ((history.lastPositionMs.coerceIn(0L, history.durationMs).toDouble() / history.durationMs.toDouble()) * 100.0).toInt().coerceIn(0, 100)
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

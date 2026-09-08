package com.zubaer.maxvideoplayer.feature.network.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntry
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntryType
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol

private enum class NetworkPage { HOME, OPEN_STREAM, EDIT_LOCATION }

@Composable
fun NetworkScreen(
    state: NetworkUiState,
    viewModel: NetworkViewModel,
    onBack: () -> Unit,
    onPlay: (AppMedia) -> Unit,
    onPlayQueue: (List<AppMedia>) -> Unit,
) {
    var page by remember { mutableStateOf(NetworkPage.HOME) }
    var draft by remember { mutableStateOf(NetworkLocationDraft()) }

    BackHandler {
        when {
            state.currentLocation != null -> viewModel.closeBrowser()
            page != NetworkPage.HOME -> page = NetworkPage.HOME
            else -> onBack()
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    when {
                        state.currentLocation != null -> viewModel.closeBrowser()
                        page != NetworkPage.HOME -> page = NetworkPage.HOME
                        else -> onBack()
                    }
                }) { Text("← Back") }
                Column(Modifier.weight(1f)) {
                    Text("Network", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Stream and browse without downloading the full media", style = MaterialTheme.typography.bodySmall)
                }
            }

            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("network_error")) }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).testTag("network_loading"))

            when {
                state.currentLocation != null -> NetworkBrowser(state, viewModel, onPlay, onPlayQueue)
                page == NetworkPage.OPEN_STREAM -> OpenStreamForm(
                    onCancel = { page = NetworkPage.HOME },
                    onOpen = { url, title, username, password, token, userAgent, referer ->
                        viewModel.openDirect(url, title, username, password, token, userAgent, referer, onPlay)
                    },
                )
                page == NetworkPage.EDIT_LOCATION -> LocationEditor(
                    draft = draft,
                    onDraft = { draft = it },
                    onTest = viewModel::test,
                    onSave = { value -> viewModel.save(value) { page = NetworkPage.HOME } },
                    onCancel = { page = NetworkPage.HOME },
                )
                else -> NetworkHome(
                    state = state,
                    onOpenStream = { page = NetworkPage.OPEN_STREAM },
                    onAdd = { draft = NetworkLocationDraft(); page = NetworkPage.EDIT_LOCATION },
                    onBrowse = { location -> viewModel.open(location) },
                    onPlayLocation = { location -> viewModel.playLocation(location, onPlay) },
                    onEdit = { location -> draft = NetworkLocationDraft.from(location); page = NetworkPage.EDIT_LOCATION },
                    onTest = viewModel::testSaved,
                    onForget = viewModel::forgetCredentials,
                    onRemove = viewModel::remove,
                    onRecent = { viewModel.playRecent(it, onPlay) },
                )
            }
        }
    }
}

@Composable
private fun NetworkHome(
    state: NetworkUiState,
    onOpenStream: () -> Unit,
    onAdd: () -> Unit,
    onBrowse: (NetworkLocation) -> Unit,
    onPlayLocation: (NetworkLocation) -> Unit,
    onEdit: (NetworkLocation) -> Unit,
    onTest: (NetworkLocation) -> Unit,
    onForget: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRecent: (com.zubaer.maxvideoplayer.core.database.MediaHistoryEntity) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenStream, modifier = Modifier.testTag("open_network_stream")) { Text("Open Stream") }
                OutlinedButton(onClick = onAdd, modifier = Modifier.testTag("add_network_location")) { Text("Add Location") }
            }
        }
        item { SectionTitle("Saved Locations") }
        if (state.locations.isEmpty()) item { Text("No saved network locations.", style = MaterialTheme.typography.bodyMedium) }
        items(state.locations, key = { it.id }) { location ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(location.displayName, fontWeight = FontWeight.SemiBold)
                    Text("${location.protocol.name} • ${location.host}:${location.port} • ${location.usernameHint ?: if (location.useGuest) "Guest" else "No saved login"}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (location.protocol.browsable) TextButton(onClick = { onBrowse(location) }) { Text("Browse") }
                        else TextButton(onClick = { onPlayLocation(location) }) { Text("Play") }
                        TextButton(onClick = { onTest(location) }) { Text("Test") }
                        TextButton(onClick = { onEdit(location) }) { Text("Edit") }
                        if (location.credentialRef != null) TextButton(onClick = { onForget(location.id) }) { Text("Forget login") }
                        TextButton(onClick = { onRemove(location.id) }) { Text("Remove") }
                    }
                }
            }
        }
        item { SectionTitle("Recent Network Media") }
        if (state.recent.isEmpty()) item { Text("Network playback history will appear here.") }
        items(state.recent, key = { it.stableMediaId }) { history ->
            Row(
                Modifier.fillMaxWidth().clickable { onRecent(history) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(history.title, maxLines = 1)
                    Text("Resume at ${formatTime(history.lastPositionMs)}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Play")
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun OpenStreamForm(
    onCancel: () -> Unit,
    onOpen: (String, String, String, String, String, String, String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var insecureAcknowledged by remember { mutableStateOf(false) }
    val insecure = url.trim().startsWith("http://", ignoreCase = true)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Open Network Stream") }
        item { Text("Supports HTTP/HTTPS, HLS (.m3u8), DASH (.mpd), and RTSP.") }
        item { OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().testTag("stream_url"), label = { Text("URL") }, singleLine = true) }
        item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Display title (optional)") }, singleLine = true) }
        if (insecure) {
            item { Text("This HTTP connection is not encrypted. Passwords, tokens, and media traffic can be intercepted.", color = MaterialTheme.colorScheme.error) }
            item { SettingSwitch("I understand the HTTP security risk", insecureAcknowledged) { insecureAcknowledged = it } }
        }
        item { TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced options" else "Advanced authentication and headers") } }
        if (advanced) {
            item { OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username (optional)") }, singleLine = true) }
            item {
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password (optional)") }, singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") } },
                )
            }
            item { OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Bearer token (optional)") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) }
            item { OutlinedTextField(userAgent, { userAgent = it }, Modifier.fillMaxWidth(), label = { Text("Custom User-Agent (optional)") }, singleLine = true) }
            item { OutlinedTextField(referer, { referer = it }, Modifier.fillMaxWidth(), label = { Text("Referer (optional)") }, singleLine = true) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(url, title, username, password, token, userAgent, referer) }, enabled = url.isNotBlank() && (!insecure || insecureAcknowledged)) { Text("Play") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun LocationEditor(
    draft: NetworkLocationDraft,
    onDraft: (NetworkLocationDraft) -> Unit,
    onTest: (NetworkLocationDraft) -> Unit,
    onSave: (NetworkLocationDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var advanced by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    val protocols = listOf(NetworkProtocol.SMB, NetworkProtocol.WEBDAV, NetworkProtocol.FTP, NetworkProtocol.FTPS, NetworkProtocol.HTTPS)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle(if (draft.id == null) "Add Network Location" else "Edit Network Location") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(protocols) { protocol ->
                    FilterChip(
                        selected = draft.protocol == protocol,
                        onClick = { onDraft(draft.copy(protocol = protocol, port = protocol.defaultPort.toString())) },
                        label = { Text(protocol.name) },
                    )
                }
            }
        }
        item { OutlinedTextField(draft.displayName, { onDraft(draft.copy(displayName = it)) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true) }
        item { OutlinedTextField(draft.host, { onDraft(draft.copy(host = it)) }, Modifier.fillMaxWidth().testTag("network_host"), label = { Text("Server host") }, singleLine = true) }
        item {
            OutlinedTextField(
                draft.port, { onDraft(draft.copy(port = it.filter(Char::isDigit))) }, Modifier.fillMaxWidth(), label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        item {
            OutlinedTextField(
                draft.basePath, { onDraft(draft.copy(basePath = it)) }, Modifier.fillMaxWidth(),
                label = { Text(if (draft.protocol == NetworkProtocol.SMB) "Share / base folder" else "Base path") }, singleLine = true,
            )
        }
        if (draft.protocol == NetworkProtocol.SMB) item { SettingSwitch("Guest access", draft.useGuest) { onDraft(draft.copy(useGuest = it)) } }
        if (!draft.useGuest) {
            item { OutlinedTextField(draft.username, { onDraft(draft.copy(username = it)) }, Modifier.fillMaxWidth(), label = { Text("Username (optional)") }, singleLine = true) }
            item {
                OutlinedTextField(
                    draft.password, { onDraft(draft.copy(password = it)) }, Modifier.fillMaxWidth(), label = { Text("Password (optional)") }, singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") } },
                )
            }
        }
        if (draft.protocol == NetworkProtocol.SMB) item { OutlinedTextField(draft.domain, { onDraft(draft.copy(domain = it)) }, Modifier.fillMaxWidth(), label = { Text("Domain / workgroup (optional)") }, singleLine = true) }
        if (draft.protocol in setOf(NetworkProtocol.FTP, NetworkProtocol.FTPS)) item { SettingSwitch("Passive mode", draft.ftpPassiveMode) { onDraft(draft.copy(ftpPassiveMode = it)) } }
        if (draft.protocol == NetworkProtocol.FTP) {
            item { Text("FTP does not encrypt your password or media traffic. Prefer FTPS, WebDAV over HTTPS, SMB3, or HTTPS.", color = MaterialTheme.colorScheme.error) }
            item { SettingSwitch("I understand the FTP security risk", draft.ftpSecurityAcknowledged) { onDraft(draft.copy(ftpSecurityAcknowledged = it)) } }
        }
        item { SettingSwitch("Remember credentials securely", draft.rememberCredential) { onDraft(draft.copy(rememberCredential = it)) } }
        item { TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced HTTP options" else "Advanced HTTP options") } }
        if (advanced && draft.protocol in setOf(NetworkProtocol.WEBDAV, NetworkProtocol.HTTPS)) {
            item { OutlinedTextField(draft.bearerToken, { onDraft(draft.copy(bearerToken = it)) }, Modifier.fillMaxWidth(), label = { Text("Bearer token") }, visualTransformation = PasswordVisualTransformation()) }
            item { OutlinedTextField(draft.userAgent, { onDraft(draft.copy(userAgent = it)) }, Modifier.fillMaxWidth(), label = { Text("Custom User-Agent") }) }
            item { OutlinedTextField(draft.referer, { onDraft(draft.copy(referer = it)) }, Modifier.fillMaxWidth(), label = { Text("Referer") }) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTest(draft) }, enabled = draft.host.isNotBlank()) { Text("Test Connection") }
                Button(onClick = { onSave(draft) }, enabled = draft.host.isNotBlank() && draft.displayName.isNotBlank()) { Text("Save") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun NetworkBrowser(
    state: NetworkUiState,
    viewModel: NetworkViewModel,
    onPlay: (AppMedia) -> Unit,
    onPlayQueue: (List<AppMedia>) -> Unit,
) {
    val location = state.currentLocation ?: return
    var query by remember(location.id) { mutableStateOf("") }
    val visibleEntries = remember(state.entries, query) {
        if (query.isBlank()) state.entries else state.entries.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        Text(location.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (state.currentPath.isBlank()) location.displayName else "${location.displayName} > ${state.currentPath.split('/').joinToString(" > ")}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { if (state.currentPath.isBlank()) viewModel.closeBrowser() else viewModel.up() }) { Text("↑ Up") }
            TextButton(onClick = { viewModel.open(location, state.currentPath) }) { Text("Refresh") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().testTag("network_search"),
            label = { Text("Search this folder") },
            singleLine = true,
        )
        LazyColumn(Modifier.fillMaxSize().testTag("network_entries")) {
            if (!state.loading && visibleEntries.isEmpty()) item { Text(if (query.isBlank()) "This folder is empty or contains no listed items." else "No matching entries.", modifier = Modifier.padding(16.dp)) }
            items(visibleEntries, key = { "${it.type}:${it.remotePath}" }) { entry ->
                NetworkEntryRow(entry) { viewModel.openEntry(entry, onPlay, onPlayQueue) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NetworkEntryRow(entry: NetworkEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (entry.isDirectory) "Folder" else entry.type.label(), modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelMedium)
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal)
            if (!entry.isDirectory) Text(entry.sizeBytes?.let(::formatBytes) ?: "Unknown size", style = MaterialTheme.typography.bodySmall)
        }
        Text(if (entry.isDirectory) "Open" else "Play")
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun NetworkEntryType.label() = when (this) {
    NetworkEntryType.DIRECTORY -> "Folder"
    NetworkEntryType.VIDEO -> "Video"
    NetworkEntryType.AUDIO -> "Audio"
    NetworkEntryType.SUBTITLE -> "Subtitle"
    NetworkEntryType.PLAYLIST -> "Stream"
    NetworkEntryType.OTHER -> "File"
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes B"
}

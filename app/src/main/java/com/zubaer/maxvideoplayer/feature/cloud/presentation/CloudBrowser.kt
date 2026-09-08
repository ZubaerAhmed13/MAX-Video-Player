package com.zubaer.maxvideoplayer.feature.cloud.presentation

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.MediaSourceType
import com.zubaer.maxvideoplayer.feature.cloud.auth.CloudOAuthCoordinator
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudAccount
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudAuthState
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudEntry
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudMediaPolicy
import com.zubaer.maxvideoplayer.feature.cloud.model.CloudProvider
import com.zubaer.maxvideoplayer.feature.cloud.playback.CloudUriCodec
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CloudBrowserState(
    val accounts: List<CloudAccount> = emptyList(),
    val providerStates: Map<CloudProvider, CloudAuthState> = emptyMap(),
    val selectedAccountId: String? = null,
    val folderStack: List<Pair<String?, String>> = emptyList(),
    val entries: List<CloudEntry> = emptyList(),
    val nextPageToken: String? = null,
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class CloudBrowserViewModel(
    private val coordinator: CloudOAuthCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(CloudBrowserState())
    val state: StateFlow<CloudBrowserState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            coordinator.restoreRegisteredAccounts()
            coordinator.accounts.collectLatest { accounts ->
                _state.value = _state.value.copy(accounts = accounts)
                val selected = _state.value.selectedAccountId
                if (selected == null && accounts.isNotEmpty()) selectAccount(accounts.first().id)
            }
        }
        viewModelScope.launch {
            coordinator.uiState.collectLatest { auth ->
                _state.value = _state.value.copy(providerStates = auth.providerStates, error = auth.error ?: _state.value.error)
            }
        }
    }

    fun selectAccount(accountId: String) {
        _state.value = _state.value.copy(
            selectedAccountId = accountId,
            folderStack = listOf(null to "Root"),
            query = "",
            entries = emptyList(),
            nextPageToken = null,
        )
        load(reset = true)
    }

    fun openFolder(entry: CloudEntry) {
        if (!entry.isFolder) return
        _state.value = _state.value.copy(
            folderStack = _state.value.folderStack + (entry.identity.providerFileId to entry.name),
            query = "",
            entries = emptyList(),
            nextPageToken = null,
        )
        load(reset = true)
    }

    fun upFolder() {
        if (_state.value.folderStack.size <= 1) return
        _state.value = _state.value.copy(
            folderStack = _state.value.folderStack.dropLast(1),
            query = "",
            entries = emptyList(),
            nextPageToken = null,
        )
        load(reset = true)
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun search() = load(reset = true)

    fun refresh() = load(reset = true)

    fun loadMore() {
        if (_state.value.nextPageToken != null) load(reset = false)
    }

    fun disconnect(accountId: String) {
        viewModelScope.launch {
            coordinator.disconnect(accountId)
            if (_state.value.selectedAccountId == accountId) {
                _state.value = _state.value.copy(
                    selectedAccountId = null,
                    folderStack = emptyList(),
                    entries = emptyList(),
                    nextPageToken = null,
                )
            }
        }
    }

    fun clearError() {
        coordinator.clearError()
        _state.value = _state.value.copy(error = null)
    }

    private fun load(reset: Boolean) {
        val accountId = _state.value.selectedAccountId ?: return
        val requestedQuery = _state.value.query.trim()
        val parentId = _state.value.folderStack.lastOrNull()?.first
        val pageToken = if (reset) null else _state.value.nextPageToken ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val client = coordinator.providerClient(accountId)
                    ?: error("Cloud account is no longer connected.")
                if (requestedQuery.isNotBlank()) client.search(requestedQuery, pageToken, PAGE_SIZE)
                else client.browse(parentId, pageToken, PAGE_SIZE)
            }.onSuccess { page ->
                _state.value = _state.value.copy(
                    entries = if (reset) page.entries else _state.value.entries + page.entries,
                    nextPageToken = page.nextPageToken,
                    loading = false,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Cloud request failed.")
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}

@Composable
fun CloudBrowserScreen(
    state: CloudBrowserState,
    coordinator: CloudOAuthCoordinator,
    viewModel: CloudBrowserViewModel,
    onBack: () -> Unit,
    onPlay: (AppMedia) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var authLaunchError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("cloud_browser"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Cloud", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::refresh, enabled = state.selectedAccountId != null && !state.loading) { Text("Refresh") }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudProvider.entries.forEach { provider ->
                val authState = state.providerStates[provider] ?: CloudAuthState.SIGNED_OUT
                Button(
                    onClick = {
                        runCatching { coordinator.beginAuthorization(provider) }
                            .onSuccess { request ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, request.authorizationUri))
                                authLaunchError = null
                            }
                            .onFailure { authLaunchError = it.message }
                    },
                    enabled = authState != CloudAuthState.AUTHORIZING && authState != CloudAuthState.TOKEN_REFRESHING,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(providerLabel(provider) + if (authState == CloudAuthState.NOT_CONFIGURED) "\nNot configured" else "")
                }
            }
        }

        if (state.accounts.isNotEmpty()) {
            Text("Accounts", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.accounts.forEach { account ->
                    TextButton(onClick = { viewModel.selectAccount(account.id) }) {
                        Text((if (state.selectedAccountId == account.id) "✓ " else "") + account.displayName)
                    }
                }
            }
            state.accounts.firstOrNull { it.id == state.selectedAccountId }?.let { account ->
                Row(Modifier.fillMaxWidth()) {
                    Text(account.emailHint ?: account.provider.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { viewModel.disconnect(account.id) }) { Text("Disconnect") }
                }
            }
        }

        if (state.selectedAccountId != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Search this cloud") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("cloud_search"),
                )
                Button(onClick = viewModel::search, enabled = !state.loading) { Text("Search") }
            }
            if (state.folderStack.size > 1) {
                TextButton(onClick = viewModel::upFolder) { Text("↑ ${state.folderStack.dropLast(1).last().second}") }
            }
            Text(state.folderStack.joinToString(" / ") { it.second }, style = MaterialTheme.typography.bodySmall)
        }

        (state.error ?: authLaunchError)?.let { error ->
            Row(Modifier.fillMaxWidth()) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.clearError(); authLaunchError = null }) { Text("Dismiss") }
            }
        }

        if (state.loading && state.entries.isEmpty()) CircularProgressIndicator()

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(state.entries, key = { it.identity.stableId }) { entry ->
                Column(
                    Modifier.fillMaxWidth().clickable(enabled = entry.isFolder || CloudMediaPolicy.isPlayableCandidate(entry)) {
                        if (entry.isFolder) viewModel.openFolder(entry) else onPlay(entry.toAppMedia())
                    }.padding(vertical = 10.dp),
                ) {
                    Text((if (entry.isFolder) "📁 " else "▶ ") + entry.name)
                    val detail = listOfNotNull(entry.mimeType, entry.sizeBytes?.let(::formatBytes), entry.modifiedAt).joinToString(" · ")
                    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
                    if (!entry.isFolder && !CloudMediaPolicy.isPlayableCandidate(entry)) {
                        Text("Not a playable media item", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.nextPageToken != null) {
                item {
                    TextButton(onClick = viewModel::loadMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.loading) "Loading…" else "Load more")
                    }
                }
            }
        }
    }
}

fun CloudEntry.toAppMedia(): AppMedia = AppMedia(
    stableId = identity.stableId,
    uri = CloudUriCodec.encode(identity, revision).toString(),
    title = name,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    fileName = name,
    sourceId = "cloud:${identity.provider.name.lowercase()}:${identity.accountId}",
    sourceType = MediaSourceType.NETWORK,
)

private fun providerLabel(provider: CloudProvider): String = when (provider) {
    CloudProvider.GOOGLE_DRIVE -> "Google Drive"
    CloudProvider.ONEDRIVE -> "OneDrive"
    CloudProvider.DROPBOX -> "Dropbox"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes B"
}

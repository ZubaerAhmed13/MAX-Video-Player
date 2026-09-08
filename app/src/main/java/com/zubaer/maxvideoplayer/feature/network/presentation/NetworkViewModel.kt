package com.zubaer.maxvideoplayer.feature.network.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zubaer.maxvideoplayer.core.database.MediaHistoryEntity
import com.zubaer.maxvideoplayer.core.database.PlaybackHistoryRepository
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.feature.network.model.NetworkCredential
import com.zubaer.maxvideoplayer.feature.network.model.NetworkEntry
import com.zubaer.maxvideoplayer.feature.network.model.NetworkLocation
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
import com.zubaer.maxvideoplayer.feature.network.repository.NetworkRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NetworkLocationDraft(
    val id: String? = null,
    val displayName: String = "",
    val protocol: NetworkProtocol = NetworkProtocol.SMB,
    val host: String = "",
    val port: String = NetworkProtocol.SMB.defaultPort.toString(),
    val basePath: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val bearerToken: String = "",
    val userAgent: String = "",
    val referer: String = "",
    val rememberCredential: Boolean = true,
    val useGuest: Boolean = false,
    val ftpPassiveMode: Boolean = true,
    val ftpSecurityAcknowledged: Boolean = false,
) {
    fun location(existing: NetworkLocation? = null): NetworkLocation {
        val parsedPort = port.toIntOrNull() ?: error("Enter a valid port")
        if (protocol == NetworkProtocol.FTP && password.isNotBlank() && !ftpSecurityAcknowledged) {
            error("Acknowledge the FTP security warning before saving a password")
        }
        return NetworkLocation(
            id = id ?: existing?.id ?: java.util.UUID.randomUUID().toString(),
            displayName = displayName.trim().ifBlank { host.trim() },
            protocol = protocol,
            host = host.trim(),
            port = parsedPort,
            basePath = NetworkUriPolicy.safeRemotePath(basePath),
            credentialRef = existing?.credentialRef,
            usernameHint = username.trim().takeIf { it.isNotBlank() } ?: existing?.usernameHint,
            useGuest = useGuest,
            ftpPassiveMode = ftpPassiveMode,
            ftpSecurityAcknowledged = ftpSecurityAcknowledged,
            createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis(),
            lastConnectedAtMs = existing?.lastConnectedAtMs,
        )
    }

    fun credential(): NetworkCredential = NetworkCredential(
        username = username.trim(),
        password = password,
        domain = domain.trim(),
        bearerToken = bearerToken.trim(),
        headers = buildMap {
            if (userAgent.isNotBlank()) put("User-Agent", userAgent.trim())
            if (referer.isNotBlank()) put("Referer", referer.trim())
        },
    )

    companion object {
        fun from(location: NetworkLocation) = NetworkLocationDraft(
            id = location.id,
            displayName = location.displayName,
            protocol = location.protocol,
            host = location.host,
            port = location.port.toString(),
            basePath = location.basePath,
            username = location.usernameHint.orEmpty(),
            rememberCredential = location.credentialRef != null,
            useGuest = location.useGuest,
            ftpPassiveMode = location.ftpPassiveMode,
            ftpSecurityAcknowledged = location.ftpSecurityAcknowledged,
        )
    }
}

data class NetworkUiState(
    val locations: List<NetworkLocation> = emptyList(),
    val recent: List<MediaHistoryEntity> = emptyList(),
    val currentLocation: NetworkLocation? = null,
    val currentPath: String = "",
    val entries: List<NetworkEntry> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class NetworkViewModel(
    private val repository: NetworkRepository,
    historyRepository: PlaybackHistoryRepository,
) : ViewModel() {
    private val transient = MutableStateFlow(NetworkUiState())
    private var browseJob: Job? = null

    val state: StateFlow<NetworkUiState> = combine(
        repository.observeLocations(),
        historyRepository.recent(30),
        transient,
    ) { locations, history, ui ->
        ui.copy(
            locations = locations,
            recent = history.filter { row ->
                val scheme = runCatching { android.net.Uri.parse(row.uri).scheme?.lowercase() }.getOrNull()
                scheme in setOf("http", "https", "rtsp", "ftp", "ftps", "maxsmb")
            },
            currentLocation = ui.currentLocation?.let { current -> locations.firstOrNull { it.id == current.id } ?: current },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), NetworkUiState())

    fun save(draft: NetworkLocationDraft, onSaved: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val existing = state.value.locations.firstOrNull { it.id == draft.id }
                repository.saveLocation(draft.location(existing), draft.credential(), draft.rememberCredential)
            }.onSuccess {
                transient.value = transient.value.copy(message = "Network location saved", error = null)
                onSaved()
            }.onFailure { setError(it) }
        }
    }

    fun test(draft: NetworkLocationDraft) {
        viewModelScope.launch {
            transient.value = transient.value.copy(loading = true, message = null, error = null)
            val existing = state.value.locations.firstOrNull { it.id == draft.id }
            val entered = draft.credential()
            val previous = existing?.let(repository::credential)
            val credential = if (previous == null) entered else entered.copy(
                password = entered.password.ifBlank { previous.password },
                bearerToken = entered.bearerToken.ifBlank { previous.bearerToken },
                headers = if (entered.headers.isEmpty()) previous.headers else entered.headers,
            )
            runCatching { repository.test(draft.location(existing), credential) }
                .onSuccess { result ->
                    transient.value = transient.value.copy(
                        loading = false,
                        message = if (result.connected) result.message else null,
                        error = if (result.connected) null else result.message,
                    )
                }.onFailure(::setError)
        }
    }

    fun testSaved(location: NetworkLocation) {
        viewModelScope.launch {
            transient.value = transient.value.copy(loading = true, message = null, error = null)
            runCatching { repository.testSaved(location) }
                .onSuccess { result ->
                    transient.value = transient.value.copy(
                        loading = false,
                        message = if (result.connected) result.message else null,
                        error = if (result.connected) null else result.message,
                    )
                }.onFailure(::setError)
        }
    }

    fun playLocation(location: NetworkLocation, onPlay: (AppMedia) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.prepareLocation(location) }.onSuccess(onPlay).onFailure(::setError)
        }
    }

    fun open(location: NetworkLocation, path: String = "") {
        if (!location.protocol.browsable) return
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            transient.value = transient.value.copy(currentLocation = location, currentPath = path, loading = true, error = null, message = null)
            runCatching { repository.list(location, path) }
                .onSuccess { entries -> transient.value = transient.value.copy(entries = entries, loading = false) }
                .onFailure(::setError)
        }
    }

    fun openEntry(entry: NetworkEntry, onPlay: (AppMedia) -> Unit, onPlayQueue: (List<AppMedia>) -> Unit) {
        val location = state.value.currentLocation ?: return
        if (entry.isDirectory) open(location, entry.remotePath) else viewModelScope.launch {
            runCatching { repository.prepareQueue(entry) }.onSuccess { queue ->
                if (queue.size == 1) onPlay(queue.single()) else onPlayQueue(queue)
            }.onFailure(::setError)
        }
    }

    fun up() {
        val location = state.value.currentLocation ?: return
        val parent = state.value.currentPath.substringBeforeLast('/', "")
        open(location, parent)
    }

    fun closeBrowser() {
        browseJob?.cancel()
        transient.value = transient.value.copy(currentLocation = null, currentPath = "", entries = emptyList(), loading = false, error = null)
    }

    fun forgetCredentials(id: String) {
        viewModelScope.launch {
            runCatching { repository.forgetCredentials(id) }
                .onSuccess { transient.value = transient.value.copy(message = "Saved credentials removed", error = null) }
                .onFailure(::setError)
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            runCatching { repository.removeLocation(id) }
                .onSuccess { transient.value = transient.value.copy(message = "Network location removed", error = null) }
                .onFailure(::setError)
        }
    }

    fun playRecent(history: MediaHistoryEntity, onPlay: (AppMedia) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.prepareHistory(history) }.onSuccess(onPlay).onFailure(::setError)
        }
    }

    fun openDirect(
        url: String,
        title: String,
        username: String,
        password: String,
        bearerToken: String,
        userAgent: String,
        referer: String,
        onPlay: (AppMedia) -> Unit,
    ) {
        runCatching {
            repository.prepareDirect(
                url,
                title,
                NetworkCredential(username = username, password = password, bearerToken = bearerToken),
                buildMap {
                    if (userAgent.isNotBlank()) put("User-Agent", userAgent.trim())
                    if (referer.isNotBlank()) put("Referer", referer.trim())
                },
            )
        }.onSuccess(onPlay).onFailure(::setError)
    }

    fun clearNotice() {
        transient.value = transient.value.copy(message = null, error = null)
    }

    private fun setError(error: Throwable) {
        transient.value = transient.value.copy(loading = false, message = null, error = error.message ?: "Network operation failed")
    }
}

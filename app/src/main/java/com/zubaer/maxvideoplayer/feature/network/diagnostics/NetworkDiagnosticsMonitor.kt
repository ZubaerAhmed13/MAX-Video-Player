package com.zubaer.maxvideoplayer.feature.network.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.media3.common.Player
import com.zubaer.maxvideoplayer.feature.network.model.NetworkConnectionState
import com.zubaer.maxvideoplayer.feature.network.model.NetworkDiagnostics
import com.zubaer.maxvideoplayer.feature.network.model.NetworkPlaybackPhase
import com.zubaer.maxvideoplayer.feature.network.model.NetworkProtocol
import com.zubaer.maxvideoplayer.feature.network.model.NetworkTransport
import com.zubaer.maxvideoplayer.feature.network.model.NetworkUriPolicy
import com.zubaer.maxvideoplayer.feature.network.repository.NetworkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkDiagnosticsMonitor(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow(NetworkDiagnostics())
    val state: StateFlow<NetworkDiagnostics> = _state.asStateFlow()
    private var activeNetworkMedia = false
    private var hasBeenReady = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateConnectivity()
        override fun onLost(network: Network) {
            updateConnectivity()
            if (activeNetworkMedia) mutate(event = "Network lost") {
                it.copy(connectionState = NetworkConnectionState.OFFLINE, phase = NetworkPlaybackPhase.RECONNECTING)
            }
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateConnectivity(capabilities)
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivity.registerNetworkCallback(request, callback)
        }
        updateConnectivity()
    }

    fun activate(uri: String?) {
        val parsed = uri?.let(Uri::parse)
        activeNetworkMedia = parsed?.scheme?.lowercase() in setOf("http", "https", "rtsp", "ftp", "ftps", "maxsmb")
        hasBeenReady = false
        if (!activeNetworkMedia) {
            _state.value = _state.value.copy(protocol = null, host = null, sanitizedUri = null, phase = NetworkPlaybackPhase.IDLE)
            return
        }
        val protocol = runCatching { NetworkRepository.detectProtocol(uri.orEmpty()) }.getOrNull()
        mutate(event = "Source opened") {
            it.copy(
                protocol = protocol,
                host = parsed?.host,
                sanitizedUri = uri?.let(NetworkUriPolicy::sanitize),
                connectionState = NetworkConnectionState.CONNECTING,
                phase = NetworkPlaybackPhase.INITIAL_LOADING,
                retryCount = 0,
            )
        }
    }

    fun onPlayerState(player: Player) {
        if (!activeNetworkMedia) return
        when (player.playbackState) {
            Player.STATE_BUFFERING -> mutate(event = if (hasBeenReady) "Buffering" else "Initial loading") {
                it.copy(
                    phase = if (!it.networkAvailable && hasBeenReady) NetworkPlaybackPhase.RECONNECTING
                    else if (hasBeenReady) NetworkPlaybackPhase.BUFFERING else NetworkPlaybackPhase.INITIAL_LOADING,
                    connectionState = if (it.networkAvailable) NetworkConnectionState.CONNECTING else NetworkConnectionState.OFFLINE,
                )
            }
            Player.STATE_READY -> {
                hasBeenReady = true
                mutate(event = "Connected") {
                    it.copy(
                        phase = if (player.playWhenReady) NetworkPlaybackPhase.PLAYING else NetworkPlaybackPhase.PAUSED,
                        connectionState = NetworkConnectionState.CONNECTED,
                    )
                }
            }
            Player.STATE_ENDED -> mutate(event = "Playback ended") { it.copy(phase = NetworkPlaybackPhase.PAUSED) }
            Player.STATE_IDLE -> Unit
        }
    }

    fun onFailure() {
        if (!activeNetworkMedia) return
        mutate(event = "Playback failed") { it.copy(connectionState = NetworkConnectionState.FAILED, phase = NetworkPlaybackPhase.FAILED) }
    }

    fun recordReconnect() {
        if (!activeNetworkMedia) return
        mutate(event = "Reconnect #${_state.value.retryCount + 1}") {
            it.copy(
                retryCount = it.retryCount + 1,
                lastReconnectAtMs = System.currentTimeMillis(),
                phase = NetworkPlaybackPhase.RECONNECTING,
            )
        }
    }

    private fun updateConnectivity(provided: NetworkCapabilities? = null) {
        val active = connectivity.activeNetwork
        val capabilities = provided ?: active?.let(connectivity::getNetworkCapabilities)
        val available = active != null || capabilities != null
        val transport = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> NetworkTransport.VPN
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkTransport.WIFI
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkTransport.CELLULAR
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> NetworkTransport.ETHERNET
            available -> NetworkTransport.OTHER
            else -> NetworkTransport.NONE
        }
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        _state.value = _state.value.copy(
            networkAvailable = available,
            validatedInternet = validated,
            metered = connectivity.isActiveNetworkMetered,
            transport = transport,
        )
    }

    private inline fun mutate(event: String? = null, transform: (NetworkDiagnostics) -> NetworkDiagnostics) {
        val current = _state.value
        val events = if (event == null || current.events.lastOrNull() == event) current.events else (current.events + event).takeLast(30)
        _state.value = transform(current).copy(events = events)
    }
}

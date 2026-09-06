package com.zubaer.maxvideoplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zubaer.maxvideoplayer.core.database.PlaybackHistoryRepository
import com.zubaer.maxvideoplayer.core.model.AppMedia
import com.zubaer.maxvideoplayer.core.model.ResumeAction
import com.zubaer.maxvideoplayer.playback.session.PlaybackConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerCoordinatorState(
    val preparing: Boolean = true,
    val resumePositionMs: Long? = null,
)

class PlayerViewModel(
    private val media: AppMedia,
    private val historyRepository: PlaybackHistoryRepository,
    private val playbackConnection: PlaybackConnection,
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerCoordinatorState())
    val state: StateFlow<PlayerCoordinatorState> = _state.asStateFlow()

    init {
        playbackConnection.connect()
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { historyRepository.get(media.stableId) }
            val decision = historyRepository.resumeDecision(history)
            if (decision.action == ResumeAction.OFFER_RESUME) {
                _state.value = PlayerCoordinatorState(preparing = false, resumePositionMs = decision.positionMs)
            } else {
                loadAt(0L)
            }
        }
    }

    fun resume() {
        val position = _state.value.resumePositionMs ?: 0L
        _state.value = PlayerCoordinatorState(preparing = true)
        viewModelScope.launch { loadAt(position) }
    }

    fun startOver() {
        _state.value = PlayerCoordinatorState(preparing = true)
        viewModelScope.launch { loadAt(0L) }
    }

    private suspend fun loadAt(positionMs: Long) {
        playbackConnection.connect()
        playbackConnection.state.filter { it.connected }.first()
        playbackConnection.load(media, positionMs, playWhenReady = true)
        _state.value = PlayerCoordinatorState(preparing = false)
    }
}

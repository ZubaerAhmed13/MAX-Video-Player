package com.zubaer.maxvideoplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zubaer.maxvideoplayer.core.media.MediaStoreRepository
import com.zubaer.maxvideoplayer.core.model.AppMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

 data class LibraryUiState(
    val loading: Boolean = false,
    val videos: List<AppMedia> = emptyList(),
    val error: String? = null,
)

class LibraryViewModel(private val repository: MediaStoreRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.videos() }
                .onSuccess { _state.value = LibraryUiState(videos = it) }
                .onFailure { error ->
                    _state.value = LibraryUiState(error = error.message ?: "Unable to read local media")
                }
        }
    }
}

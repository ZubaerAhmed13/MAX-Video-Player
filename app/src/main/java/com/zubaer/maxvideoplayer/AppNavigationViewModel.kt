package com.zubaer.maxvideoplayer

import androidx.lifecycle.ViewModel
import com.zubaer.maxvideoplayer.core.model.AppMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped navigation state. Keeping the selected media here means a
 * configuration change can recreate Compose/Activity without losing the player
 * destination while the actual player instance remains service-owned.
 */
class AppNavigationViewModel : ViewModel() {
    private val _selectedMedia = MutableStateFlow<AppMedia?>(null)
    val selectedMedia: StateFlow<AppMedia?> = _selectedMedia.asStateFlow()

    fun select(media: AppMedia) {
        _selectedMedia.value = media
    }

    fun clearSelection() {
        _selectedMedia.value = null
    }
}

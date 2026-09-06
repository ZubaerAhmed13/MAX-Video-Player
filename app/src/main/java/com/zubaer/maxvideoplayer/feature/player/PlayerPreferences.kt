package com.zubaer.maxvideoplayer.feature.player

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<PlayerPreferencesState> = _state.asStateFlow()

    fun setDoubleTapSeekSeconds(value: Int) = update(KEY_DOUBLE_TAP_SECONDS, value.coerceIn(5, 300))
    fun setGestureSensitivity(value: GestureSensitivity) = update(KEY_SENSITIVITY, value.name)
    fun setHorizontalSeekEnabled(value: Boolean) = update(KEY_HORIZONTAL_SEEK, value)
    fun setBrightnessGestureEnabled(value: Boolean) = update(KEY_BRIGHTNESS, value)
    fun setVolumeGestureEnabled(value: Boolean) = update(KEY_VOLUME, value)
    fun setPinchZoomEnabled(value: Boolean) = update(KEY_PINCH_ZOOM, value)
    fun setAutoHideMillis(value: Long) = update(KEY_AUTO_HIDE, value.coerceIn(2_000L, 8_000L))
    fun setOrientationMode(value: OrientationMode) = update(KEY_ORIENTATION, value.name)
    fun setDefaultResizeMode(value: ResizeMode) = update(KEY_RESIZE, value.name)
    fun setRememberPlaybackSpeed(value: Boolean) = update(KEY_REMEMBER_SPEED, value)
    fun setRememberedPlaybackSpeed(value: Float) = update(KEY_SPEED, value.coerceIn(0.25f, 4f))
    fun setAutoPip(value: Boolean) = update(KEY_AUTO_PIP, value)
    fun setTutorialSeen(value: Boolean) = update(KEY_TUTORIAL_SEEN, value)

    private fun update(key: String, value: Any) {
        val editor = prefs.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            else -> return
        }
        editor.apply()
        _state.value = read()
    }

    private fun read(): PlayerPreferencesState = PlayerPreferencesState(
        doubleTapSeekSeconds = prefs.getInt(KEY_DOUBLE_TAP_SECONDS, 10).coerceIn(5, 300),
        gestureSensitivity = PlayerPreferenceCodec.sensitivity(prefs.getString(KEY_SENSITIVITY, null)),
        horizontalSeekEnabled = prefs.getBoolean(KEY_HORIZONTAL_SEEK, true),
        brightnessGestureEnabled = prefs.getBoolean(KEY_BRIGHTNESS, true),
        volumeGestureEnabled = prefs.getBoolean(KEY_VOLUME, true),
        pinchZoomEnabled = prefs.getBoolean(KEY_PINCH_ZOOM, true),
        autoHideMillis = prefs.getLong(KEY_AUTO_HIDE, 3_000L).coerceIn(2_000L, 8_000L),
        orientationMode = PlayerPreferenceCodec.orientationMode(prefs.getString(KEY_ORIENTATION, null)),
        defaultResizeMode = PlayerPreferenceCodec.resizeMode(prefs.getString(KEY_RESIZE, null)),
        rememberPlaybackSpeed = prefs.getBoolean(KEY_REMEMBER_SPEED, false),
        rememberedPlaybackSpeed = prefs.getFloat(KEY_SPEED, 1f).coerceIn(0.25f, 4f),
        autoPip = prefs.getBoolean(KEY_AUTO_PIP, false),
        tutorialSeen = prefs.getBoolean(KEY_TUTORIAL_SEEN, false),
    )

    companion object {
        private const val NAME = "player_interaction_preferences_v1"
        private const val KEY_DOUBLE_TAP_SECONDS = "double_tap_seconds"
        private const val KEY_SENSITIVITY = "gesture_sensitivity"
        private const val KEY_HORIZONTAL_SEEK = "horizontal_seek"
        private const val KEY_BRIGHTNESS = "brightness_gesture"
        private const val KEY_VOLUME = "volume_gesture"
        private const val KEY_PINCH_ZOOM = "pinch_zoom"
        private const val KEY_AUTO_HIDE = "auto_hide_ms"
        private const val KEY_ORIENTATION = "orientation_mode"
        private const val KEY_RESIZE = "resize_mode"
        private const val KEY_REMEMBER_SPEED = "remember_speed"
        private const val KEY_SPEED = "remembered_speed"
        private const val KEY_AUTO_PIP = "auto_pip"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
    }
}

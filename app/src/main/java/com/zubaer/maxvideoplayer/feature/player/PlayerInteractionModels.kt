package com.zubaer.maxvideoplayer.feature.player

enum class PlayerGestureKind {
    NONE,
    SEEK,
    BRIGHTNESS,
    VOLUME,
    ZOOM,
    PAN,
}

enum class PlayerMenu {
    NONE,
    SPEED,
    PLAYBACK,
    SUBTITLES,
    DISPLAY,
    ORIENTATION,
    SETTINGS,
    INFO,
}

enum class ResizeMode {
    FIT,
    FILL,
    CROP,
    ORIGINAL,
    ASPECT_16_9,
    ASPECT_4_3,
    ASPECT_18_9,
    ASPECT_21_9,
    CUSTOM,
}

enum class OrientationMode {
    AUTO,
    PORTRAIT,
    LANDSCAPE,
    REVERSE_PORTRAIT,
    REVERSE_LANDSCAPE,
    LOCK_CURRENT,
}

enum class GestureSensitivity(val multiplier: Float) {
    LOW(0.65f),
    MEDIUM(1f),
    HIGH(1.5f),
}

enum class DoubleTapZone {
    LEFT,
    CENTER,
    RIGHT,
}

sealed interface PlayerHudState {
    data object Hidden : PlayerHudState
    data class Seek(val fromMs: Long, val targetMs: Long) : PlayerHudState
    data class Brightness(val fraction: Float) : PlayerHudState
    data class Volume(val fraction: Float) : PlayerHudState
    data class Zoom(val scale: Float) : PlayerHudState
}

data class PlayerPreferencesState(
    val doubleTapSeekSeconds: Int = 10,
    val gestureSensitivity: GestureSensitivity = GestureSensitivity.MEDIUM,
    val horizontalSeekEnabled: Boolean = true,
    val brightnessGestureEnabled: Boolean = true,
    val volumeGestureEnabled: Boolean = true,
    val pinchZoomEnabled: Boolean = true,
    val autoHideMillis: Long = 3_000L,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
    val defaultResizeMode: ResizeMode = ResizeMode.FIT,
    val customAspectRatio: Float = 16f / 9f,
    val rememberPlaybackSpeed: Boolean = false,
    val rememberedPlaybackSpeed: Float = 1f,
    val autoPip: Boolean = false,
    val tutorialSeen: Boolean = false,
)

data class PlayerCoordinatorState(
    val preparing: Boolean = true,
    val resumePositionMs: Long? = null,
    val controlsVisible: Boolean = true,
    val controlsLocked: Boolean = false,
    val unlockVisible: Boolean = false,
    val interactionInProgress: Boolean = false,
    val gestureKind: PlayerGestureKind = PlayerGestureKind.NONE,
    val activeMenu: PlayerMenu = PlayerMenu.NONE,
    val hud: PlayerHudState = PlayerHudState.Hidden,
    val seekTargetMs: Long? = null,
    val brightnessFraction: Float = 0.5f,
    val volumeFraction: Float = 0f,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val resizeMode: ResizeMode = ResizeMode.FIT,
    val customAspectRatio: Float = 16f / 9f,
    val displayRotationDegrees: Int = 0,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
    val fullscreen: Boolean = false,
    val tutorialVisible: Boolean = false,
    val accessibilityMode: Boolean = false,
    val isPlaying: Boolean = false,
    val preferences: PlayerPreferencesState = PlayerPreferencesState(),
)

data class PanBounds(
    val maxX: Float,
    val maxY: Float,
)

data class VideoTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
    val rotationDegrees: Float,
)

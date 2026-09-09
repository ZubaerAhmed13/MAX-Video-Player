package com.zubaer.maxvideoplayer.feature.tv

import android.view.KeyEvent

/** Semantic player actions produced by a TV remote/media keyboard. */
enum class TvPlayerAction {
    PLAY_PAUSE,
    PLAY,
    PAUSE,
    SEEK_BACKWARD,
    SEEK_FORWARD,
    PREVIOUS,
    NEXT,
    SHOW_CONTROLS,
    BACK,
}

enum class TvBackAction {
    CLOSE_PANEL,
    HIDE_CONTROLS,
    EXIT_PLAYER,
}

/**
 * Central policy for Android TV player input. PlayerScreen translates these semantic actions into
 * the same service-owned playback operations used by touch UI; raw KeyEvent checks stay here.
 */
object TvPlayerInputController {
    const val SEEK_STEP_MS = 10_000L

    fun actionFor(keyCode: Int): TvPlayerAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        -> TvPlayerAction.PLAY_PAUSE

        KeyEvent.KEYCODE_MEDIA_PLAY -> TvPlayerAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> TvPlayerAction.PAUSE

        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        -> TvPlayerAction.SEEK_BACKWARD

        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        -> TvPlayerAction.SEEK_FORWARD

        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> TvPlayerAction.PREVIOUS
        KeyEvent.KEYCODE_MEDIA_NEXT -> TvPlayerAction.NEXT

        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        -> TvPlayerAction.SHOW_CONTROLS

        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE,
        -> TvPlayerAction.BACK

        else -> null
    }

    fun backAction(panelOpen: Boolean, controlsVisible: Boolean): TvBackAction = when {
        panelOpen -> TvBackAction.CLOSE_PANEL
        controlsVisible -> TvBackAction.HIDE_CONTROLS
        else -> TvBackAction.EXIT_PLAYER
    }
}

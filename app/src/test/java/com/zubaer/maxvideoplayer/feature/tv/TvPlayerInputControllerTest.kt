package com.zubaer.maxvideoplayer.feature.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvPlayerInputControllerTest {
    @Test
    fun centerAndMediaKeys_mapToPlaybackActions() {
        assertEquals(TvPlayerAction.PLAY_PAUSE, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(TvPlayerAction.PLAY, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(TvPlayerAction.PAUSE, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(TvPlayerAction.NEXT, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(TvPlayerAction.PREVIOUS, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
    }

    @Test
    fun dpadAndTransportSeekKeys_mapToTenSecondSeekActions() {
        assertEquals(10_000L, TvPlayerInputController.SEEK_STEP_MS)
        assertEquals(TvPlayerAction.SEEK_BACKWARD, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(TvPlayerAction.SEEK_FORWARD, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(TvPlayerAction.SEEK_BACKWARD, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertEquals(TvPlayerAction.SEEK_FORWARD, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertEquals(TvPlayerAction.SHOW_CONTROLS, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(TvPlayerAction.SHOW_CONTROLS, TvPlayerInputController.actionFor(KeyEvent.KEYCODE_DPAD_DOWN))
        assertNull(TvPlayerInputController.actionFor(KeyEvent.KEYCODE_A))
    }

    @Test
    fun backPolicy_closesPanelThenControlsThenPlayer() {
        assertEquals(TvBackAction.CLOSE_PANEL, TvPlayerInputController.backAction(panelOpen = true, controlsVisible = true))
        assertEquals(TvBackAction.HIDE_CONTROLS, TvPlayerInputController.backAction(panelOpen = false, controlsVisible = true))
        assertEquals(TvBackAction.EXIT_PLAYER, TvPlayerInputController.backAction(panelOpen = false, controlsVisible = false))
    }
}
